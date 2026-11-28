package it.evadid.homepage.webElements.editor.code.PythonEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.fragment.{FragmentCodeEditor, FragmentRange}
import it.evadid.homepage.webElements.editor.code.fragment.FragmentCodeEditor.Scope
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.editing.PythonMethodEditing
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.source.{PythonFunctionStructured, SourceParsing, SourceText}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.interactions.programming.state.ProgrammingStatePythonStructured

/** Edits one function of the module or a method of a class, shown without its class indentation. Any code typed
  * before or after the function needs its module or class, which then shows the whole draft.
  */
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

  /** The functions starting in `range`. */
  private def drafted(source: ProgrammingStatePythonStructured, range: FragmentRange): Vector[PythonFunctionStructured] =
    functions(source, range.owner).filter(found => found.span.start >= range.start && found.span.start < range.end.max(range.start + 1))

  /** The function the draft now holds, if it holds only that one. */
  override protected def identify(source: ProgrammingStatePythonStructured, range: FragmentRange, previous: Function): Option[Function] =
    drafted(source, range) match {
      case Vector(found) if onlyTriviaAround(source, range, Seq(found.span)) =>
        Some(Function(range.owner, found.name, functions(source, range.owner).filter(_.name == found.name).indexOf(found)))
      case _ => None
    }

  override protected def widening(source: ProgrammingStatePythonStructured, range: FragmentRange): Option[Scope] =
    memberWidening(source, range, drafted(source, range).map(_.span))

  override protected def present(stored: String): String = SourceText.dedent(stored)
  override protected def store(draft: String, stored: String): String = PythonMethodEditing.indented(draft, stored)
  override protected def cssClass: String = "java-function-editor__method"
}

object PythonFunctionEditor {
  /** The `occurrence`-th function named `name` of the module or class stored under `owner`. */
  final case class Function(owner: BeEntityName, name: String, occurrence: Int)
}

/** Edits the complete source of the module or of one class; renaming a class in its declaration renames its entry.
  * Code typed outside the class, such as a second class, needs the whole program.
  */
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
  /** A class with its decorators; the module is the whole program's own source. */
  override protected def widening(source: ProgrammingStatePythonStructured, range: FragmentRange): Option[Scope] =
    ownerWidening(source, range, PythonMethodEditing.source(source, range.owner).filter(_ => range.owner != PythonMethodEditing.module).toSeq
      .flatMap { code =>
        val structured = SourceParsing.parse(code, SourceLanguage.Python).structured
        val decorators = structured.codeLines.takeWhile(_.code.startsWith("@"))
        structured.classes.headOption.map(owner => decorators.headOption.fold(owner.span)(first => owner.span.copy(start = first.span.start,
          startLine = first.span.startLine)))
      })
  override protected def cssClass: String = "java-function-editor__class"
}
