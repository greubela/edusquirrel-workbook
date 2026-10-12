package it.evadid.core.util.io

import fastparse.*
import fastparse.MultiLineWhitespace.*
import it.evadid.distribution.command.SerializedException
import it.evadid.util.parsing.{Js, JsonGrammar}
import upickle.default.*

import scala.util.{Success, Try}

object ConstructorLikeParserWithJsonElements {

  case class ConstructorLikeReadResult(elementType: String, jsonPayloads: Seq[String]) derives ReadWriter {
    
    /** Positional arguments need the same field layout used by the writer. */
    def valuesWithOrder(order: Map[Int, List[it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig]]): Map[String, ujson.Value] = {
      val fields = scala.collection.mutable.LinkedHashMap.empty[String, ujson.Value]
      def add(key: String, value: ujson.Value): Unit = {
        require(!fields.contains(key), s"Duplicate constructor field: $key")
        fields(key) = value
      }
      jsonPayloads.zipWithIndex.foreach { (payload, position) =>
        val arguments = ujson.read("[" + payload + "]").arr.toList
        val positional = order.getOrElse(position, Nil).filter(_.suppressKey)
        require(arguments.size >= positional.size, s"Missing positional arguments in constructor group $position")
        positional.zip(arguments).foreach { (config, value) => add(config.varId, value) }
        arguments.drop(positional.size).foreach {
          case obj: ujson.Obj => obj.obj.foreach { (key, value) => add(key, value) }
          case _ => throw new IllegalArgumentException(s"Unnamed argument in constructor group $position")
        }
      }
      fields.toMap
    }

    lazy val values: Map[String, ujson.Value] = valuesWithOrder(Map.empty)

    def valueAsString(key: String): Option[String] = {
      values.get(key).map(write(_))
    }
  }

  private def jsonPayload(using P[?]): P[Js.Val] = JsonGrammar.jsonExpr

  private val allowedCharsInIdentifier: List[Char] = List('_', '$', '/', '\\', '.', '-')

  private def identifier(using P[?]): P[Unit] =
    P(CharPred(c => c.isLetterOrDigit || allowedCharsInIdentifier.contains(c)).rep(1))

  private def getParser()(using P[?]): P[(String, Seq[String])] = {
    P(identifier.! ~ ("(" ~ jsonPayload.rep(sep = ",").! ~ ")").rep(1) ~ End)
  }


  def parseString(input: String): Try[ConstructorLikeReadResult] = {
    parse(input, ctx => getParser()(using ctx)) match {
      case Parsed.Success(resultTuple, index) => {
        val (constructorName: String, jsonPayloads: Seq[String]) = resultTuple
        Success(ConstructorLikeReadResult(constructorName, jsonPayloads))
      }
      case f: Parsed.Failure => {
        scala.util.Failure(SerializedException(s"Error at ConstructorLikeParserWithJsonElements: ${f.msg} (${f.trace().longMsg})"))
      }
    }
  }
}
