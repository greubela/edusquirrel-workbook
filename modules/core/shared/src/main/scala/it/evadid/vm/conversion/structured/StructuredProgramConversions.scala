package it.evadid.vm.conversion.structured

import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.*


/** Class-level conversions. The reserved Main class represents module/static scope. */
object StructuredProgramConversions {
  import SourceStructure.*
  private val mainName = BeEntityName.fromCodeString("Main")
  private def join(parts: Iterable[String]): String = parts.filter(_.trim.nonEmpty).mkString("\n\n")
  def javaWithLooseCode(classes: Map[BeEntityName, String], loose: String): ProgrammingStateJavaStructured = {
    val preamble = classes.get(mainName).flatMap(source => javaClasses(source).headOption.map(block => source.take(block.start))).getOrElse("")
    val existing = classes.get(mainName).map(javaMembers).getOrElse(Vector.empty)
    val looseMembers = javaMembers(s"class Main { $loose }")
    val (definitions, executable) = looseMembers.partition(member => methodName(member).nonEmpty)
    val staticDefinitions = definitions.map(member => if scope(member) == MemberScope.Static then member else "static " + member)
    // Declarations belong to Main, but initializers remain at their original execution position.
    val fields = executable.flatMap(declaration).map(field => s"static ${field.javaType} ${field.name};")
    val startup = executable.flatMap { member =>
      declaration(member) match {
        case Some(field) => field.initializer.map(initial => s"${field.name} = $initial;")
        case None => Some(member)
      }
    }
    val main = existing.find(member => methodName(member).contains("main") && header(member).exists {
      case group: Group => group.open == '(' && group.children.exists { case Word(_, _, "String") => true; case _ => false }
      case _ => false
    })
    val other = existing.filterNot(member => main.contains(member))
    val body = join(main.flatMap(SourceStructure.body).toList ++ startup)
    val entry = s"public static void main(String[] unusableArgs) {\n${indent(body)}\n}"
    val code = preamble + s"class Main {\n${indent(join(other ++ fields ++ staticDefinitions :+ entry))}\n}"
    new ProgrammingStateJavaStructured(classes.updated(mainName, code))
  }

}
