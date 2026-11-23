package it.evadid.homepage.webElements.code

import it.evadid.core.datastructures.language.AppLanguage.Java
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.conversion.structured.SourceStructure

/** Methods of Java class sources, for selecting and editing one method at a time. */
object JavaMethodSource {

  /** A method of a class source: its signature and the offsets from its first token to its `}`. */
  final case class JavaMethod(
                                               className: String,
                                               name: String,
                                               returnType: String,
                                               parameters: String,
                                               start: Int,
                                               end: Int
                                             ) derives upickle.default.ReadWriter

  final case class JavaClass(name: String, bodyStart: Int, bodyEnd: Int, methods: List[JavaMethod]) derives upickle.default.ReadWriter

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

  private[code] def parses(code: String): Boolean =
    JavaParser.parse(code).isRight && structurallyBalanced(code)

  /** The `static void main(String[] args)` entry point, if it is the only `main` (of class `Main`, if present). */
  private[code] def initialMethod(source: String, requirePublicMain: Boolean = true): Option[JavaMethod] =
    entryMethod(source, discover(source), SourceStructure.balanced(source), requirePublicMain)

  private def entryMethod(source: String, currentClasses: List[JavaClass], balanced: Boolean,
      requirePublicMain: Boolean): Option[JavaMethod] = {
    if !balanced then None
    else {
      val entryClasses = if !requirePublicMain && currentClasses.exists(_.name == "Main") then
        currentClasses.filter(_.name == "Main") else currentClasses
      entryClasses.flatMap(_.methods).filter(_.name == "main") match
        case List(function) if function.returnType == "void" &&
          SourceStructure.declaration(function.parameters).exists(parameter =>
            Set("String[]", "java.lang.String[]")(parameter.javaType.filterNot(_.isWhitespace))) &&
          findSelection(currentClasses, function).isDefined =>
          val modifiers = SourceStructure.header(source.substring(function.start, function.end)).collect {
            case SourceStructure.Word(_, _, word) => word
          }.toSet
          Option.when((!requirePublicMain || modifiers.contains("public")) && modifiers.contains("static"))(function)
        case _ => None
    }
  }

  private[code] def structurallyBalanced(source: String): Boolean = SourceStructure.balanced(source)

  /** Classes with closed bodies and their methods, from the shared fastparse source lexer. Known
    * library classes, such as the generated `Turtle`, belong to the runtime and are not listed.
    */
  private[code] def discover(source: String): List[JavaClass] = {
    val libraries = KnownDependencies.definitionRanges(source, Java)
    SourceStructure.javaClassSpans(source).toList.filterNot(clazz =>
      libraries.exists((start, end) => start <= clazz.bodyOpen && clazz.bodyClose < end)).map { clazz =>
      JavaClass(clazz.name, clazz.bodyOpen, clazz.bodyClose, clazz.methods.toList.map(method =>
        JavaMethod(clazz.name, method.name, method.returnType, method.parameters, method.start, method.end)))
    }
  }

  private[code] def replaceMethod(source: String, function: JavaMethod, draft: String): String =
    source.substring(0, function.start) + draft + source.substring(function.end)

  private[code] def findEditedMethod(
                                        source: String,
                                        currentClasses: List[JavaClass],
                                        previous: JavaMethod,
                                        range: MethodRange
                                      ): Option[JavaMethod] = {
    if !range.isWithin(source) then None
    else currentClasses.filter(_.name == previous.className) match
      case List(clazz) if range.start > clazz.bodyStart && range.end <= clazz.bodyEnd =>
        clazz.methods.filter(function => function.start >= range.start && function.end <= range.end) match
          case List(function) if onlyTrivia(source.substring(range.start, function.start)) &&
            onlyTrivia(source.substring(function.end, range.end)) => Some(function)
          case _ => None
      case _ => None
  }

  private[code] def sameMethod(first: JavaMethod, second: JavaMethod): Boolean =
    first.className == second.className && first.name == second.name &&
      first.returnType == second.returnType && first.parameters == second.parameters

  private[code] def findSelection(classes: List[JavaClass], previous: JavaMethod): Option[JavaMethod] =
    classes.filter(_.name == previous.className) match
      case List(clazz) => clazz.methods.filter(sameMethod(_, previous)) match
        case List(function) => Some(function)
        case _ => None
      case _ => None

  /** Only whitespace and closed comments. */
  private def onlyTrivia(source: String): Boolean =
    SourceStructure.significant(SourceStructure.tokens(source, python = false)).isEmpty && SourceStructure.balanced(source)

  private[code] def lineSeparator(source: String): String =
    "\r\n|\r|\n".r.findFirstIn(source).getOrElse("\n")

  private[code] def methodDeclaration(returnType: String, name: String, parameters: String, isStatic: Boolean): String = {
    val modifiers = if isStatic then "public static" else "public"
    s"$modifiers $returnType $name($parameters) {\n    \n  }"
  }

  private[code] def insertMethod(source: String, clazz: JavaClass, declaration: String): String = {
    val beforeClosingBrace = source.substring(0, clazz.bodyEnd)
    val afterClosingBrace = source.substring(clazz.bodyEnd)
    val newline = lineSeparator(source)
    val inserted = declaration.replaceAll("\r\n|\r|\n", newline)
    beforeClosingBrace + s"$newline  $inserted$newline" + afterClosingBrace
  }
}
