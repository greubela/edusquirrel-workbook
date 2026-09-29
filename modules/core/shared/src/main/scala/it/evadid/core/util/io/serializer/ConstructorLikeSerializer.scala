package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.{VariableDisplayConfig, VariableToSerialize}
import it.evadid.core.util.io.{ConstructorLikeParserWithJsonElements, Serializer}
import ujson.Value
import upickle.default.*

object ConstructorLikeSerializer {

  case class VariableDisplayConfig(varId: String, inlinedWithoutKey: Boolean)


  private case class VariableToSerialize(
                                          val key: String,
                                          jsonValueAsStr: String,
                                          constructorPosition: Int,
                                          positionInConstructor: Int,
                                          displayConf: VariableDisplayConfig
                                        ) {
    override val toString: String = {
      s"\"${key}: ${jsonValueAsStr}"
    }
  }

  private def formatConstructorEntry(set: Set[VariableToSerialize]): String = {
    set.toList.sortBy(_.positionInConstructor).map(curEntry => {
      if (curEntry.displayConf.inlinedWithoutKey) curEntry.jsonValueAsStr
      else s"\n${curEntry.key}: ${curEntry.jsonValueAsStr}"
    }).mkString("(", ", ", ")")
  }

  private def createSerVar(elementMapAndOrder: Map[Int, List[VariableDisplayConfig]], key: String, jsonValueAsStr: String): Option[VariableToSerialize] = {
    elementMapAndOrder
      .zipWithIndex
      .flatMap((tup, indexNr) => tup._2.map(cur => (tup._1, indexNr, cur)))
      .find(_._3.varId == key)
      .map(trip => VariableToSerialize(key, jsonValueAsStr, trip._1, trip._2, trip._3))
  }

  def serialize[T](elementMapAndOrder: Map[Int, List[VariableDisplayConfig]], obj: T, writer: Writer[T], constructorName: String): String = {
    val jsonString: String = writeJs(obj)(using writer).str
    val regularSer: ujson.Value = ujson.read(jsonString)
    val variableSet: Set[VariableToSerialize] = regularSer.obj.keySet.toSet.flatMap(key => {
      createSerVar(elementMapAndOrder, key, ujson.write(regularSer.obj(key)))
    })
    if (variableSet.nonEmpty) {
      val maxPos = variableSet.maxBy(_.constructorPosition).constructorPosition
      val grouped = variableSet.groupBy(_.constructorPosition)
      val res: List[Set[VariableToSerialize]] = 0.to(maxPos).toList.map(curNr => grouped.getOrElse(curNr, Set()))
      res.map(formatConstructorEntry).mkString(constructorName, "", "")
    } else {
      constructorName + "()"
    }
  }

  case class ConstructorLikeReadResult(elementType: String, values: Map[String, ujson.Value]) derives ReadWriter {
    def valueAsString(key: String): Option[String] = {
      values.get(key).map(write(_))
    }
  }

  def deserialize(str: String): ConstructorLikeReadResult = {
    val parsed: (String, Seq[String]) = ConstructorLikeParserWithJsonElements.parseString(str).get
    val mapped: Map[String, Value] = parsed._2.flatMap { curJsonStr =>
      ujson.read(curJsonStr).obj.map((key, value) => key -> value)
    }.toMap
    ConstructorLikeReadResult(parsed._1, mapped)
  }

  def deserialize[T](str: String, reader: Reader[T]): T = {
    val raw = deserialize(str)
    read(write(raw.values))(using reader)
  }

}

trait ConstructorLikeSerializer[T] extends Serializer[T] {

  private def formatConstructorEntry(set: Set[VariableToSerialize]): String =
    ConstructorLikeSerializer.formatConstructorEntry(set)

  override def serialize(obj: T): String =
    ConstructorLikeSerializer.serialize(elementMapAndOrder, obj, regularSerializer.uPickleReadWrite, constructorName)

  override def deserialize(str: String): T = ConstructorLikeSerializer.deserialize(str, regularSerializer.uPickleReadWrite)

  implicit val regularSerializer: Serializer[T]

  val constructorName: String

  val elementMapAndOrder: Map[Int, List[VariableDisplayConfig]]


}
