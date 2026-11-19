package it.evadid.vm.conversion.structured

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
    val methodPattern = "^(?:(?:public|private|protected|static|final)\\s+)*[\\w$<>\\[\\]]+\\s+[\\w$]+\\s*\\(".r
    val variablePattern = """^(?:(?:public|private|protected|static|final)\s+)*([\w$<>\[\]]+)\s+([\w$]+)\s*(?:=\s*(.*?))?;?$""".r
    val (definitions, executable) = looseMembers.partition(member => methodPattern.findFirstIn(mask(member, false)).nonEmpty)
    val staticDefinitions = definitions.map(member => if scope(member) == MemberScope.Static then member else "static " + member)
    // Declarations belong to Main, but initializers remain at their original execution position.
    val fields = executable.collect { case variablePattern(typ, name, _) => s"static $typ $name;" }
    val startup = executable.flatMap {
      case variablePattern(_, name, value) => Option(value).map(initial => s"$name = ${initial.stripSuffix(";")};")
      case statement => Some(statement)
    }
    val main = existing.find(member => methodName(member).contains("main") && member.takeWhile(_ != '{').contains("String"))
    val other = existing.filterNot(member => main.contains(member))
    val body = join(main.map(methodBody).toList ++ startup)
    val entry = s"public static void main(String[] unusableArgs) {\n${indent(body)}\n}"
    val code = preamble + s"class Main {\n${indent(join(other ++ fields ++ staticDefinitions :+ entry))}\n}"
    new ProgrammingStateJavaStructured(classes.updated(mainName, code))
  }

}
