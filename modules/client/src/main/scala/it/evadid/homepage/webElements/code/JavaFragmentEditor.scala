package it.evadid.homepage.webElements.code

import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured

import scala.collection.immutable.VectorMap
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Characters `[start, end)` of the source of class `className` in a structured Java answer. */
final case class JavaFragmentRange(className: BeEntityName, start: Int, end: Int) {
  private def within(source: String): Boolean = start >= 0 && start <= end && end <= source.length

  def text(state: ProgrammingStateJavaStructured): Option[String] =
    state.definedClasses.get(className).filter(within).map(_.substring(start, end))

  /** `state` with this range replaced by `draft`, and the range the draft then occupies. A class whose
    * edited declaration names another class that is not yet defined keeps its position under the new name.
    */
  def replace(state: ProgrammingStateJavaStructured, draft: String): Option[(ProgrammingStateJavaStructured, JavaFragmentRange)] =
    state.definedClasses.get(className).filter(within).map { source =>
      val edited = source.substring(0, start) + draft + source.substring(end)
      val key = JavaFragmentRange.declaredName(edited)
        .filterNot(name => state.definedClasses.keys.exists(JavaFragmentRange.sameName(_, name)))
        .getOrElse(className)
      val classes = VectorMap.from(state.definedClasses.map((name, value) => if name == className then key -> edited else name -> value))
      (ProgrammingStateJavaStructured(classes), JavaFragmentRange(key, start, start + draft.length))
    }

  /** The line of the printed program (`state.toString`) on which this fragment starts. */
  def firstLineNumber(state: ProgrammingStateJavaStructured): Int = {
    val preceding = state.definedClasses.toSeq.takeWhile(_._1 != className).map(_._2.length + "\n\n".length).sum
    JavaMethodSource.firstLineNumber(state.toString, Some(JavaMethodSource.MethodRange(preceding + start, preceding + end)))
  }
}

object JavaFragmentRange {
  /** The name of the first class declared in `source`. */
  def declaredName(source: String): Option[BeEntityName] =
    SourceStructure.javaClasses(source).headOption.map(block => BeEntityName.fromCodeString(block.name))

  def sameName(left: BeEntityName, right: BeEntityName): Boolean =
    left.universalInterpretation() == right.universalInterpretation()
}

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
    loadingLabel: Signal[String]
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
      fragment.set(Some(Fragment(identify(next, nextRange, current.identity).getOrElse(current.identity), Some(nextRange))))
      state.set(next)
    }

  private lazy val editor = CodeMirrorEditor(logger, local, language = Java, config = Var(editorConfig),
    loadingLabel = loadingLabel,
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
    editor.getDomElement())
}

object JavaFragmentEditor {
  private final case class Fragment[I](identity: I, range: Option[JavaFragmentRange])
}

/** Edits the complete source of one class, identified by its name in the answer. Renaming the class in its
  * declaration renames its entry.
  */
final class JavaClassEditor(
    logger: Logger,
    state: Var[ProgrammingStateJavaStructured],
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    loadingLabel: Signal[String] = Val("Loading editor…")
) extends JavaFragmentEditor[BeEntityName](logger, state, editorConfig, loadingLabel) {
  override protected def locate(source: ProgrammingStateJavaStructured, name: BeEntityName): Option[JavaFragmentRange] =
    source.definedClasses.get(name).map(text => JavaFragmentRange(name, 0, text.length))
  override protected def identify(source: ProgrammingStateJavaStructured, range: JavaFragmentRange, previous: BeEntityName) =
    Some(range.className)
  override protected def cssClass: String = "java-function-editor__class"
}

/** Edits one method or constructor, identified by its class and signature. */
final class JavaFunctionEditor(
    logger: Logger,
    state: Var[ProgrammingStateJavaStructured],
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    loadingLabel: Signal[String] = Val("Loading editor…")
) extends JavaFragmentEditor[JavaFunctionEditor.Function](logger, state, editorConfig, loadingLabel) {
  import JavaFunctionEditor.Function
  import JavaMethodSource.*

  override protected def locate(source: ProgrammingStateJavaStructured, function: Function): Option[JavaFragmentRange] =
    source.definedClasses.get(function.className)
      .flatMap(text => findSelection(discover(text), function.method))
      .map(method => JavaFragmentRange(function.className, method.start, method.end))
  override protected def identify(source: ProgrammingStateJavaStructured, range: JavaFragmentRange, previous: Function) =
    source.definedClasses.get(range.className)
      .flatMap(text => findEditedMethod(text, discover(text), previous.method, MethodRange(range.start, range.end)))
      .map(Function(range.className, _))
  override protected def cssClass: String = "java-function-editor__method"
}

object JavaFunctionEditor {
  /** A method of the class stored under `className`; its offsets are relative to that class's source. */
  final case class Function(className: BeEntityName, method: JavaMethodSource.JavaMethod)

  /** The method of class `className` whose declaration is `code`, ignoring surrounding whitespace. */
  def find(source: ProgrammingStateJavaStructured, className: BeEntityName, code: String): Option[Function] =
    source.definedClasses.get(className).flatMap(text => JavaMethodSource.discover(text).flatMap(_.methods)
      .find(method => text.substring(method.start, method.end).trim == code.trim)).map(Function(className, _))
}
