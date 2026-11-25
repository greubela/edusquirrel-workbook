package it.evadid.homepage.webElements.editor.code.JavaEditor

import it.evadid.vm.editing.JavaMethodEditing
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.vm.parsing.source.JavaFunctionStructured
import it.evadid.util.logging.Logger
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured

/** Edits one method or constructor, identified by its class and signature. */
final class JavaFunctionEditor(
    logger: Logger,
    state: Var[ProgrammingStateJavaStructured],
    loadingLabel: Signal[String],
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil)
) extends JavaFragmentEditor[JavaFunctionEditor.Function](logger, state, editorConfig, loadingLabel, diagnostics) {
  import JavaFunctionEditor.Function
  import JavaMethodEditing.*

  override protected def locate(source: ProgrammingStateJavaStructured, function: Function): Option[JavaFragmentRange] =
    source.definedClasses.get(function.className)
      .flatMap(text => findSelection(discover(text), function.method))
      .map(method => JavaFragmentRange(function.className, method.span.start, method.span.end))
  override protected def identify(source: ProgrammingStateJavaStructured, range: JavaFragmentRange, previous: Function) =
    source.definedClasses.get(range.className)
      .flatMap(text => findEditedMethod(text, discover(text), previous.method, MethodRange(range.start, range.end)))
      .map(Function(range.className, _))
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
