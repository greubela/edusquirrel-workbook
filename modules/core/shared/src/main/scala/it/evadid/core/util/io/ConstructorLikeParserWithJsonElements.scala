package it.evadid.core.util.io

import it.evadid.distribution.command.SerializedException
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import upickle.default.*

import scala.util.{Success, Try}

object ConstructorLikeParserWithJsonElements {

  case class ConstructorLikeReadResult(elementType: String, jsonPayloads: Seq[String]) derives ReadWriter {
    
    /** Positional arguments need the same field layout used by the writer. */
    def valuesWithOrder(order: Map[Int, Seq[VariableDisplayConfig]]): Map[String, ujson.Value] =
      jsonPayloads.zipWithIndex.iterator.flatMap { (payload, position) =>
        fieldsInGroup(payload, position, order.getOrElse(position, Nil))
      }.foldLeft(Map.empty[String, ujson.Value]) { case (fields, (key, value)) =>
        require(!fields.contains(key), s"Duplicate constructor field: $key")
        fields.updated(key, value)
      }

    private def fieldsInGroup(payload: String, position: Int, layout: Seq[VariableDisplayConfig]): Seq[(String, ujson.Value)] = {
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

  /** Only frame constructor groups here; uJSON owns JSON syntax, escaping and numeric validation. */
  def parseString(input: String): Try[ConstructorLikeReadResult] = Try {
    val name = input.takeWhile(c => c.isLetterOrDigit || "_$/\\.-".contains(c))
    require(name.nonEmpty, "Missing constructor name")

    @scala.annotation.tailrec
    def endOfGroup(index: Int, quoted: Boolean = false, escaped: Boolean = false): Int = {
      require(index < input.length, "Unclosed constructor argument group")
      val char = input(index)
      if quoted then
        if escaped then endOfGroup(index + 1, true, false)
        else if char == '\\' then endOfGroup(index + 1, true, true)
        else endOfGroup(index + 1, char != '"', false)
      else if char == ')' then index
      else endOfGroup(index + 1, char == '"', false)
    }

    @scala.annotation.tailrec
    def groups(index: Int, collected: Vector[String]): Seq[String] = {
      val start = input.indexWhere(!_.isWhitespace, index)
      if start == -1 then collected
      else {
        require(input(start) == '(', s"Expected constructor argument group at $start")
        val end = endOfGroup(start + 1)
        val payload = input.substring(start + 1, end)
        ujson.read("[" + payload + "]")
        groups(end + 1, collected :+ payload)
      }
    }
    val payloads = groups(name.length, Vector.empty)
    require(payloads.nonEmpty, "Missing constructor argument group")
    ConstructorLikeReadResult(name, payloads)
  }
}
