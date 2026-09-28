package it.evadid.core.util.io.serializer

import it.evadid.core.util.io.{ConstructorLikeParserWithJsonElements, Serializer}
import ujson.Value
import upickle.default.*


trait ConstructorLikeSerializer[T] extends Serializer[T] {

  private case class VariableToSerialize(val key: String, jsonValueAsStr: String, constructorPosition: Int, positionInConstructor: Int, displayConf: VariableDisplayConfig) {
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


  override def serialize(obj: T): String = {
    val jsonString: String = writeJs(obj)(using regularSerializer.uPickleReadWrite).str
    val regularSer: ujson.Value = ujson.read(jsonString)

    val variableSet: Set[VariableToSerialize] = regularSer.obj.keySet.toSet.flatMap(key => {
      createSerVar(key, ujson.write(regularSer.obj(key)))
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

  override def deserialize(str: String): T = {
    val parsed: (String, Seq[String]) = ConstructorLikeParserWithJsonElements.parseString(str).get
    val mapped: Map[String, Value] = parsed._2.flatMap { curJsonStr =>
      ujson.read(curJsonStr).obj.map {
        case (key, value) => key -> value
      }
    }.toMap
    regularSerializer.deserialize(write(mapped))
  }

  implicit val regularSerializer: Serializer[T]

  val constructorName: String

  case class VariableDisplayConfig(varId: String, inlinedWithoutKey: Boolean)

  val elementMapAndOrder: Map[Int, List[VariableDisplayConfig]]

  private def createSerVar(key: String, jsonValueAsStr: String): Option[VariableToSerialize] = {
    elementMapAndOrder
      .zipWithIndex
      .flatMap((tup, indexNr) => tup._2.map(cur => (tup._1, indexNr, cur)))
      .find(_._3.varId == key)
      .map(trip => VariableToSerialize(key, jsonValueAsStr, trip._1, trip._2, trip._3))
  }

}
