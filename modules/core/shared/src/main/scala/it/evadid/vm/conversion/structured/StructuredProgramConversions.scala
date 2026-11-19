package it.evadid.vm.conversion.structured

import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.*


/** Class-level conversions. The reserved Main class represents module/static scope. */
object StructuredProgramConversions {
  import SourceStructure.*
  private val mainName = BeEntityName.fromCodeString("Main")
  private def join(parts: Iterable[String]): String = parts.filter(_.trim.nonEmpty).mkString("\n\n")
  private def methodName(source: String): Option[String] = {
    val header = mask(source.takeWhile(_ != '{'), false)
    if header.contains('=') then None
    else "([A-Za-z_$][\\w$]*)\\s*\\(".r.findFirstMatchIn(header).map(_.group(1))
  }
  private def methodBody(source: String): String = {
    val clean = mask(source, false)
    val open = clean.indexOf('{')
    val close = clean.lastIndexOf('}')
    require(open >= 0 && close > open, "A method body is incomplete.")
    dedent(source.substring(open + 1, close))
  }
  def javaWithLooseCode(classes: Map[BeEntityName, String], loose: String): ProgrammingStateJavaStructured = {
    val preamble = classes.get(mainName).flatMap(source => javaClasses(source).headOption.map(block => source.take(block.start))).getOrElse("")
    val existing = classes.get(mainName).map(javaMembers).getOrElse(Vector.empty)
    val looseMembers = javaMembers(s"class Main { $loose }")
    val (definitions, executable) = looseMembers.partition { member =>
      "^(?:(?:public|private|protected|static|final)\\s+)*[\\w$<>\\[\\]]+\\s+[\\w$]+\\s*\\(".r.findFirstIn(mask(member, false)).nonEmpty ||
        "^(?:final\\s+)?[A-Za-z_$][\\w$<>\\[\\]]*\\s+[A-Za-z_$][\\w$]*\\s*(?:=|;)".r.findFirstIn(member).nonEmpty
    }
    val staticDefinitions = definitions.map(member => if scope(member) == MemberScope.Static then member else "static " + member)
    val entryDefinitions = staticDefinitions.filter(methodName(_).contains("main"))
    val helperDefinitions = staticDefinitions.filterNot(methodName(_).contains("main"))
    val main = existing.find(methodName(_).contains("main")).orElse(entryDefinitions.headOption)
    val other = existing.filterNot(methodName(_).contains("main"))
    val startup = if entryDefinitions.nonEmpty then executable.filterNot(_.trim.matches("main\\s*\\(\\s*\\)\\s*;?")) else executable
    val body = join(main.map(methodBody).toList ++ startup)
    val entry = s"public static void main(String[] unusableArgs) {\n${indent(body)}\n}"
    val code = preamble + s"class Main {\n${indent(join(other ++ helperDefinitions :+ entry))}\n}"
    new ProgrammingStateJavaStructured(classes.updated(mainName, code))
  }

  def toExpression(state: ProgrammingStatePythonStructured): ProgrammingStateBeExpression =
    ProgrammingStateBeExpression(PythonToExpression.program(state.toString).fullProgram)

  def fromExpression(state: ProgrammingStateBeExpression): ProgrammingStatePythonStructured =
    ProgrammingStatePythonStructured(ExpressionToPython.printedPython(state.expression))
}
