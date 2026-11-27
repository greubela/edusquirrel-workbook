package it.evadid.homepage.webElements.editor.code.PythonEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.fragment.{FragmentCodeEditor, FragmentRange}
import it.evadid.homepage.webElements.editor.code.fragment.FragmentCodeEditor.Scope
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.vm.editing.PythonMethodEditing
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.source.{PythonFunctionStructured, PythonLineStructured, SourceParsing, SourceText}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonStructured

/** Edits one function of the module or a method of a class, shown without its class indentation. A draft with
  * further complete functions needs their module or class; one with an attribute (an assignment) the whole program.
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

  /** The functions starting in `range` and its code lines outside them. */
  private def drafted(source: ProgrammingStatePythonStructured, range: FragmentRange): (Vector[PythonFunctionStructured], Vector[PythonLineStructured]) = {
    def starts(start: Int) = start >= range.start && start < range.end.max(range.start + 1)
    val found = functions(source, range.owner).filter(function => starts(function.span.start))
    val lines = PythonMethodEditing.source(source, range.owner).toVector.flatMap(SourceParsing.parse(_, SourceLanguage.Python).structured.codeLines)
    (found, lines.filter(line => starts(line.span.start) && !found.exists(_.span.contains(line.span.start))))
  }

  /** The function the draft now holds, if it holds only that one. */
  override protected def identify(source: ProgrammingStatePythonStructured, range: FragmentRange, previous: Function): Option[Function] =
    drafted(source, range) match {
      case (Vector(found), Vector()) =>
        Some(Function(range.owner, found.name, functions(source, range.owner).filter(_.name == found.name).indexOf(found)))
      case _ => None
    }

  override protected def widening(source: ProgrammingStatePythonStructured, range: FragmentRange): Option[Scope] = {
    val (found, others) = drafted(source, range)
    Option.when(found.nonEmpty && found.size + others.size > 1 && others.forall(line => PythonFragmentEditors.attribute(line.code)) &&
      range.text(source).map(SourceText.dedent).exists(PythonMethodEditing.valid))(if others.isEmpty then Scope.Owner else Scope.Program)
  }

  override protected def present(stored: String): String = SourceText.dedent(stored)
  override protected def store(draft: String, stored: String): String = PythonMethodEditing.indented(draft, stored)
  override protected def cssClass: String = "java-function-editor__method"
}

object PythonFunctionEditor {
  /** The `occurrence`-th function named `name` of the module or class stored under `owner`. */
  final case class Function(owner: BeEntityName, name: String, occurrence: Int)
}

/** Edits the complete source of the module or of one class; renaming a class in its declaration renames its entry.
  * A class whose draft holds further top-level code, such as a second class, needs the whole program.
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
  override protected def widening(source: ProgrammingStatePythonStructured, range: FragmentRange): Option[Scope] =
    PythonMethodEditing.source(source, range.owner).filter(_ => range.owner != PythonMethodEditing.module).flatMap { code =>
      val topLevel = SourceParsing.parse(code, SourceLanguage.Python).structured.codeLines.filter(line => line.indent == 0 && !line.code.startsWith("@"))
      Option.when(topLevel.size > 1 && PythonMethodEditing.valid(code))(Scope.Program)
    }
  override protected def cssClass: String = "java-function-editor__class"
}

private object PythonFragmentEditors {
  private val assignment = raw"[A-Za-z_][A-Za-z0-9_]*\s*(:[^=]+)?=[^=].*".r

  /** Whether a logical line declares an attribute: `size = 3` or `size: int = 3`. */
  def attribute(code: String): Boolean = assignment.matches(code)
}
