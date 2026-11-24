package it.evadid.homepage.webElements.code

import com.raquo.laminar.api.L.*
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
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    loadingLabel: Signal[String] = Val("Loading editor…")
) extends JavaFragmentEditor[BeEntityName](logger, state, editorConfig, loadingLabel) {
  override protected def locate(source: ProgrammingStateJavaStructured, name: BeEntityName): Option[JavaFragmentRange] =
    source.definedClasses.get(name).map(text => JavaFragmentRange(name, 0, text.length))
  override protected def identify(source: ProgrammingStateJavaStructured, range: JavaFragmentRange, previous: BeEntityName) =
    Some(range.className)
  override protected def cssClass: String = "java-function-editor__class"
}
