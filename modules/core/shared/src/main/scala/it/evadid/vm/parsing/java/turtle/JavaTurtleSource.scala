package it.evadid.vm.parsing.java.turtle

import fastparse.Parsed
import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.java.clean.model.JavaAST.*

object JavaTurtleSource {
  enum Problem {
    case UnclosedComment, UnsupportedType, UnsupportedLiteral, UnsupportedNumber
    case UnicodeEscape, UnsupportedSyntax, ParseFailure
    case UnsupportedStructure, InvalidMain, MissingMain, DuplicateDeclaration, InvalidIdentifier
  }

  case class SourceRange(start: Int, end: Int)
  case class Diagnostic(problem: Problem, message: String, range: Option[SourceRange])

  final class ParsedSource private[JavaTurtleSource](val source: String, val program: JavaProgram)

  def parse(source: String): Either[Diagnostic, ParsedSource] =
    checkSource(source).flatMap(_ => parseAst(source))

  private def parseAst(source: String): Either[Diagnostic, ParsedSource] =
    fastparse.parse(source, context => JavaParser.javaProgram(using context)) match {
      case Parsed.Success(program, _) =>
        firstUnsupported(program) match {
          case Some(_) => Left(Diagnostic(Problem.UnsupportedSyntax, "This Java construct is not supported yet.", None))
          case None => Right(new ParsedSource(source, program))
        }
      case failure: Parsed.Failure =>
        Left(Diagnostic(
          Problem.ParseFailure,
          "This source could not be read as a supported Java program.",
          Some(SourceRange(failure.index, math.min(failure.index + 1, source.length)))
        ))
    }

  private val unsupportedTypes = Set("byte", "short", "long", "float", "double")

  private def isDigit(char: Char): Boolean = char >= '0' && char <= '9'
  private def isNameStart(char: Char): Boolean =
    (char >= 'a' && char <= 'z') || (char >= 'A' && char <= 'Z') || char == '_' || char == '$'
  private def isNamePart(char: Char): Boolean = isNameStart(char) || isDigit(char)

  private def checkSource(source: String): Either[Diagnostic, Unit] = {
    val unicodeEscape = source.indexOf("\\" + "u")
    if unicodeEscape >= 0 then
      return Left(Diagnostic(
        Problem.UnicodeEscape, "Unicode escapes are not supported yet.",
        Some(SourceRange(unicodeEscape, unicodeEscape + 2))
      ))

    var offset = 0
    while offset < source.length do {
      val start = offset
      val char = source.charAt(offset)
      if source.startsWith("//", offset) then {
        offset += 2
        while offset < source.length && source.charAt(offset) != '\n' && source.charAt(offset) != '\r' do offset += 1
      } else if source.startsWith("/*", offset) then {
        val end = source.indexOf("*/", offset + 2)
        if end < 0 then
          return Left(Diagnostic(
            Problem.UnclosedComment, "Close the block comment with */.", Some(SourceRange(start, source.length))
          ))
        offset = end + 2
      } else if char == '"' || char == '\'' then {
        return Left(Diagnostic(
          Problem.UnsupportedLiteral, "String and character literals are not supported yet.", Some(SourceRange(start, start + 1))
        ))
      } else if isDigit(char) || (char == '.' && offset + 1 < source.length && isDigit(source.charAt(offset + 1))) then {
        offset += 1
        while offset < source.length && (isNamePart(source.charAt(offset)) || source.charAt(offset) == '.') do offset += 1
        val number = source.substring(start, offset)
        if !number.forall(isDigit) || (number.length > 1 && number.head == '0') then
          return Left(Diagnostic(
            Problem.UnsupportedNumber, "Use decimal integers without leading zeros.", Some(SourceRange(start, offset))
          ))
      } else if isNameStart(char) then {
        offset += 1
        while offset < source.length && isNamePart(source.charAt(offset)) do offset += 1
        if unsupportedTypes.contains(source.substring(start, offset)) then
          return Left(Diagnostic(
            Problem.UnsupportedType, "Use int or boolean for turtle programs.", Some(SourceRange(start, offset))
          ))
      } else offset += 1
    }
    Right(())
  }

  private def firstUnsupported(node: GenericAST): Option[JavaUnparsableStatement] =
    node match {
      case unsupported: JavaUnparsableStatement => Some(unsupported)
      case _ => node.getChildren().iterator.map(firstUnsupported).collectFirst { case Some(unsupported) => unsupported }
    }
}
