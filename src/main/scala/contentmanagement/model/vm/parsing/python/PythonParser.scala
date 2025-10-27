package contentmanagement.model.vm.parsing.python

import contentmanagement.model.vm.expressions.*
import contentmanagement.model.vm.expressions.controlStructures.BeExpressionIfElse

import fastparse.*
import fastparse.NoWhitespace.*
import scala.scalajs.js.internal.UnitOps.unitOrOps

object PythonParser {


  def parsePython(source: String): BeExpression = {
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
    P(ifStatement | simpleStatement)

  private def ifStatement[$: P]: P[BeExpression] =
    P(ws.? ~ "if" ~ ws.? ~ condition ~ ":" ~ lineSep ~ blockBody ~ elseClause).map {
      case (cond, ifBodyText, elseBodyText) =>
        val conditionExpr = BeExpressionUnsupported(cond)
        val thenBodyExpr = parseBlockExpressions(ifBodyText)
        val elseBodyExpr = elseBodyText.map(parseBlockExpressions).getOrElse(BeSequence(false, List()))
        BeExpressionIfElse(conditionExpr, thenBodyExpr, elseBodyExpr)
    }

  private def simpleStatement[$: P]: P[BeExpression] =
    P(ws.? ~ CharsWhile(isLineChar, 1).!).map { rawLine =>
      BeExpressionUnsupported(rawLine.trim)
    }

  private def condition[$: P]: P[String] =
    P(CharsWhile(c => c != ':' && c != '\n' && c != '\r', 1).!).map(_.trim)

  private def blockBody[$: P]: P[String] =
    P(blockLine.rep(1, sep = lineSep)).map { lines =>
      val builder = new StringBuilder
      lines.foreach { line =>
        if (builder.nonEmpty) builder.append('\n')
        builder.append(line)
      }
      builder.toString()
    }

  private def elseClause[$: P]: P[Option[String]] =
    P((stmtSep.rep(1) ~ "else" ~ ws.? ~ ":" ~ lineSep ~ blockBody).map(body => Some(body)) | Pass.map(_ => None))

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


}
