package it.evadid.homepage.webElements.editor.code.JavaEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured

/** Edits the complete source of one class, identified by its name in the answer. Renaming the class in its
  * declaration renames its entry.
  */
final class JavaClassEditor(
    logger: Logger,
    state: Var[ProgrammingStateJavaStructured],
    loadingLabel: Signal[String],
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil)
) extends JavaFragmentEditor[BeEntityName](logger, state, editorConfig, loadingLabel, diagnostics) {
  override protected def locate(source: ProgrammingStateJavaStructured, name: BeEntityName): Option[JavaFragmentRange] =
    source.definedClasses.get(name).map(text => JavaFragmentRange(name, 0, text.length))
  override protected def identify(source: ProgrammingStateJavaStructured, range: JavaFragmentRange, previous: BeEntityName) =
    Some(range.className)
  override protected def cssClass: String = "java-function-editor__class"
}
