package it.evadid.vm.conversion.structured

import it.evadid.util.logging.Logger
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.source.*
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.interactions.programming.state.*

import scala.collection.immutable.VectorMap

/** Class-level conversions between source text and structured states, built on the step-1 structure of
  * [[SourceParsing]]. The reserved `Main` class represents module/static scope.
  */
object StructuredProgramConversions {
  /** Classes hoisted out of a source (first definition per name) and the code that remains. */
  final case class Parts(definedClasses: Map[BeEntityName, String], remaining: String,
      prepared: Option[SourceTextWithOrigins] = None)

  private val logger = Logger.withNameAndPrefixes(Some("StructuredProgramConversions"))
  private val mainName = BeEntityName.fromCodeString("Main")
  private def join(parts: Iterable[String]): String = parts.filter(_.trim.nonEmpty).mkString("\n\n")

  private def hoist(source: String, blocks: Vector[(String, SourceSpan)], python: Boolean, warn: String => Unit): Parts = {
    val original = SourceTextWithOrigins(source)
    val classes = blocks.foldLeft(VectorMap.empty[BeEntityName, SourceTextWithOrigins]) { case (saved, (name, span)) =>
      val entity = BeEntityName.fromCodeString(name)
      if saved.keys.exists(_.universalInterpretation() == entity.universalInterpretation()) then {
        warn(s"Duplicate class '$name' ignored; keeping the first definition."); saved
      } else {
        val children = blocks.map(_._2).filter(child => child.start > span.start && child.end <= span.end)
        val text = original.slice(span.start, span.end).without(children.map(child => (child.start - span.start, child.end - span.start)))
        saved.updated(entity, if python then repairEmptyBlocks(text.dedented) else text)
      }
    }
    val rest = original.without(blocks.map((_, span) => span.start -> span.end))
    val remaining = (if python then repairEmptyBlocks(rest) else rest).trimmed
    val prepared = SourceTextWithOrigins.join((classes.values.toSeq :+ remaining).filter(_.text.nonEmpty), "\n\n")
    Parts(VectorMap.from(classes.map((key, value) => key -> value.text)), remaining.text, Option.when(python)(prepared))
  }

  /** A block header left without a body after hoisting receives `pass`, anchored to that header. */
  private def repairEmptyBlocks(source: SourceTextWithOrigins): SourceTextWithOrigins = {
    val lines = SourceParsing.parse(source.text, SourceLanguage.Python).structured.lines
    val newline = if source.text.contains("\r\n") then "\r\n" else if source.text.contains("\r") then "\r" else "\n"
    // Insert only the missing bodies: rebuilding logical lines can add blank lines inside opaque imports/strings.
    lines.zipWithIndex.reverse.foldLeft(source) { case (current, (line, index)) =>
      val next = lines.drop(index + 1).find(_.code.nonEmpty)
      if line.opensBlock && next.forall(_.indent <= line.indent) then {
        val at = if source.text.lift(line.span.end - 1).contains('\r') then line.span.end - 1 else line.span.end
        val pass = newline + " " * (line.indent + 4) + "pass"
        val inserted = SourceTextWithOrigins(pass, Vector.fill(pass.length)(source.offsets.lift(at - 1).getOrElse(0)))
        SourceTextWithOrigins.join(Seq(current.slice(0, at), inserted, current.slice(at, current.text.length)), "")
      } else current
    }
  }

  /** Java classes as entries; package and import declarations, and remaining comments, become the first class's preamble. */
  def javaParts(source: String, warn: String => Unit = logger.logWarn): Parts = {
    val structured = SourceParsing.parse(source, SourceLanguage.Java).structured
    val parts = hoist(source, structured.classes.map(owner => owner.name -> owner.span), python = false, warn)
    val declarations = (structured.topLevelClasses.map(_.span) ++ structured.preamble.map(_.span))
      .map(span => span.start -> span.end).sorted
    val gaps = (declarations.flatMap((start, end) => Vector(start, end)).prepended(0) :+ source.length).grouped(2)
      .collect { case Vector(start, end) if start < end => start -> end }.toVector
    val onlyTrivia = gaps.forall((start, end) => structured.onlyTrivia(start, end))
    val rest = SourceText.remove(source, declarations).trim
    val preamble = (structured.preamble.map(_.text) ++ Option.when(onlyTrivia)(rest)).filter(_.nonEmpty).mkString("\n")
    if preamble.isEmpty then parts
    else {
      val (name, body) = parts.definedClasses.headOption.getOrElse(mainName -> "class Main {}")
      Parts(parts.definedClasses.updated(name, preamble + "\n" + body), if onlyTrivia then "" else rest)
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
    val looseMembers = SourceParsing.parse(loose, SourceLanguage.Java).structured.looseMembers
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
