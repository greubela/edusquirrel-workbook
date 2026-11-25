package it.evadid.homepage.webElements.editor.code.JavaEditor

import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Edits one part of a structured Java answer, such as a class or a method, in the local Var `local`.
  *
  * Changes of `local` are written into `state` at the fragment's range, which then spans the draft, so
  * unfinished code stays in place. Any other change of `state` refreshes `local` from the fragment; when its
  * text changed, the fragment is found again by its identity `I` (see [[locate]]). Subclasses differ only in
  * how they locate and identify their fragment.
  */
abstract class JavaFragmentEditor[I](
    logger: Logger,
    val state: Var[ProgrammingStateJavaStructured],
    editorConfig: CodeMirrorEditorConfig,
    loadingLabel: Signal[String],
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]]
) extends HtmlAppElement {
  import JavaFragmentEditor.Fragment

  private val fragment: Var[Option[Fragment[I]]] = Var(None)

  /** The fragment text shown in the editor. */
  val local: Var[String] = Var("")

  /** What the editor edits, e.g. a class name or a method signature; `None` until something is selected. */
  val target: Signal[Option[I]] = fragment.signal.map(_.map(_.identity)).distinct

  /** Whether the selected fragment was found in the answer. */
  val located: Signal[Boolean] = fragment.signal.map(_.exists(_.range.nonEmpty)).distinct

  /** The range of the fragment identified by `identity` in `source`. */
  protected def locate(source: ProgrammingStateJavaStructured, identity: I): Option[JavaFragmentRange]

  /** The identity of the fragment now at `range`, after an edit; `None` keeps `previous` (e.g. unfinished code). */
  protected def identify(source: ProgrammingStateJavaStructured, range: JavaFragmentRange, previous: I): Option[I]

  protected def cssClass: String

  /** Edits the fragment identified by `identity` from now on. */
  def select(identity: I): Unit = refresh(state.now(), Fragment(identity, None))

  def clear(): Unit = {
    fragment.set(None)
    local.set("")
  }

  /** Keeps the fragment while its text is unchanged; otherwise locates it again and shows its current text. */
  private def refresh(source: ProgrammingStateJavaStructured, current: Fragment[I]): Unit =
    if !current.range.flatMap(_.text(source)).contains(local.now()) then {
      val range = locate(source, current.identity)
      fragment.set(Some(Fragment(current.identity, range)))
      range.flatMap(_.text(source)).foreach(local.set)
    }

  /** The shared update logic: write the draft into the answer and follow its range and identity. */
  private def write(draft: String): Unit =
    for {
      current <- fragment.now()
      range <- current.range
      if draft == local.now() && !range.text(state.now()).contains(draft)
      (next, nextRange) <- range.replace(state.now(), draft)
    } {
      val identity = identify(next, nextRange, current.identity)
      val selectedRange = identity.flatMap(locate(next, _)).getOrElse(nextRange)
      fragment.set(Some(Fragment(identity.getOrElse(current.identity), Some(selectedRange))))
      state.set(next)
      selectedRange.text(next).filter(_ != draft).foreach(local.set)
    }

  private lazy val editor = CodeMirrorEditor(logger, local, language = Java, config = Var(editorConfig),
    loadingLabel = loadingLabel,
    // Diagnostics of the printed program, in the fragment's own lines.
    diagnostics = state.signal.combineWith(fragment.signal, diagnostics).map((source, current, all) =>
      current.flatMap(_.range).toSeq.flatMap(range => all.flatMap(range.localize(source, _)))),
    firstLineNumber = state.signal.combineWith(fragment.signal)
      .map((source, current) => current.flatMap(_.range).fold(1)(_.firstLineNumber(source))).distinct)

  override def dismissOnOutsideClick: Boolean = false

  override def getDomElement(): Element = div(
    cls := cssClass,
    onMountCallback { ctx =>
      state.signal.foreach(source => fragment.now().foreach(refresh(source, _)))(using ctx.owner)
      val lifetime = new Subscription(ctx.owner, () => ())
      // Write after the transaction settles, so a newly selected fragment cannot receive the previous draft.
      local.signal.changes.distinct.foreach(draft => Future { if !lifetime.isKilled then write(draft) })(using ctx.owner)
    },
    // Child CodeMirror commits buffered text during unmount, after these subscriptions may be gone.
    onUnmountCallback { _ =>
      val closingAnswer = state.now()
      Future { if state.now() == closingAnswer then write(local.now()) }
    },
    editor.getDomElement())
}

object JavaFragmentEditor {
  private final case class Fragment[I](identity: I, range: Option[JavaFragmentRange])
}
