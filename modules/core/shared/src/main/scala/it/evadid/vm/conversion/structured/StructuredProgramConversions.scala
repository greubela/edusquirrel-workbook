package it.evadid.vm.conversion.structured

import it.evadid.core.datastructures.language.AppLanguage.{English, Java, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.BeAssignVariable
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.expressionjava.BeExpressionToJavaStr
import it.evadid.vm.conversion.expressionsnap.SnapProjectXml
import it.evadid.vm.conversion.javaexpression.JavaToBeExpressionParser
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.conversion.snapexpression.{SnapStateConversion, SnapExpressionSupport}
import it.evadid.vm.conversion.snapxml.SnapXmlParser
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.types.BeDataValueLiteral
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{SnapCanvasLayout, SnapCanvasScript}
import scala.collection.immutable.VectorMap

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
  private def removeMainCalls(source: String): String =
    source.linesIterator.zip(mask(source, true).linesIterator)
      .filterNot((_, clean) => clean.matches("main\\s*\\(\\s*\\)\\s*"))
      .map(_._1).mkString("\n")

  def javaWithLooseCode(classes: Map[BeEntityName, String], loose: String): ProgrammingStateJavaStructured = {
    val preamble = classes.get(mainName).flatMap(source => javaClasses(source).headOption.map(block => source.take(block.start))).getOrElse("")
    val existing = classes.get(mainName).map(javaMembers).getOrElse(Vector.empty)
    val looseMembers = javaMembers(s"class Main { $loose }")
    val (definitions, executable) = looseMembers.partition { member =>
      "^(?:(?:public|private|protected|static|final)\\s+)*[\\w$<>\\[\\]]+\\s+[\\w$]+\\s*\\(".r.findFirstIn(mask(member, false)).nonEmpty ||
        "^(?:final\\s+)?[A-Za-z_$][\\w$<>\\[\\]]*\\s+[A-Za-z_$][\\w$]*\\s*(?:=|;)".r.findFirstIn(member).nonEmpty
    }
    val staticDefinitions = definitions.map(member => if scope(member) == MemberScope.Static then member else "static " + member)
    val main = existing.find(methodName(_).contains("main"))
    val other = existing.filterNot(methodName(_).contains("main"))
    val body = join(main.map(methodBody).toList ++ executable)
    val entry = s"public static void main(String[] unusableArgs) {\n${indent(body)}\n}"
    val code = preamble + s"class Main {\n${indent(join(other ++ staticDefinitions :+ entry))}\n}"
    new ProgrammingStateJavaStructured(classes.updated(mainName, code))
  }

  /** Main is a presentation wrapper, not an additional runtime call. unusedCode stays a definition. */
  def moduleSource(state: ProgrammingStatePythonStructured): String = {
    val main = pythonBlocks(state.mainScript, "def").find(block => block.indent == 0 && block.name == "main")
    main match {
      case None => state.mainScript
      case Some(entry) =>
        val rest = remove(state.mainScript, Seq(entry.start -> entry.end))
        join(List(removeMainCalls(rest), entry.body))
    }
  }

  def toExpression(state: ProgrammingStatePythonStructured): ProgrammingStateBeExpression =
    ProgrammingStateBeExpression(PythonToExpression.program(join(state.definedClasses.values.toList :+ moduleSource(state))).fullProgram)

  def fromExpression(state: ProgrammingStateBeExpression): ProgrammingStatePythonStructured = {
    val source = ExpressionToPython.printedPython(state.expression)
    val parts = SourceStructure.python(source)
    val module = moduleSource(new ProgrammingStatePythonStructured(parts.definedClasses, parts.remaining))
    val functions = pythonBlocks(module, "def").filter(_.indent == 0)
    val rest = remove(module, functions.map(f => f.start -> f.end)).trim
    val main = if rest.nonEmpty then s"def main():\n${indent(rest)}\n\nmain()" else ""
    new ProgrammingStatePythonStructured(parts.definedClasses, join(functions.map(_.source) :+ main))
  }

}
