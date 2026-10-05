package it.evadid.vm.parsing.java.turtle

import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.java.clean.model.JavaAST.*
import it.evadid.vm.parsing.java.clean.model.JavaType
import JavaTurtleSource.{Diagnostic, Problem, SourceRange}
import scala.collection.mutable.ArrayBuffer

object JavaTurtleInputLimits {
  val MaxSourceCharacters = 32768
  val MaxTokens = 8192
  val MaxTokenCharacters = 128
  val MaxNesting = 16
  val MaxSegmentTokens = 128
  val MaxIfStatements = 32
  val MaxAstNodes = 4096
  val MaxAstDepth = 64
  val MaxMethods = 128
  val MaxParameters = 16
  val MaxLocalsPerMethod = 128
  val MaxVariables = 1024
  val MaxTypeNesting = 16

  private def exceeded(message: String, range: Option[SourceRange] = None): Left[Diagnostic, Nothing] =
    Left(Diagnostic(Problem.InputLimit, message, range))

  private[turtle] def checkLength(source: String): Either[Diagnostic, Unit] =
    if source.length > MaxSourceCharacters then
      exceeded(s"Keep the turtle program within $MaxSourceCharacters characters.",
        Some(SourceRange(MaxSourceCharacters, source.length)))
    else Right(())

  private val operators = Vector(
    ">>>=", "<<=", ">>=", ">>>", "<<", ">>", "==", ">=", "<=", "!=", "&&", "||", "++", "--",
    "+=", "-=", "*=", "/=", "&=", "|=", "^=", "%=", "->",
    "=", ">", "<", "!", "~", "?", ":", "+", "-", "*", "/", "&", "|", "^", "%"
  )

  private def isNamePart(char: Char): Boolean =
    (char >= 'a' && char <= 'z') || (char >= 'A' && char <= 'Z') ||
      (char >= '0' && char <= '9') || char == '_' || char == '$'

  // Comments and literals have already been checked by JavaTurtleSource.
  private[turtle] def checkTokens(source: String): Either[Diagnostic, Unit] = {
    var offset = 0
    var tokens = 0
    var segmentTokens = 0
    var ifStatements = 0
    var delimiters = List.empty[Char]
    while offset < source.length do {
      val start = offset
      val char = source.charAt(offset)
      if " \t\r\n\f".contains(char) then offset += 1
      else if source.startsWith("//", offset) then {
        offset += 2
        while offset < source.length && source.charAt(offset) != '\n' && source.charAt(offset) != '\r' do offset += 1
      } else if source.startsWith("/*", offset) then {
        val end = source.indexOf("*/", offset + 2)
        if end < 0 then return Left(Diagnostic(Problem.UnclosedComment,
          "Close the block comment with */.", Some(SourceRange(start, source.length))))
        offset = end + 2
      }
      else {
        if isNamePart(char) then {
          offset += 1
          while offset < source.length && isNamePart(source.charAt(offset)) do offset += 1
        } else offset += operators.find(operator => source.startsWith(operator, offset)).fold(1)(_.length)
        val range = Some(SourceRange(start, offset))
        if offset - start > MaxTokenCharacters then
          return exceeded(s"Keep each name or number within $MaxTokenCharacters characters.", range)
        tokens += 1
        segmentTokens += 1
        if tokens > MaxTokens then return exceeded(s"Use at most $MaxTokens tokens in a turtle program.", range)
        if segmentTokens > MaxSegmentTokens then
          return exceeded(s"Split this section into smaller statements (at most $MaxSegmentTokens tokens).", range)

        if source.substring(start, offset) == "if" then {
          ifStatements += 1
          if ifStatements > MaxIfStatements then
            return exceeded(s"Use at most $MaxIfStatements if statements in a turtle program.", range)
        }
        if "([{".contains(char) then {
          delimiters = char :: delimiters
          if delimiters.size > MaxNesting then
            return exceeded(s"Use at most $MaxNesting nested delimiters.", range)
        } else if delimiters.headOption.exists(open =>
          (open == '(' && char == ')') || (open == '[' && char == ']') || (open == '{' && char == '}')) then
          delimiters = delimiters.tail

        // These boundaries end parser recursion; commas and comments do not.
        if char == ';' || char == '{' || char == '}' then segmentTokens = 0
      }
    }
    Right(())
  }

  private case class Entry(node: GenericAST, depth: Int, method: Int, parameter: Boolean = false)

  private[turtle] def checkAst(program: JavaProgram): Either[Diagnostic, Unit] = {
    var pending = List(Entry(program, 1, -1))
    val locals = ArrayBuffer.empty[Int]
    var nodes = 0
    var variables = 0
    while pending.nonEmpty do {
      val entry = pending.head
      pending = pending.tail
      nodes += 1
      if nodes > MaxAstNodes then return exceeded(s"Use at most $MaxAstNodes syntax nodes in a turtle program.")
      if entry.depth > MaxAstDepth then return exceeded(s"Use at most $MaxAstDepth nested syntax nodes.")
      entry.node match {
        case _: JavaUnparsableStatement =>
          return Left(Diagnostic(Problem.UnsupportedSyntax, "This Java construct is not supported yet.", None))
        case method: JavaMethodDef =>
          if locals.size >= MaxMethods then return exceeded(s"Use at most $MaxMethods methods in a turtle program.")
          if method.parameters.size > MaxParameters then return exceeded(s"Use at most $MaxParameters parameters per method.")
          if method.returnType.exists(typeTooDeep) then return typeLimit
          val owner = locals.size
          locals += 0
          pending = Entry(method.body, entry.depth + 1, owner) :: pending
          method.parameters.reverseIterator.foreach { parameter =>
            pending = Entry(parameter, entry.depth + 1, owner, parameter = true) :: pending
          }
        case node =>
          node match {
            case declaration: JavaVariableDeclaration =>
              variables += 1
              if variables > MaxVariables then return exceeded(s"Use at most $MaxVariables variable declarations in a turtle program.")
              if entry.method >= 0 && !entry.parameter then {
                locals(entry.method) += 1
                if locals(entry.method) > MaxLocalsPerMethod then
                  return exceeded(s"Use at most $MaxLocalsPerMethod local declarations per method.")
              }
              if typeTooDeep(declaration.javaType) then return typeLimit
            case clazz: JavaClassDef =>
              if clazz.extendsType.exists(typeTooDeep) || clazz.implementsTypes.exists(typeTooDeep) then return typeLimit
            case creation: JavaNewExpression => if typeTooDeep(creation.javaType) then return typeLimit
            case literal: JavaLiteral[?] => if typeTooDeep(literal.literalType) then return typeLimit
            case _ => ()
          }
          node.getChildren().reverseIterator.foreach { child =>
            pending = Entry(child, entry.depth + 1, entry.method) :: pending
          }
      }
    }
    Right(())
  }

  private def typeLimit: Left[Diagnostic, Nothing] =
    exceeded(s"Use at most $MaxTypeNesting nested array or list types.")

  private def typeTooDeep(javaType: JavaType[?]): Boolean = {
    var current: JavaType[?] = javaType
    var nesting = 0
    while nesting <= MaxTypeNesting do {
      current match {
        case array: JavaType.JAVA_ARRAY[?] => current = array.elementType
        case list: JavaType.JAVA_LIST[?] => current = list.elementType
        case _ => return false
      }
      nesting += 1
    }
    true
  }
}
