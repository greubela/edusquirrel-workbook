package it.evadid.homepage.webElements.editor.code.PythonEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.fragment.{FragmentCodeEditor, FragmentRange}
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.editing.PythonMethodEditing
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.source.SourceText
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonStructured

/** Edits one function of the module or a method of a class, shown without its class indentation. */
final class PythonFunctionEditor(
    logger: Logger,
    state: Var[ProgrammingStatePythonStructured],
    loadingLabel: Signal[String],
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil)
) extends FragmentCodeEditor[ProgrammingStatePythonStructured, PythonFunctionEditor.Function](logger, state, editorConfig, loadingLabel, diagnostics) {
  import PythonFunctionEditor.Function

  private def functions(source: ProgrammingStatePythonStructured, owner: BeEntityName) =
    PythonMethodEditing.source(source, owner).toVector.flatMap(PythonMethodEditing.functions(_, owner == PythonMethodEditing.module))

  override protected def locate(source: ProgrammingStatePythonStructured, function: Function): Option[FragmentRange] =
    functions(source, function.owner).filter(_.name == function.name).lift(function.occurrence)
      .map(found => FragmentRange(function.owner, found.span.start, found.span.end))

  /** The function the draft now starts with; a function newly typed into the draft is selected instead. */
  override protected def identify(source: ProgrammingStatePythonStructured, range: FragmentRange, previous: Function): Option[Function] = {
    val all = functions(source, range.owner)
    val drafted = all.filter(found => found.span.start >= range.start && found.span.start < range.end.max(range.start + 1))
    drafted.find(_.name != previous.name).filter(_ => drafted.size > 1).orElse(drafted.headOption)
      .map(found => Function(range.owner, found.name, all.filter(_.name == found.name).indexOf(found)))
  }

  override protected def present(stored: String): String = SourceText.dedent(stored)
  override protected def store(draft: String, stored: String): String = PythonMethodEditing.indented(draft, stored)
  override protected def cssClass: String = "java-function-editor__method"
}

object PythonFunctionEditor {
  /** The `occurrence`-th function named `name` of the module or class stored under `owner`. */
  final case class Function(owner: BeEntityName, name: String, occurrence: Int)
}

/** Edits the complete source of the module or of one class; renaming a class in its declaration renames its entry. */
final class PythonClassEditor(
    logger: Logger,
    state: Var[ProgrammingStatePythonStructured],
    loadingLabel: Signal[String],
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil)
) extends FragmentCodeEditor[ProgrammingStatePythonStructured, BeEntityName](logger, state, editorConfig, loadingLabel, diagnostics) {
  override protected def locate(source: ProgrammingStatePythonStructured, owner: BeEntityName): Option[FragmentRange] =
    PythonMethodEditing.source(source, owner).map(text => FragmentRange(owner, 0, text.length))
  override protected def identify(source: ProgrammingStatePythonStructured, range: FragmentRange, previous: BeEntityName) = Some(range.owner)
  override protected def cssClass: String = "java-function-editor__class"
}
