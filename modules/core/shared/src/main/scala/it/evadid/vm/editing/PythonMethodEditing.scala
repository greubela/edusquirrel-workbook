package it.evadid.vm.editing

import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.source.*
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonStructured

/** Source ranges for Python's module, classes and functions, using the shared tolerant parsing stages. */
object PythonMethodEditing {
  val module: BeEntityName = BeEntityName.fromCodeString("__main__")

  def source(state: ProgrammingStatePythonStructured, owner: BeEntityName): Option[String] =
    if owner == module then Some(state.mainScript) else state.definedClasses.get(owner)

  def updated(state: ProgrammingStatePythonStructured, owner: BeEntityName, code: String): ProgrammingStatePythonStructured =
    if owner == module then state.copy(mainScript = code)
    else {
      val key = SourceParsing.parse(code, SourceLanguage.Python).structured.classes.headOption
        .map(clazz => BeEntityName.fromCodeString(clazz.name)).getOrElse(owner)
      state.copy(definedClasses = scala.collection.immutable.VectorMap.from(state.definedClasses.map { (existing, value) =>
        if existing == owner then key -> code else existing -> value
      }))
    }

  def functions(code: String, moduleScope: Boolean): Vector[PythonFunctionStructured] = {
    val all = SourceParsing.parse(code, SourceLanguage.Python).structured.functions
    if moduleScope then all.filter(function => function.owner.isEmpty && function.indent == 0)
    else {
      val methods = all.filter(_.owner.nonEmpty)
      methods.filter(_.indent == methods.map(_.indent).minOption.getOrElse(0))
    }
  }

  /** Replaces a dedented method fragment and restores the indentation of its original declaration. */
  def replace(code: String, function: PythonFunctionStructured, draft: String): String = {
    val original = function.span.text(code)
    val indent = original.takeWhile(c => c == ' ' || c == '\t')
    val body = draft.linesIterator.map(line => if line.isEmpty then "" else indent + line).mkString("\n")
    val suffix = if original.endsWith("\n") then "\n" else ""
    code.take(function.span.start) + body + suffix + code.drop(function.span.end)
  }

  private val keywords = Set("False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue",
    "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
    "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield")

  def validName(name: String): Boolean = name.headOption.exists(c => c.isLetter || c == '_') &&
    name.forall(c => c.isLetterOrDigit || c == '_') && !keywords(name)

  def valid(code: String): Boolean = {
    val parsed = SourceParsing.parse(code, SourceLanguage.Python)
    def readable(node: it.evadid.vm.parsing.python.clean.model.PyAST): Boolean = node match {
      case _: it.evadid.vm.parsing.python.clean.model.PyAST.PyUnparsableStatement => false
      case other => other.getChildren().collect { case child: it.evadid.vm.parsing.python.clean.model.PyAST => child }.forall(readable)
    }
    parsed.structured.balanced && parsed.syntax.toOption.exists(syntax => readable(syntax.program)) &&
      parsed.structured.classes.forall(owner => validName(owner.name)) &&
      parsed.structured.functions.forall(function => validName(function.name) && function.parameters.forall(parameter => validName(parameter.name)))
  }

  def insert(code: String, declaration: String, moduleScope: Boolean): String = {
    val body = if moduleScope then declaration else SourceText.indent(declaration)
    val parsed = SourceParsing.parse(code, SourceLanguage.Python).structured
    val empty = Option.when(!moduleScope)(parsed.codeLines.drop(1)).filter(_.map(_.code) == Vector("pass"))
    val base = empty.fold(code)(lines => code.take(lines.head.span.start))
    val main = Option.when(moduleScope)(functions(code, true).find(_.name == "main")).flatten
    main.fold(base.stripTrailing() + "\n\n" + body + "\n")(function =>
      code.take(function.span.start) + body + "\n\n" + code.drop(function.span.start))
  }
}
