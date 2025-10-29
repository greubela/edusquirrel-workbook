package contentmanagement.model.vm.parsing.python

import contentmanagement.model.language.AppLanguage.English
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.controlStructures.*
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.code.errors.*
import contentmanagement.model.vm.code.others.*
import contentmanagement.model.vm.code.usage.*
import contentmanagement.model.vm.types.BeDataType
import fastparse.*
import fastparse.NoWhitespace.*
import sourcecode.Text.generate

import scala.collection.mutable
import scala.scalajs.js.internal.UnitOps.unitOrOps

object PythonParser {

  private val definedStructures: mutable.ListBuffer[BeDefineStructure] = mutable.ListBuffer()
  private val knownFunctions: mutable.Map[String, BeDefineFunction] = mutable.Map()
  private val knownVariables: mutable.Map[String, BeDefineVariable] = mutable.Map()

  def parsePython(source: String): BeExpression = {
    definedStructures.clear()
    knownFunctions.clear()
    knownVariables.clear()
    if (source.trim.isEmpty) {
      BeSequence(false, List())
    } else {
      parse(source, statements(_)) match {
        case Parsed.Success(stmts, _) =>
          BeSequence(false, stmts.toList)
        case failure: Parsed.Failure =>
          BeExpressionUnparsable(source, failure.trace().longAggregateMsg)
      }
    }
  }

  private def statements[$: P]: P[Seq[BeExpression]] =
    P(stmtSep.rep ~ statement.rep(sep = stmtSep) ~ stmtSep.rep)

  private def statement[$: P]: P[BeExpression] =
    P(classStatement |
      functionDefinition |
      ifStatement |
      whileStatement |
      forStatement |
      tryStatement |
      returnStatement |
      raiseStatement |
      passStatement |
      assignmentStatement |
      expressionStatement)

