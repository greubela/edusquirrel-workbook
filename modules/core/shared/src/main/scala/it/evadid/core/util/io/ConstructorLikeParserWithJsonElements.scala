package it.evadid.core.util.io

import fastparse.*
import fastparse.MultiLineWhitespace.*
import it.evadid.distribution.command.SerializedException
import it.evadid.util.parsing.{Js, JsonGrammar}
import ujson.Value
import upickle.default.*

import scala.util.{Failure, Success, Try}

object ConstructorLikeParserWithJsonElements {

  case class ConstructorLikeReadResult(elementType: String, jsonPayloads: Seq[String]) derives ReadWriter {
    
    lazy val values: Map[String, ujson.Value] = {
      jsonPayloads.flatMap { curJsonStr =>
        ujson.read(curJsonStr).obj.map((key, value) => key -> value)
      }.toMap
    }
    
    def valueAsString(key: String): Option[String] = {
      values.get(key).map(write(_))
    }
  }

  private def jsonPayload(using P[?]): P[Js.Val] = JsonGrammar.jsonExpr

  private def identifier(using P[?]): P[Unit] =
    P(CharPred(c => c.isLetterOrDigit || c == '_').rep(1))

  private def getParser()(using P[?]): P[(String, Seq[String])] = {
    P(identifier.!) ~ (P("(") ~ jsonPayload.! ~ P(")")).rep(1)
  }


  def parseString(input: String): Try[ConstructorLikeReadResult] = {
    // FIX 1: Convert Context Function (?=>) to an explicit function parameter expected by parse
    parse(input, ctx => getParser()(using ctx)) match {
      // FIX 2: FastParse Success matches on (value, index). Unpack value explicitly to find the tuple.
      case Parsed.Success(resultTuple, index) => {
        val (constructorName: String, jsonPayloads: Seq[String]) = resultTuple
        Success(ConstructorLikeReadResult(constructorName, jsonPayloads))
      }
      case f: Parsed.Failure => {
        scala.util.Failure(SerializedException(f.longMsg))
      }
    }
  }
}
