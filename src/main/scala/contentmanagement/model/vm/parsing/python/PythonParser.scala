package contentmanagement.model.vm.parsing.python

import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.controlStructures.*
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.code.errors.*
import contentmanagement.model.vm.code.others.*
import contentmanagement.model.vm.code.usage.*
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeDataType.{AnyType, BeUnionAllowedTypes}
import fastparse.*
import fastparse.NoWhitespace.*

import scala.collection.mutable

object PythonParser {

  private val definedStructures: mutable.ListBuffer[BeDefineStructure] = mutable.ListBuffer()
  private val knownFunctions: mutable.Map[String, BeDefineFunction] = mutable.Map()
  private val knownVariables: mutable.Map[String, BeDefineVariable] = mutable.Map()
  private val reservedKeywords: Set[String] = Set(
    "False",
    "None",
    "True",
    "and",
    "as",
    "assert",
    "async",
    "await",
    "break",
    "case",
    "class",
    "continue",
    "def",
    "del",
    "elif",
    "else",
    "except",
    "finally",
    "for",
    "from",
    "global",
    "if",
    "import",
    "in",
    "is",
    "lambda",
    "match",
    "nonlocal",
    "not",
    "or",
    "pass",
    "raise",
    "return",
    "try",
    "while",
    "with",
    "yield"
  )

  def parsePython(source: String): BeExpression = {
    definedStructures.clear()
    knownFunctions.clear()
    knownVariables.clear()

    if (source.trim.isEmpty) {
      BeSequence.optionalBody(List.empty)
    } else {
      parse(source, statements(_)) match {
        case Parsed.Success(stmts, _) => BeSequence.optionalBody(stmts.toList)
        case failure: Parsed.Failure   => BeExpressionUnparsable(source, failure.trace().longAggregateMsg)
      }
    }
  }

  private def statements[$: P]: P[Seq[BeExpression]] =
    P(stmtSep.rep ~ statement.rep(sep = stmtSep) ~ stmtSep.rep)

  private def statement[$: P]: P[BeExpression] =
    P(
      classStatement |
        functionDefinition |
        ifStatement |
        whileStatement |
        forStatement |
        tryStatement |
        returnStatement |
        raiseStatement |
        passStatement |
        assignmentStatement |
        expressionStatement
    )

