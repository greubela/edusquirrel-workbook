package it.evadid.vm.conversion.structured

import it.evadid.util.logging.Logger
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.source.*
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.elements.interactionElements.programming.state.*

import scala.collection.immutable.VectorMap

/** Class-level conversions between source text and structured states, built on the step-1 structure of
  * [[SourceParsing]]. The reserved `Main` class represents module/static scope.
  */
object StructuredProgramConversions {
  /** Classes hoisted out of a source (first definition per name) and the code that remains. */
  final case class Parts(definedClasses: Map[BeEntityName, String], remaining: String)

  private val logger = Logger.withNameAndPrefixes(Some("StructuredProgramConversions"))
  private val mainName = BeEntityName.fromCodeString("Main")
  private def join(parts: Iterable[String]): String = parts.filter(_.trim.nonEmpty).mkString("\n\n")

  private def hoist(source: String, blocks: Vector[(String, SourceSpan)], python: Boolean, warn: String => Unit): Parts = {
    val classes = blocks.foldLeft(VectorMap.empty[BeEntityName, String]) { case (saved, (name, span)) =>
      val entity = BeEntityName.fromCodeString(name)
      if saved.keys.exists(_.universalInterpretation() == entity.universalInterpretation()) then {
        warn(s"Duplicate class '$name' ignored; keeping the first definition."); saved
      } else {
        val children = blocks.map(_._2).filter(child => child.start > span.start && child.end <= span.end)
        val text = SourceText.remove(span.text(source), children.map(child => (child.start - span.start, child.end - span.start)))
        saved.updated(entity, if python then repairEmptyBlocks(SourceText.dedent(text)) else text)
      }
    }
    val remaining = SourceText.remove(source, blocks.map((_, span) => span.start -> span.end))
    Parts(classes, if python then repairEmptyBlocks(remaining).trim else remaining.trim)
  }

  /** A block header left without a body after hoisting receives `pass`. */
  private def repairEmptyBlocks(source: String): String = {
    val lines = SourceParsing.parse(source, SourceLanguage.Python).structured.lines
    lines.zipWithIndex.flatMap { (line, index) =>
      val text = line.span.text(source)
      val next = lines.drop(index + 1).find(_.code.nonEmpty)
      if line.opensBlock && next.forall(_.indent <= line.indent) then Vector(text, " " * (line.indent + 4) + "pass") else Vector(text)
    }.mkString("\n")
  }

  /** Java classes as entries; package and import declarations, and remaining comments, become the first class's preamble. */
  def javaParts(source: String, warn: String => Unit = logger.logWarn): Parts = {
    val structured = SourceParsing.parse(source, SourceLanguage.Java).structured
    val parts = hoist(source, structured.classes.map(owner => owner.name -> owner.span), python = false, warn)
    val remaining = SourceParsing.parse(parts.remaining, SourceLanguage.Java).structured
    val imports = remaining.preamble.map(declaration => declaration.span.start -> declaration.span.end)
    val rest = SourceText.remove(parts.remaining, imports)
    val onlyTrivia = SourceParsing.parse(rest, SourceLanguage.Java).structured.trivia
    val preamble = (remaining.preamble.map(_.text) ++ Option.when(onlyTrivia)(rest.trim)).filter(_.nonEmpty).mkString("\n")
    if preamble.isEmpty then parts
    else {
      val (name, body) = parts.definedClasses.headOption.getOrElse(mainName -> "class Main {}")
      Parts(parts.definedClasses.updated(name, preamble + "\n" + body), if onlyTrivia then "" else rest.trim)
    }
  }

  /** Python classes as entries and the module code that remains. */
  def pythonParts(source: String, warn: String => Unit = logger.logWarn): Parts = {
    val structured = SourceParsing.parse(source, SourceLanguage.Python).structured
    hoist(source, structured.classes.map(owner => owner.name -> owner.span), python = true, warn)
  }

  /** Loose Java code becomes `Main`: declarations become static members, other statements `main`'s body in order. */
  def javaWithLooseCode(classes: Map[BeEntityName, String], loose: String): ProgrammingStateJavaStructured = {
    val existingMain = classes.get(mainName).flatMap(source => SourceParsing.parse(source, SourceLanguage.Java).structured.topLevelClasses.headOption
      .map(owner => source.take(owner.span.start) -> owner))
    val preamble = existingMain.fold("")(_._1)
    val existing = existingMain.toVector.flatMap(_._2.members)
    val looseMembers = SourceParsing.parse(s"class Main { $loose }", SourceLanguage.Java).structured.topLevelClasses.headOption.toVector.flatMap(_.members)
    val definitions = looseMembers.collect { case function: JavaFunctionStructured => function }
    val staticDefinitions = definitions.map(function => if function.isStatic then function.code else "static " + function.code)
    val executable = looseMembers.filterNot(_.isInstanceOf[JavaFunctionStructured])
    // Declarations belong to Main, but initializers remain at their original execution position.
    val fields = executable.collect { case field: JavaFieldStructured => s"static ${field.javaType} ${field.name};" }
    val startup = executable.flatMap {
      case field: JavaFieldStructured => field.initializer.map(initial => s"${field.name} = $initial;")
      case other => Some(other.code)
    }
    val main = existing.collectFirst { case function: JavaFunctionStructured if function.name == "main" &&
        function.parameters.exists(_.javaType.startsWith("String")) => function }
    val other = existing.filterNot(member => main.contains(member)).map(_.code)
    val body = join(main.flatMap(_.body).toList ++ startup)
    val entry = s"public static void main(String[] unusableArgs) {\n${SourceText.indent(body)}\n}"
    val code = preamble + s"class Main {\n${SourceText.indent(join(other ++ fields ++ staticDefinitions :+ entry))}\n}"
    new ProgrammingStateJavaStructured(classes.updated(mainName, code))
  }
}
