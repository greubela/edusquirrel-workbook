package contentmanagement.model.vm.parsing.python

import contentmanagement.model.vm.expressions.*
import contentmanagement.model.vm.types.BeDataType
import fastparse.*
import fastparse.NoWhitespace.*

object PythonParser {

  def parsePython(source: String): BeExpression = {
    if (source.trim.isEmpty) {
      BeSequence(Nil, mayBeEmpty = true, Some(Set(BeDataType.Unit)))
    } else {
      parse(source, statements(_)) match {
        case Parsed.Success(stmts, _) =>
          BeSequence(stmts.toList, mayBeEmpty = true, Some(Set(BeDataType.Unit)))
        case failure: Parsed.Failure =>
          BeExpressionSyntaxError(source, failure.trace().longAggregateMsg)
      }
    }
  }

  private def statements[$: P]: P[Seq[BeExpression]] =
    P(stmtSep.rep ~ statement.rep(sep = stmtSep) ~ stmtSep.rep)

  private def statement[$: P]: P[BeExpression] =
    P(ifStatement | simpleStatement)

  private def ifStatement[$: P]: P[BeExpression] =
    P("if" ~ ws1 ~ condition ~ ":" ~ lineSep ~ blockBody ~ elseClause.?).map {
      case (cond, ifBodyText, elseBodyText) =>
        val ifExpressions = parseBlockExpressions(ifBodyText)
        val elseExpressions = elseBodyText.map(parseBlockExpressions).getOrElse(Nil)
        BeExpressionIfElse(cond, ifExpressions, elseExpressions)
    }

  private def simpleStatement[$: P]: P[BeExpression] =
    P(ws.? ~ CharsWhile(isLineChar, 1).!).map { rawLine =>
      BeExpressionUnkown(rawLine.trim)
    }

  private def condition[$: P]: P[String] =
    P(CharsWhile(c => c != ':' && c != '\n' && c != '\r', 1).!).map(_.trim)

  private def blockBody[$: P]: P[String] =
    P(blockLine.rep(1, sep = lineSep) ~ lineSep.?).map { case (lines, trailing) =>
      val builder = new StringBuilder
      lines.foreach { line =>
        if (builder.nonEmpty) builder.append('\n')
        builder.append(line)
      }
      trailing.foreach(_ => ())
      builder.toString()
    }

  private def elseClause[$: P]: P[Option[String]] =
    P(stmtSep.rep ~ "else" ~ ws.? ~ ":" ~ lineSep ~ blockBody).map(body => Some(body))

  private def blockLine[$: P]: P[String] =
    P("    " ~ CharsWhile(isLineChar).!).map(_.trim)

  private def stmtSep[$: P]: P[Unit] =
    P((ws.? ~ lineSep).rep(1))

  private def lineSep[$: P]: P[Unit] = P("\r\n" | "\n")

  private def ws[$: P]: P[Unit] = P(CharIn(" \t").rep)

  private def ws1[$: P]: P[Unit] = P(CharIn(" \t").rep(1))

  private def isLineChar(c: Char): Boolean = c != '\n' && c != '\r'

  private def parseBlockExpressions(body: String): List[BeExpression] = {
    if (body.trim.isEmpty) {
      Nil
    } else {
      parse(body, statements(_)) match {
        case Parsed.Success(stmts, _) => stmts.toList
        case failure: Parsed.Failure =>
          List(BeExpressionSyntaxError(body, failure.trace().longAggregateMsg))
      }
    }
  }
}
