package it.evadid.homepage.webElements.editor.code.fragment

import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.parsing.source.SourceSpan

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Edits one part of a structured answer `S` (Java or Python, see [[FragmentSource]]), such as a class or a
  * method, in the local Var `local`.
  *
  * Changes of `local` are written into `state` at the fragment's range, which then spans the draft, so
  * unfinished code stays in place. Any other change of `state` refreshes `local` from the fragment; when its
  * text changed, the fragment is found again by its identity `I` (see [[locate]]). A draft that comes to hold code
  * beyond its declaration keeps its whole range and reports the [[FragmentCodeEditor.Scope]] that shows all of
  * it in [[widened]]. Subclasses differ only in how they locate, identify, widen and present their fragment.
  */
abstract class FragmentCodeEditor[S, I](
    logger: Logger,
    val state: Var[S],
    editorConfig: CodeMirrorEditorConfig,
    loadingLabel: Signal[String],
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]]
)(using sources: FragmentSource[S]) extends HtmlAppElement {
  import FragmentCodeEditor.{Fragment, Scope}

  private val fragment: Var[Option[Fragment[S, I]]] = Var(None)

  /** The fragment text shown in the editor. */
  val local: Var[String] = Var("")

  /** What the editor edits, e.g. a class name or a method; `None` until something is selected. */
  val target: Signal[Option[I]] = fragment.signal.map(_.map(_.identity)).distinct

  /** What the editor edits with the answer it was found in, so that readers never pair it with another answer. */
  val targetInAnswer: Signal[Option[(I, S)]] = fragment.signal.map(_.map(current => (current.identity, current.source))).distinct

  /** Whether the selected fragment was found in the answer. */
  val located: Signal[Boolean] = fragment.signal.map(_.exists(_.range.nonEmpty)).distinct

  /** The scope that shows the whole selected fragment once it holds more than one declaration; its component
    * switches to that scope's mode.
    */
  val widened: Signal[Option[Scope]] = fragment.signal
    .map(_.flatMap(current => current.range.flatMap(widening(current.source, _)))).distinct

  /** The range of the fragment identified by `identity` in `source`. */
  protected def locate(source: S, identity: I): Option[FragmentRange]

  /** The identity of the fragment now at `range`, after an edit; `None` keeps `previous` (e.g. unfinished code). */
  protected def identify(source: S, range: FragmentRange, previous: I): Option[I]

  /** The scope a fragment at `range` needs because it holds code beyond its own declaration; `None` otherwise.
    * Editors decide with [[memberWidening]] or [[ownerWidening]], so both languages follow one rule.
    */
  protected def widening(source: S, range: FragmentRange): Option[Scope]

  /** Whether the fragment at `range` holds only whitespace and comments besides `spans` (of its owner's source). */
  protected final def onlyTriviaAround(source: S, range: FragmentRange, spans: Seq[SourceSpan]): Boolean =
    sources.source(source, range.owner).forall(code => sources.onlyTriviaAround(code, range.start, range.end, spans))

  /** Whether the owner's brackets, strings and comments are closed; while they are open, the declarations' extent is
    * still being typed and nothing widens.
    */
  private def closed(source: S, range: FragmentRange): Boolean = sources.source(source, range.owner).exists(sources.balanced)

  /** For an editor of one member (a method): its owner's view as soon as the draft holds code besides the one
    * complete member in `members`, finished or not. A member that is itself unfinished is not in `members`.
    */
  protected final def memberWidening(source: S, range: FragmentRange, members: Seq[SourceSpan]): Option[Scope] =
    Option.when(closed(source, range) && (members.size > 1 || members.nonEmpty && !onlyTriviaAround(source, range, members)))(Scope.Owner)

  /** For an editor of a whole owner (a class): the program's view as soon as the draft holds code besides `declaration`. */
  protected final def ownerWidening(source: S, range: FragmentRange, declaration: Seq[SourceSpan]): Option[Scope] =
    Option.when(closed(source, range) && declaration.nonEmpty && !onlyTriviaAround(source, range, declaration))(Scope.Program)

  /** The editor text of a fragment stored as `stored` (e.g. a Python method without its class indentation). */
  protected def present(stored: String): String = stored

  /** The stored form of the editor text `draft` for the fragment now stored as `stored`; inverse of [[present]]. */
  protected def store(draft: String, stored: String): String = draft

  protected def cssClass: String

  /** A cursor offset in the editor text, placed once the native editor exists. */
  private val caret = Var(Option.empty[Int])
  /** A printed-program offset to continue at when this editor is shown next. */
  private val pendingCursor = Var(Option.empty[Int])

  /** The focused cursor as an offset of the printed program, to continue there in another view. */
  def printedCursor: Option[Int] = for {
    offset <- editor.focusedCursor
    current <- fragment.now()
    range <- current.range
    stored <- range.text(current.source)
  } yield range.printedOffset(current.source, FragmentCodeEditor.mapOffset(present(stored), stored, offset))

  /** Continues editing at the printed-program offset `printed` when this editor is shown next. */
  def placeCursor(printed: Int): Unit = pendingCursor.set(Some(printed))

  private def placePendingCursor(): Unit = pendingCursor.now().foreach { printed =>
    pendingCursor.set(None)
    // The range may still describe the answer as it was when this editor was last shown.
    fragment.now().foreach(refresh(state.now(), _))
    for {
      range <- fragment.now().flatMap(_.range)
      stored <- range.text(state.now())
      offset <- range.fragmentOffset(state.now(), printed)
    } caret.set(Some(FragmentCodeEditor.mapOffset(stored, present(stored), offset)))
  }

  /** Edits the fragment identified by `identity` from now on. */
  def select(identity: I): Unit = refresh(state.now(), Fragment(identity, None, state.now()))

  def clear(): Unit = {
    fragment.set(None)
    local.set("")
  }

  private def shown(range: FragmentRange, source: S): Option[String] = range.text(source).map(present)

  /** Keeps the fragment while its text is unchanged; otherwise locates it again and shows its current text. */
  private def refresh(source: S, current: Fragment[S, I]): Unit =
    if !current.range.flatMap(shown(_, source)).contains(present(local.now())) then {
      val range = locate(source, current.identity)
      fragment.set(Some(Fragment(current.identity, range, source)))
      // Equal text is not set again: the editor would replace its document and lose keystrokes not yet written.
      range.flatMap(shown(_, source)).filter(_ != local.now()).foreach(local.set)
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
      // A widened draft keeps its range, so all of it stays visible until the component switches its mode.
      val identity = if widening(next, nextRange).isDefined then None else identify(next, nextRange, current.identity)
      val selectedRange = identity.flatMap(locate(next, _)).getOrElse(nextRange)
      fragment.set(Some(Fragment(identity.getOrElse(current.identity), Some(selectedRange), next)))
      state.set(next)
      shown(selectedRange, next).filter(text => present(text) != present(draft)).foreach(local.set)
    }

  private lazy val editor = CodeMirrorEditor(logger, local, language = sources.language, config = Var(editorConfig),
    loadingLabel = loadingLabel, caret = caret,
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
      // After the edit that opened this editor has reached the answer.
      Future(placePendingCursor())
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
  /** The fragment `identity` at `range` of `source`, the answer the range was found in: whatever reads the range
    * reads it on that answer, never on an answer from before or after it.
    */
  private final case class Fragment[S, I](identity: I, range: Option[FragmentRange], source: S)

  /** Maps `offset` in `from` to the same place in `to`, which holds the same lines with other indentation (a Python
    * method with or without its class indentation); identical texts map to the same offset.
    */
  private[fragment] def mapOffset(from: String, to: String, offset: Int): Int = {
    def indent(line: String) = line.takeWhile(c => c == ' ' || c == '\t').length
    val (fromLines, toLines) = (from.split("\n", -1).toVector, to.split("\n", -1).toVector)
    val line = from.take(offset).count(_ == '\n').min(toLines.size - 1)
    val column = offset - (from.lastIndexOf('\n', offset - 1) + 1)
    val target = toLines(line)
    toLines.take(line).map(_.length + 1).sum +
      (column + indent(target) - fromLines.lift(line).fold(0)(indent)).max(0).min(target.length)
  }

  /** The most local view that shows a whole draft holding more than its fragment: its owner's source (a method
    * with further code of its class, such as another method or an attribute) or the whole program (a second class).
    */
  enum Scope { case Owner, Program }
}
