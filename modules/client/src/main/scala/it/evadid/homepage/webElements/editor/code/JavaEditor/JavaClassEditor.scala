package it.evadid.homepage.webElements.editor.code.JavaEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.fragment.{FragmentCodeEditor, FragmentRange}
import it.evadid.homepage.webElements.editor.code.fragment.FragmentCodeEditor.Scope
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured

/** Edits the complete source of one class, identified by its name in the answer. Renaming the class in its
  * declaration renames its entry; a second complete class in the draft needs the whole program.
  */
final class JavaClassEditor(
    logger: Logger,
    state: Var[ProgrammingStateJavaStructured],
    loadingLabel: Signal[String],
    editorConfig: CodeMirrorEditorConfig = CodeMirrorEditorConfig.Default,
    diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil)
) extends FragmentCodeEditor[ProgrammingStateJavaStructured, BeEntityName](logger, state, editorConfig, loadingLabel, diagnostics) {
  override protected def locate(source: ProgrammingStateJavaStructured, name: BeEntityName): Option[FragmentRange] =
    source.definedClasses.get(name).map(text => FragmentRange(name, 0, text.length))
  override protected def identify(source: ProgrammingStateJavaStructured, range: FragmentRange, previous: BeEntityName) =
    Some(range.owner)
  override protected def widening(source: ProgrammingStateJavaStructured, range: FragmentRange): Option[Scope] =
    source.definedClasses.get(range.owner).flatMap { text =>
      val structured = SourceParsing.parse(text, SourceLanguage.Java).structured
      Option.when(structured.balanced && structured.topLevelClasses.count(owner => owner.closed && !owner.library) > 1)(Scope.Program)
    }
  override protected def cssClass: String = "java-function-editor__class"
}
