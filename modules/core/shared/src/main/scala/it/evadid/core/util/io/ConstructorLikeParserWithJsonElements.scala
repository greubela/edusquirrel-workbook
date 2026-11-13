package it.evadid.core.util.io

import fastparse.*
import fastparse.MultiLineWhitespace.*
import it.evadid.distribution.command.SerializedException
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.util.parsing.{Js, JsonGrammar}
import upickle.default.*

import scala.util.{Success, Try}

object ConstructorLikeParserWithJsonElements {

  case class ConstructorLikeReadResult(elementType: String, jsonPayloads: Seq[String]) derives ReadWriter {
    
    /** Positional arguments need the same field layout used by the writer. */
    def valuesWithOrder(order: Map[Int, List[VariableDisplayConfig]]): Map[String, ujson.Value] =
      jsonPayloads.zipWithIndex.iterator.flatMap { (payload, position) =>
        fieldsInGroup(payload, position, order.getOrElse(position, Nil))
      }.foldLeft(Map.empty[String, ujson.Value]) { case (fields, (key, value)) =>
        require(!fields.contains(key), s"Duplicate constructor field: $key")
        fields.updated(key, value)
      }

    private def fieldsInGroup(payload: String, position: Int, layout: List[VariableDisplayConfig]): List[(String, ujson.Value)] = {
      val arguments = ujson.read("[" + payload + "]").arr.toList
      val positional = layout.filter(_.suppressKey)
      require(arguments.size >= positional.size, s"Missing positional arguments in constructor group $position")
      val positionalFields = positional.zip(arguments).map((config, value) => config.varId -> value)
      val namedFields = arguments.drop(positional.size).flatMap {
        case obj: ujson.Obj => obj.obj.toList
        case _ => throw new IllegalArgumentException(s"Unnamed argument in constructor group $position")
      }
      positionalFields ++ namedFields
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
