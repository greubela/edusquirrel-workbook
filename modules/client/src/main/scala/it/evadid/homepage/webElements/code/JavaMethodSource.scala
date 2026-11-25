package it.evadid.homepage.webElements.code

import it.evadid.vm.parsing.source.{JavaClassStructured, JavaFunctionStructured, SourceParsing}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage

/** Editing one method of a Java class source at a time, on the declarations of the parsing pipeline's first step
  * ([[SourceParsing]]). Offsets are relative to the class source; library classes such as the generated `Turtle`
  * belong to the runtime and are not listed.
  */
object JavaMethodSource {
  private[code] final case class MethodRange(start: Int, end: Int) {
    def isWithin(source: String): Boolean = start >= 0 && end >= start && end <= source.length

    def replace(source: String, draft: String): Option[(String, MethodRange)] =
      Option.when(isWithin(source))(
        (source.substring(0, start) + draft + source.substring(end), MethodRange(start, start + draft.length))
      )
  }

  private[code] def firstLineNumber(source: String, range: Option[MethodRange]): Int =
    range.filter(_.isWithin(source)).fold(1) { current =>
      1 + (0 until current.start).count { index =>
        source.charAt(index) == '\n' || source.charAt(index) == '\r' &&
          (index + 1 == source.length || source.charAt(index + 1) != '\n')
      }
    }

  /** Whether `code` is a complete program: the grammar reads it and every bracket, string and comment is closed. */
  private[code] def parses(code: String): Boolean = {
    val parsed = SourceParsing.parse(code, SourceLanguage.Java)
    parsed.syntax.isRight && parsed.structured.balanced
  }

  /** The `static void main(String[] args)` entry point, if it is the only `main` (of class `Main`, if present). */
  private[code] def initialMethod(source: String, requirePublicMain: Boolean = true): Option[JavaFunctionStructured] = {
    val structured = SourceParsing.parse(source, SourceLanguage.Java).structured
    if !structured.balanced then None
    else {
      val classes = discover(source)
      val entryClasses = if !requirePublicMain && classes.exists(_.name == "Main") then classes.filter(_.name == "Main") else classes
      entryClasses.flatMap(_.functions).filter(_.name == "main") match
        case Vector(function) if function.returnType.contains("void") &&
          function.parameters.map(_.javaType.filterNot(_.isWhitespace)).exists(Set("String[]", "java.lang.String[]")) &&
          findSelection(classes, function).isDefined =>
          Option.when((!requirePublicMain || function.modifiers.contains("public")) && function.isStatic)(function)
        case _ => None
    }
  }

  /** The program's classes with closed bodies, each with its methods whose bodies are closed. */
  private[code] def discover(source: String): Vector[JavaClassStructured] =
    SourceParsing.parse(source, SourceLanguage.Java).structured.programClasses.filter(_.closed)
      .map(owner => owner.copy(members = owner.members.filter {
        case function: JavaFunctionStructured => function.closed
        case _ => true
      }))

  /** The offset of a closed class body's `}`. */
  private[code] def closingBrace(owner: JavaClassStructured): Int = owner.bodySpan.fold(owner.span.end)(_.end)

  private[code] def replaceMethod(source: String, function: JavaFunctionStructured, draft: String): String =
    source.substring(0, function.span.start) + draft + source.substring(function.span.end)

  private[code] def findEditedMethod(
      source: String,
      currentClasses: Vector[JavaClassStructured],
      previous: JavaFunctionStructured,
      range: MethodRange
  ): Option[JavaFunctionStructured] =
    if !range.isWithin(source) then None
    else currentClasses.filter(_.name == previous.className) match
      case Vector(owner) if owner.bodySpan.exists(body => range.start >= body.start && range.end <= body.end) =>
        owner.functions.filter(function => function.span.start >= range.start && function.span.end <= range.end) match
          case Vector(function) if onlyTrivia(source.substring(range.start, function.span.start)) &&
            onlyTrivia(source.substring(function.span.end, range.end)) => Some(function)
          case functions if functions.size > 1 && onlyTrivia(source.substring(range.start, functions.head.span.start)) &&
            onlyTrivia(source.substring(functions.last.span.end, range.end)) => Some(functions.last)
          case _ => None
      case _ => None

  /** Same class, name and signature; offsets and bodies may differ. */
  private[code] def sameMethod(first: JavaFunctionStructured, second: JavaFunctionStructured): Boolean =
    first.className == second.className && first.name == second.name &&
      first.returnType == second.returnType && first.parameterList == second.parameterList

  private[code] def findSelection(classes: Vector[JavaClassStructured], previous: JavaFunctionStructured): Option[JavaFunctionStructured] =
    classes.filter(_.name == previous.className) match
      case Vector(owner) => owner.functions.filter(sameMethod(_, previous)) match
        case Vector(function) => Some(function)
        case _ => None
      case _ => None

  /** Only whitespace and closed comments. */
  private def onlyTrivia(source: String): Boolean = SourceParsing.parse(source, SourceLanguage.Java).structured.trivia

  private[code] def lineSeparator(source: String): String =
    "\r\n|\r|\n".r.findFirstIn(source).getOrElse("\n")

  private[code] def methodDeclaration(returnType: String, name: String, parameters: String, isStatic: Boolean): String = {
    val modifiers = if isStatic then "public static" else "public"
    s"$modifiers $returnType $name($parameters) {\n    \n  }"
  }

  private[code] def insertMethod(source: String, owner: JavaClassStructured, declaration: String): String = {
    val brace = closingBrace(owner)
    val newline = lineSeparator(source)
    val inserted = declaration.replaceAll("\r\n|\r|\n", newline)
    source.substring(0, brace) + s"$newline  $inserted$newline" + source.substring(brace)
  }
}
