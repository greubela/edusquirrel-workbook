package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
import it.evadid.core.util.io.{ConstructorLikeParserWithJsonElements, Serializer}
import ujson.Value
import upickle.default.*

object ConstructorLikeSerializer {
  case class VariableDisplayConfig(varId: String, suppressKey: Boolean) derives ReadWriter

  def getAutoFieldsMap[T](obj: T)(implicit regularSerializer: Writer[T]): Map[String, Value] =
    writeJs(obj)(using regularSerializer).obj.toMap

  def serialize[T](elementMapAndOrder: Map[Int, List[VariableDisplayConfig]], obj: T, writer: Writer[T], constructorName: String): String =
    serializeFields(elementMapAndOrder, getAutoFieldsMap(obj)(using writer), constructorName)

  /** Keep JSON values intact; fields without a display configuration remain named fields. */
  def serializeFields(order: Map[Int, List[VariableDisplayConfig]], fields: Map[String, Value], constructorName: String): String = {
    require(order.keys.forall(_ >= 0), "Constructor positions must be non-negative")
    val configured = order.values.flatten.map(_.varId).toList
    require(configured.distinct.size == configured.size, "Constructor fields must be configured only once")
    val extra = fields.keySet.diff(configured.toSet).toList.sorted
    val last = order.keys.maxOption.getOrElse(-1)
    val groups = if (extra.isEmpty) order else order + ((last + 1) -> extra.map(VariableDisplayConfig(_, false)))
    val payloads = (0 to groups.keys.maxOption.getOrElse(0)).map { position =>
      val entries = groups.getOrElse(position, Nil).filter(config => fields.contains(config.varId))
      val positional = entries.filter(_.suppressKey).map(config => ujson.write(fields(config.varId)))
      val named = entries.filterNot(_.suppressKey).map(config => config.varId -> fields(config.varId))
      val arguments = positional ++ (if (named.isEmpty) Nil else List(ujson.write(ujson.Obj.from(named))))
      arguments.mkString("(", ", ", ")")
    }
    constructorName + payloads.mkString
  }

  def deserialize(str: String): ConstructorLikeReadResult =
    ConstructorLikeParserWithJsonElements.parseString(str).get

  def deserialize[T](str: String, reader: Reader[T]): T =
    read[T](ujson.Obj.from(deserialize(str).values))(using reader)

  def deserialize[T](str: String, reader: Reader[T], order: Map[Int, List[VariableDisplayConfig]], constructorName: String): T = {
    val raw = deserialize(str)
    require(raw.elementType == constructorName, s"Expected $constructorName, found ${raw.elementType}")
    read[T](ujson.Obj.from(raw.valuesWithOrder(order)))(using reader)
  }
}

trait ConstructorLikeSerializer[T] extends Serializer[T] {
  override def serialize(obj: T): String =
    ConstructorLikeSerializer.serializeFields(elementMapAndOrder, ujson.read(regularSerializer.serialize(obj)).obj.toMap, constructorName)

  override def deserialize(str: String): T = {
    val raw = ConstructorLikeSerializer.deserialize(str)
    require(raw.elementType == constructorName, s"Expected $constructorName, found ${raw.elementType}")
    regularSerializer.deserialize(ujson.write(ujson.Obj.from(raw.valuesWithOrder(elementMapAndOrder))))
  }

  implicit val regularSerializer: Serializer[T]
  val constructorName: String
  val elementMapAndOrder: Map[Int, List[ConstructorLikeSerializer.VariableDisplayConfig]]
}