  private def passStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "pass" ~ ws.?).map(_ => BeExpression.pass)

  private def ifStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "if" ~ ws.? ~ conditionExpr ~ ":" ~ lineSep ~ blockBody ~ elifClause.rep ~ elseClause.?).map {
      case (condExpr, thenBodyText, elifParts, elsePart) =>
        val baseElse = elsePart.map(parseBlockExpressions).getOrElse(BeSequence.optionalUnitBody(List()))
        val base = BeIfElse(condExpr, parseBlockExpressions(thenBodyText), baseElse)
        elifParts.foldRight(base) { case ((elifCondText, elifBodyText), acc) =>
          val elifCondition = parseInlineExpression(elifCondText)
          val elifBody = parseBlockExpressions(elifBodyText)
          BeIfElse(elifCondition, elifBody, BeSequence(false, List(acc)))
        }
    }

  private def whileStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "while" ~ ws.? ~ conditionExpr ~ ":" ~ lineSep ~ blockBody).map { case (condExpr, bodyText) =>
      BeWhile(condExpr, parseBlockExpressions(bodyText))
    }

  private def forStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "for" ~ ws.? ~ identifier.! ~ ws.? ~ "in" ~ ws.? ~ CharsWhile(c => c != ':' && c != '\n' && c != '\r', 1).! ~ ws.? ~ ":" ~ lineSep ~ blockBody).map {
      case (name, iterableText, bodyText) =>
        val loopVariable = resolveVariable(name)
        val iterableExpr = parseInlineExpression(iterableText)
        val bodySequence = parseBlockExpressions(bodyText)
        iterableExpr match {
          case call: BeFunctionCall if name == "_" && isSimpleRangeCall(call) =>
            BeRepeatNr(extractRangeCount(call), bodySequence)
          case _ =>
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
    P(ws.? ~ identifier.! ~ ws.? ~ "=" ~ !"=" ~ ws.? ~ CharsWhile(isLineChar, 1).!).map {
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
    P(ws.? ~ "class" ~ ws.? ~ identifier.! ~ ws.? ~ ":" ~ lineSep ~ blockBody).map { case (name, bodyText) =>
      val bodySequence = parseBlockExpressions(bodyText)
      val members = bodySequence.body
      val attributes = members.collect { case variable: BeDefineVariable => variable }
      val methods = members.collect { case func: BeDefineFunction => func }
      val nameMap: LanguageMap[HumanLanguage] = LanguageMap.universalMap(name)
      val placeholderClass = BeDefineClass(nameMap, attributes, List())
      val methodsWithClass = methods.map(method => method.copy(functionTypeInfo = method.functionTypeInfo.copy(isMethodInClass = Some(placeholderClass))))
      val finalClass = placeholderClass.copy(methods = methodsWithClass)
      definedStructures += finalClass
      finalClass
    }

  private def functionDefinition[$: P]: P[BeExpression] =
    P(ws.? ~ "def" ~ ws.? ~ identifier.! ~ ws.? ~ "(" ~ parameterList ~ ")" ~ returnAnnotation.? ~ ws.? ~ ":" ~ lineSep ~ blockBody).map {
      case (name, params, returnTypeOpt, bodyText) =>
        val paramDefinitions = params.map { case (paramName, typeHint) =>
          BeDefineVariable(LanguageMap.universalMap(paramName), mapType(typeHint))
        }
        val returnVariable = returnTypeOpt.flatMap { returnStr =>
          val mapped = mapType(Some(returnStr))
          if (mapped.contains(BeDataType.Unit) && mapped.size == 1) None
          else Some(BeDefineVariable(LanguageMap.universalMap("return"), mapped))
        }
        val bodyExpr = parseBlockExpressions(bodyText)
        val functionInfo = BeDefineFunction.functionInfo(LanguageMap.universalMap(name))
        val functionDef = BeDefineFunction(paramDefinitions, returnVariable, bodyExpr, functionInfo)
        knownFunctions.update(name, functionDef)
        definedStructures += functionDef
        functionDef
    }

  private def parameterList[$: P]: P[List[(String, Option[String])]] =
    P(parameter.rep(sep = ws.? ~ "," ~ ws.?)).map(_.toList)

  private def parameter[$: P]: P[(String, Option[String])] =
    P(identifier.! ~ (ws.? ~ ":" ~ ws.? ~ CharsWhile(c => c != ',' && c != ')' && c != '\n' && c != '\r').!).?).map {
      case (name, typeHint) => (name, typeHint.map(_.trim).filter(_.nonEmpty))
    }

  private def returnAnnotation[$: P]: P[String] =
    P(ws.? ~ "->" ~ ws.? ~ CharsWhile(c => c != ':' && c != '\n' && c != '\r', 1).!).map(_.trim)

  private def expressionStatement[$: P]: P[BeExpression] =
    P(ws.? ~ (functionCall | valueLiteralExpression | unsupportedExpression) ~ ws.?)

  private def functionCall[$: P]: P[BeExpression] =
    P(identifier.! ~ ws.? ~ "(" ~ ws.? ~ argumentList ~ ws.? ~ ")").map { case (name, args) =>
      val function = resolveFunction(name, args.length)
      BeFunctionCall(function, args)
    }

  private def argumentList[$: P]: P[List[BeUseValue]] =
    P(valueExpression.rep(sep = ws.? ~ "," ~ ws.?)).map(_.toList)

  private def valueLiteralExpression[$: P]: P[BeExpression] =
    P(valueExpression)

  private def unsupportedExpression[$: P]: P[BeExpression] =
    P(CharsWhile(isLineChar, 1).!).map(str => BeExpressionUnsupported(str.trim))

  private def valueExpression[$: P]: P[BeUseValue] =
    P(unitLiteral | booleanLiteral | numberLiteral | stringLiteral | identifierValue)

  private def identifierValue[$: P]: P[BeUseValue] =
    P(identifier.!).map(name => BeUseValueReferencing(resolveVariable(name)))

  private def unitLiteral[$: P]: P[BeUseValue] =
    P("None").map(_ => BeUseUnitValue)

  private def booleanLiteral[$: P]: P[BeUseValue] =
    P("True".map(_ => BeUseValueLiteral("True")) | "False".map(_ => BeUseValueLiteral("False")))

  private def numberLiteral[$: P]: P[BeUseValue] =
    P(integerLiteral.!).map(num => BeUseValueLiteral(num))

  private def stringLiteral[$: P]: P[BeUseValue] =
    P(
      ("\"" ~ CharsWhile(_ != '"').! ~ "\"").map(content => BeUseValueLiteral(s"\"$content\"")) |
        ("'"  ~ CharsWhile(_ != '\'').! ~ "'").map(content  => BeUseValueLiteral(s"'$content'"))
    )

  private def integerLiteral[$: P]: P[String] =
    P(CharIn("0-9").rep(1).!)

  private def identifier[$: P]: P[Unit] =
    P(CharIn("a-zA-Z_") ~ CharIn("a-zA-Z0-9_").rep)

  private def conditionExpr[$: P]: P[BeExpression] =
    P(CharsWhile(c => c != ':' && c != '\n' && c != '\r', 1).!).map(parseInlineExpression)

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
    P("    " ~ CharsWhile(isLineChar).!).map(_.trim)

  private def stmtSep[$: P]: P[Unit] =
    P((ws.? ~ lineSep).rep(1))

  private def lineSep[$: P]: P[Unit] = P("\r\n" | "\n")

  private def ws[$: P]: P[Unit] = P(CharIn(" \t").rep)

  private def isLineChar(c: Char): Boolean = c != '\n' && c != '\r'

  private def parseBlockExpressions(body: String): BeSequence = {
    if (body.trim.isEmpty) {
      BeSequence(false, List())
    } else {
      parse(body, statements(_)) match {
        case Parsed.Success(stmts, _) => BeSequence(false, stmts.toList)
        case failure: Parsed.Failure => BeSequence(true, List(BeExpressionUnparsable(body, failure.trace().longAggregateMsg)))
      }
    }
  }

  private def parseInlineExpression(expr: String): BeExpression = {
    val trimmed = expr.trim
    if (trimmed.isEmpty) BeUseUnitValue
    else {
      parse(trimmed, inlineExpression(_)) match {
        case Parsed.Success(result, _) => result
        case _ => BeExpressionUnsupported(trimmed)
      }
    }
  }

  private def inlineExpression[$: P]: P[BeExpression] =
    P(functionCall | valueLiteralExpression | unsupportedExpression)

  private def mapType(typeHint: Option[String]): Set[BeDataType] = {
    typeHint.map(_.trim.toLowerCase) match {
      case Some("int") | Some("float") | Some("number") | Some("double") => Set(BeDataType.Numeric)
      case Some("bool") | Some("boolean") => Set(BeDataType.Boolean)
      case Some("str") | Some("string") => Set(BeDataType.String)
      case Some("date") | Some("datetime") => Set(BeDataType.Date)
      case Some("none") | Some("void") | Some("unit") => Set(BeDataType.Unit)
      case Some(_) => BeDataType.AnyType
      case None => BeDataType.AnyType
    }
  }

  private def isSimpleRangeCall(call: BeFunctionCall): Boolean = {
    val functionName = call.funcDef.functionTypeInfo.displayName.getInLanguage(English)
    functionName == "range" && call.withParameterValues.length == 1 && call.withParameterValues.headOption.exists {
      case literal: BeUseValueLiteral => literal.value.trim.toIntOption.nonEmpty
      case _ => false
    }
  }

  private def extractRangeCount(call: BeFunctionCall): Int =
    call.withParameterValues.headOption.collect {
      case literal: BeUseValueLiteral => literal.value.trim.toIntOption.getOrElse(0)
    }.getOrElse(0)

  private def resolveFunction(name: String, arity: Int): BeDefineFunction = {
    knownFunctions.getOrElse(name, {
      val parameters = (0 until arity).map(index => BeDefineVariable(LanguageMap.universalMap(s"arg$index"), BeDataType.AnyType)).toList
      val placeholder = BeDefineFunction(parameters, None, BeSequence.optionalUnitBody(List()), BeDefineFunction.functionInfo(LanguageMap.universalMap(name)))
      knownFunctions.update(name, placeholder)
      placeholder
    })
  }

  private def resolveVariable(name: String): BeDefineVariable = {
    knownVariables.getOrElseUpdate(name, BeDefineVariable(LanguageMap.universalMap(name), BeDataType.AnyType))
  }

}