  private def passStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "pass" ~ ws.?).map(_ => BeExpression.pass)

  private def ifStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "if" ~ ws.? ~ conditionExpr ~ ":" ~ lineSep ~ blockBody ~ elifClause.rep ~ elseClause.?).map {
      case (condExpr, thenBodyText, elifParts, elsePart) =>
        val elseSequence = elsePart.map(parseBlockExpressions).getOrElse(emptyOptionalSequence)
        val initial = BeIfElse(condExpr, parseBlockExpressions(thenBodyText), elseSequence)
        elifParts.foldRight(initial) { case ((elifCondText, elifBodyText), acc) =>
          val elifCondition = parseConditionSequence(elifCondText)
          val elifBody = parseBlockExpressions(elifBodyText)
          BeIfElse(elifCondition, elifBody, wrapExpression(acc))
        }
    }

  private def whileStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "while" ~ ws.? ~ conditionExpr ~ ":" ~ lineSep ~ blockBody).map { case (condExpr, bodyText) =>
      BeWhile(condExpr, parseBlockExpressions(bodyText))
    }

  private def forStatement[$: P]: P[BeExpression] =
    P(
      ws.? ~ "for" ~ ws.? ~ identifier ~ ws.? ~ "in" ~ ws.? ~
        CharsWhile(c => c != ':' && c != '\n' && c != '\r', 1).! ~ ws.? ~ ":" ~ lineSep ~ blockBody
    ).map {
      case (name, iterableText, bodyText) =>
        val loopVariable = resolveVariable(name)
        val bodySequence = parseBlockExpressions(bodyText)
        parseRangeInvocation(iterableText.trim) match {
          case Some(count) if name == "_" => BeRepeatNr(count, bodySequence)
          case _ =>
            val iterableExpr = parseInlineExpression(iterableText)
            BeForEach(loopVariable, iterableExpr, bodySequence)
        }
    }

  private def returnStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "return" ~ ws.? ~ CharsWhile(isLineChar).!.?).map { exprOpt =>
      val trimmed = exprOpt.map(_.trim).filter(_.nonEmpty)
      BeReturn(trimmed.map(parseInlineExpression))
    }

  private def raiseStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "raise" ~ ws.? ~ CharsWhile(isLineChar).!.?).map { exprOpt =>
      val trimmed = exprOpt.map(_.trim).filter(_.nonEmpty)
      BeExpressionThrowError(trimmed.map(parseInlineExpression))
    }

  private def assignmentStatement[$: P]: P[BeExpression] =
    P(ws.? ~ identifier ~ ws.? ~ "=" ~ !"=" ~ ws.? ~ CharsWhile(isLineChar, 1).!).map {
      case (name, valueText) =>
        val variable = resolveVariable(name)
        val valueExpr = parseInlineExpression(valueText.trim)
        BeAssignVariable(variable, valueExpr)
    }

  private def tryStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "try" ~ ws.? ~ ":" ~ lineSep ~ blockBody ~ exceptClause.rep(1) ~ finallyClause.?).map {
      case (tryBodyText, excepts, finallyText) =>
        val tryBody = parseBlockExpressions(tryBodyText)
        val exceptBlocks = excepts.toList.map { case (condOpt, bodyText) =>
          val condExpr = condOpt.map(parseInlineExpression)
          BeTryExcept.ExceptBlock(condExpr, parseBlockExpressions(bodyText))
        }
        val finallyBody = finallyText.map(parseBlockExpressions)
        BeTryExcept(tryBody, exceptBlocks, finallyBody)
    }

  private def classStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "class" ~ ws.? ~ identifier ~ ws.? ~ ":" ~ lineSep ~ blockBody).map { case (name, bodyText) =>
      val bodySequence = parseBlockExpressions(bodyText)
      val members = bodySequence.body
      val attributes = members.collect { case variable: BeDefineVariable => variable }
      val methods = members.collect { case func: BeDefineFunction => func }
      val nameMap: LanguageMap[HumanLanguage] = LanguageMap.universalMap(name)
      val placeholderClass = BeDefineClass(nameMap, attributes, List())
      val methodsWithClass = methods.map(method =>
        method.copy(functionTypeInfo = method.functionTypeInfo.copy(isMethodInClass = Some(placeholderClass)))
      )
      val finalClass = placeholderClass.copy(methods = methodsWithClass)
      definedStructures += finalClass
      finalClass
    }

  private def functionDefinition[$: P]: P[BeExpression] =
    P(
      ws.? ~ "def" ~ ws.? ~ identifier ~ ws.? ~ "(" ~ parameterList ~ ")" ~ returnAnnotation.? ~ ws.? ~ ":" ~ lineSep ~ blockBody
    ).map {
      case (name, params, returnTypeOpt, bodyText) =>
        val parametersWithNames = params.map { case (paramName, typeHint) =>
          paramName -> BeDefineVariable(LanguageMap.universalMap(paramName), mapType(typeHint))
        }
        val paramDefinitions = parametersWithNames.map(_._2)
        val previousVariables = parametersWithNames.map { case (paramName, definition) =>
          val previous = knownVariables.get(paramName)
          knownVariables.update(paramName, definition)
          paramName -> previous
        }
        val returnVariable = returnTypeOpt.flatMap { returnStr =>
          val mapped: BeDataType = mapType(Some(returnStr))
          if (mapped == BeDataType.Unit) None
          else Some(BeDefineVariable(LanguageMap.universalMap("return"), mapped))
        }
        val bodyExpr =
          try parseBlockExpressions(bodyText)
          finally {
            previousVariables.foreach { case (paramName, previousOpt) =>
              previousOpt match {
                case Some(previous) => knownVariables.update(paramName, previous)
                case None => knownVariables.remove(paramName)
              }
            }
          }
        val functionInfo = BeDefineFunction.functionInfo(LanguageMap.universalMap(name))
        val functionDef = BeDefineFunction(paramDefinitions, returnVariable, bodyExpr, functionInfo)
        knownFunctions.update(name, functionDef)
        definedStructures += functionDef
        functionDef
    }

  private def parameterList[$: P]: P[List[(String, Option[String])]] =
    P(parameter.rep(sep = ws.? ~ "," ~ ws.?)).map(_.toList)

  private def parameter[$: P]: P[(String, Option[String])] =
    P(identifier ~ (ws.? ~ ":" ~ ws.? ~ CharsWhile(c => c != ',' && c != ')' && c != '\n' && c != '\r').!).?).map {
      case (name, typeHint) => (name, typeHint.map(_.trim).filter(_.nonEmpty))
    }

  private def returnAnnotation[$: P]: P[String] =
    P(ws.? ~ "->" ~ ws.? ~ CharsWhile(c => c != ':' && c != '\n' && c != '\r', 1).!).map(_.trim)

  private def expressionStatement[$: P]: P[BeExpression] =
    P(ws.!.? ~ (functionCall | valueLiteralExpression | unsupportedExpression) ~ ws.?).map {
      case (indentOpt, expr) =>
        indentOpt.filter(_.nonEmpty) match {
          case Some(indent) =>
            expr match {
              case unsupported: BeExpressionUnsupported if !unsupported.originalSource.startsWith(indent) =>
                unsupported.copy(originalSource = indent + unsupported.originalSource)
              case _ => expr
            }
          case None => expr
        }
    }

  private def functionCall[$: P]: P[BeExpression] =
    P(identifier ~ ws.? ~ "(" ~ ws.? ~ argumentList ~ ws.? ~ ")").map { case (name, args) =>
      val function = resolveFunction(name, args.length)
      val parameterMap = function.inputs.zip(args).toMap
      BeFunctionCall(function, parameterMap)
    }

  private def argumentList[$: P]: P[List[BeExpression]] =
    P(inlineExpression.rep(sep = ws.? ~ "," ~ ws.?)).map(_.toList)

  private def valueLiteralExpression[$: P]: P[BeExpression] =
    P(valueExpression)

  private def unsupportedExpression[$: P]: P[BeExpression] =
    P(CharsWhile(isLineChar, 1).!).map(str => BeExpressionUnsupported(str.trim))

  private def valueExpression[$: P]: P[BeExpression] =
    P(unitLiteral | booleanLiteral | numberLiteral | stringLiteral | identifierValue)

  private def identifierValue[$: P]: P[BeExpression] =
    P(identifier).map { name =>
      val variable = resolveVariable(name)
      BeUseValue(BeUseValueReferencing(variable), Some(variable))
    }

  private def unitLiteral[$: P]: P[BeExpression] =
    P("None").map(_ => BeUseValue(BeDataValueUnit(), None))

  private def booleanLiteral[$: P]: P[BeExpression] =
    P("True".!.map(literal => BeUseValue(BeDataValueLiteral(literal), None)) |
      "False".!.map(literal => BeUseValue(BeDataValueLiteral(literal), None)))

  private def numberLiteral[$: P]: P[BeExpression] =
    P(integerLiteral.!).map(num => BeUseValue(BeDataValueLiteral(num), None))

  private def stringLiteral[$: P]: P[BeExpression] =
    P(
      ("\"" ~ CharsWhile(_ != '"').! ~ "\"").map(content => BeUseValue(BeDataValueLiteral(s"\"$content\""), None)) |
        ("'" ~ CharsWhile(_ != '\'').! ~ "'").map(content => BeUseValue(BeDataValueLiteral(s"'$content'"), None))
    )

  private def integerLiteral[$: P]: P[String] =
    P(CharIn("0-9").rep(1).!)

  private def identifier[$: P]: P[String] =
    P(CharIn("a-zA-Z_") ~ CharIn("a-zA-Z0-9_").rep).!.filter(name => !reservedKeywords.contains(name))

  private def stripTrailingWhitespace(value: String): String =
    value.reverse.dropWhile(_.isWhitespace).reverse

  private def conditionExpr[$: P]: P[BeSequence] =
    P(CharsWhile(c => c != ':' && c != '\n' && c != '\r', 1).!).map(parseConditionSequence)

  private def elifClause[$: P]: P[(String, String)] =
    P(stmtSep.rep(1) ~ "elif" ~ ws.? ~ CharsWhile(c => c != ':' && c != '\n' && c != '\r', 1).! ~ ":" ~ lineSep ~ blockBody)

  private def elseClause[$: P]: P[String] =
    P(stmtSep.rep(1) ~ "else" ~ ws.? ~ ":" ~ lineSep ~ blockBody)

  private def exceptClause[$: P]: P[(Option[String], String)] =
    P(stmtSep.rep(1) ~ "except" ~ ws.? ~ CharsWhile(c => c != ':' && c != '\n' && c != '\r').?.! ~ ":" ~ lineSep ~ blockBody).map {
      case (cond, body) => (Option(cond).map(_.trim).filter(_.nonEmpty), body)
    }

  private def finallyClause[$: P]: P[String] =
    P(stmtSep.rep(1) ~ "finally" ~ ws.? ~ ":" ~ lineSep ~ blockBody)

  private def blockBody[$: P]: P[String] =
    P(blockLine.rep(1, sep = lineSep)).map { lines =>
      val builder = new StringBuilder
      lines.foreach { line =>
        if (builder.nonEmpty) builder.append('\n')
        builder.append(line)
      }
      builder.toString()
    }

  private def blockLine[$: P]: P[String] =
    P("    " ~ CharsWhile(isLineChar, 0).!).map(stripTrailingWhitespace)

  private def stmtSep[$: P]: P[Unit] =
    P((ws.? ~ lineSep).rep(1))

  private def lineSep[$: P]: P[Unit] = P("\r\n" | "\n")

  private def ws[$: P]: P[Unit] = P(CharIn(" \t").rep)

  private def isLineChar(c: Char): Boolean = c != '\n' && c != '\r'

  private def parseBlockExpressions(body: String): BeSequence = {
    if (body.trim.isEmpty) {
      emptyOptionalSequence
    } else {
      parse(body, statements(_)) match {
        case Parsed.Success(stmts, _) => BeSequence.optionalBody(stmts.toList)
        case failure: Parsed.Failure =>
          BeSequence.optionalBody(List(BeExpressionUnparsable(body, failure.trace().longAggregateMsg)))
      }
    }
  }

  private def parseInlineExpression(expr: String): BeExpression = {
    val trimmed = expr.trim
    if (trimmed.isEmpty) {
      BeExpressionUnsupported("")
    } else {
      parse(trimmed, inlineExpression(_)) match {
        case Parsed.Success(result, _) => result
        case _                         => BeExpressionUnsupported(trimmed)
      }
    }
  }

  private def inlineExpression[$: P]: P[BeExpression] =
    P(functionCall | valueLiteralExpression | unsupportedExpression)

  private def mapType(typeHint: Option[String]): BeDataType = {
    typeHint match {
      case Some(rawHint) if rawHint.nonEmpty =>
        val normalizedParts = rawHint.split("\\|").map(_.trim).filter(_.nonEmpty)
        val mappedParts = normalizedParts.flatMap(mapAtomicType)
        if (mappedParts.isEmpty) AnyType
        else if (mappedParts.length == 1) mappedParts.head
        else BeUnionAllowedTypes(mappedParts.toSet)
      case _ => AnyType
    }
  }

  private def mapAtomicType(typeHint: String): Option[BeDataType] =
    typeHint.toLowerCase match {
      case "int" | "float" | "number" | "double" => Some(BeDataType.Numeric)
      case "bool" | "boolean"                     => Some(BeDataType.Boolean)
      case "str" | "string"                        => Some(BeDataType.String)
      case "date" | "datetime"                    => Some(BeDataType.Date)
      case "none" | "void" | "unit"              => Some(BeDataType.Unit)
      case _                                       => None
    }

  private def parseRangeInvocation(source: String): Option[Int] = {
    val trimmed = source.trim
    if (trimmed.startsWith("range(") && trimmed.endsWith(")")) {
      val inner = trimmed.substring(6, trimmed.length - 1).trim
      if (inner.nonEmpty) inner.toIntOption else None
    } else {
      None
    }
  }

  private def resolveFunction(name: String, arity: Int): BeDefineFunction = {
    knownFunctions.getOrElse(name, {
      val parameters = (0 until arity).map { index =>
        BeDefineVariable(LanguageMap.universalMap(s"arg$index"), AnyType)
      }.toList
      val placeholder = BeDefineFunction(
        parameters,
        None,
        BeSequence.optionalBody(List.empty),
        BeDefineFunction.functionInfo(LanguageMap.universalMap(name))
      )
      knownFunctions.update(name, placeholder)
      placeholder
    })
  }

  private def resolveVariable(name: String): BeDefineVariable = {
    knownVariables.getOrElseUpdate(name, BeDefineVariable(LanguageMap.universalMap(name), AnyType))
  }

  private def parseConditionSequence(conditionSource: String): BeSequence = {
    val expression = parseInlineExpression(conditionSource)
    BeSequence(List(expression), BeSequenceInfo(Some(BeDataType.Boolean), Some(1)))
  }

  private def wrapExpression(expr: BeExpression): BeSequence =
    BeSequence.optionalBody(List(expr))

  private def emptyOptionalSequence: BeSequence =
    BeSequence.optionalBody(List.empty)
}
