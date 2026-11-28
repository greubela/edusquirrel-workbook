package it.evadid.homepage.webElements.editor.code.JavaEditor

import it.evadid.vm.editing.JavaMethodEditing
import it.evadid.homepage.webElements.editor.code.fragment.{FragmentCodeEditor, FragmentRange}
import it.evadid.homepage.webElements.editor.code.fragment.FragmentCodeEditor.Scope
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.vm.parsing.source.{JavaFunctionStructured, SourceParsing}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.util.logging.Logger
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.interactions.programming.state.ProgrammingStateJavaStructured

/** Edits one method or constructor, identified by its class and signature. */
final class JavaFunctionEditor(
    logger: Logger,
    state: Var[ProgrammingStateJavaStructured],
    loadingLabel: Signal[String],
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil)
) extends FragmentCodeEditor[ProgrammingStateJavaStructured, JavaFunctionEditor.Function](logger, state, editorConfig, loadingLabel, diagnostics) {
  import JavaFunctionEditor.Function
  import JavaMethodEditing.*

  override protected def locate(source: ProgrammingStateJavaStructured, function: Function): Option[FragmentRange] =
    source.definedClasses.get(function.className)
      .flatMap(text => findSelection(discover(text), function.method))
      .map(method => FragmentRange(function.className, method.span.start, method.span.end))
  override protected def identify(source: ProgrammingStateJavaStructured, range: FragmentRange, previous: Function) =
    source.definedClasses.get(range.owner)
      .flatMap(text => findEditedMethod(text, discover(text), previous.method, MethodRange(range.start, range.end)))
      .map(Function(range.owner, _))

  /** The complete outermost methods within the draft; code a declaration does not end yet (such as a word before a
    * method's modifiers) belongs to the method after it.
    */
  override protected def widening(source: ProgrammingStateJavaStructured, range: FragmentRange): Option[Scope] = {
    val inside = source.definedClasses.get(range.owner).toVector.flatMap(text =>
      SourceParsing.parse(text, SourceLanguage.Java).structured.functions.filter(method => range.start <= method.span.start && method.span.end <= range.end))
    memberWidening(source, range, inside.filterNot(method => inside.exists(outer => outer != method && outer.span.contains(method.span))).map(_.span))
  }
  override protected def cssClass: String = "java-function-editor__method"
}

object JavaFunctionEditor {
  /** A method of the class stored under `className`; its offsets are relative to that class's source. */
  final case class Function(className: BeEntityName, method: JavaFunctionStructured)

  /** The method of class `className` whose declaration is `code`, ignoring surrounding whitespace. */
  def find(source: ProgrammingStateJavaStructured, className: BeEntityName, code: String): Option[Function] =
    source.definedClasses.get(className).flatMap(text => JavaMethodEditing.discover(text).flatMap(_.functions)
      .find(method => method.code.trim == code.trim)).map(Function(className, _))
}
