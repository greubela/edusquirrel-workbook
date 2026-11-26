package it.evadid.homepage.webElements.editor.code.fragment

import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Edits one part of a structured answer `S` (Java or Python, see [[FragmentSource]]), such as a class or a
  * method, in the local Var `local`.
  *
  * Changes of `local` are written into `state` at the fragment's range, which then spans the draft, so
  * unfinished code stays in place. Any other change of `state` refreshes `local` from the fragment; when its
  * text changed, the fragment is found again by its identity `I` (see [[locate]]). Subclasses differ only in
  * how they locate, identify and present their fragment.
  */
abstract class FragmentCodeEditor[S, I](
    logger: Logger,
    val state: Var[S],
    editorConfig: CodeMirrorEditorConfig,
    loadingLabel: Signal[String],
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]]
)(using sources: FragmentSource[S]) extends HtmlAppElement {
  import FragmentCodeEditor.Fragment

  private val fragment: Var[Option[Fragment[I]]] = Var(None)

  /** The fragment text shown in the editor. */
  val local: Var[String] = Var("")

  /** What the editor edits, e.g. a class name or a method; `None` until something is selected. */
  val target: Signal[Option[I]] = fragment.signal.map(_.map(_.identity)).distinct

  /** Whether the selected fragment was found in the answer. */
  val located: Signal[Boolean] = fragment.signal.map(_.exists(_.range.nonEmpty)).distinct

  /** The range of the fragment identified by `identity` in `source`. */
  protected def locate(source: S, identity: I): Option[FragmentRange]

  /** The identity of the fragment now at `range`, after an edit; `None` keeps `previous` (e.g. unfinished code). */
  protected def identify(source: S, range: FragmentRange, previous: I): Option[I]

  /** The editor text of a fragment stored as `stored` (e.g. a Python method without its class indentation). */
  protected def present(stored: String): String = stored

  /** The stored form of the editor text `draft` for the fragment now stored as `stored`; inverse of [[present]]. */
  protected def store(draft: String, stored: String): String = draft

  protected def cssClass: String

  /** Edits the fragment identified by `identity` from now on. */
  def select(identity: I): Unit = refresh(state.now(), Fragment(identity, None))

  def clear(): Unit = {
    fragment.set(None)
    local.set("")
  }

  private def shown(range: FragmentRange, source: S): Option[String] = range.text(source).map(present)

  /** Keeps the fragment while its text is unchanged; otherwise locates it again and shows its current text. */
  private def refresh(source: S, current: Fragment[I]): Unit =
    if !current.range.flatMap(shown(_, source)).contains(present(local.now())) then {
      val range = locate(source, current.identity)
      fragment.set(Some(Fragment(current.identity, range)))
      range.flatMap(shown(_, source)).foreach(local.set)
    }

  /** The shared update logic: write the draft into the answer and follow its range and identity. */
  private def write(draft: String): Unit =
    for {
      current <- fragment.now()
      range <- current.range
      stored <- range.text(state.now())
      if draft == local.now() && present(stored) != present(draft)
      (next, nextRange) <- range.replace(state.now(), store(draft, stored))
    } {
      val identity = identify(next, nextRange, current.identity)
      val selectedRange = identity.flatMap(locate(next, _)).getOrElse(nextRange)
      fragment.set(Some(Fragment(identity.getOrElse(current.identity), Some(selectedRange))))
      state.set(next)
      shown(selectedRange, next).filter(text => present(text) != present(draft)).foreach(local.set)
    }

  private lazy val editor = CodeMirrorEditor(logger, local, language = sources.language, config = Var(editorConfig),
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

object FragmentCodeEditor {
  private final case class Fragment[I](identity: I, range: Option[FragmentRange])
}
