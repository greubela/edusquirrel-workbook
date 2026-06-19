package it.evadid.workbook.model.interaction.variable

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.sync.UpdateImportance

case class InteractionVariableHistorySerialized(states: Set[InteractionVariableStateSerialized]) {
  override lazy val toString: String = upickle.default.write(this)

  def deserialize[T](serializer: Serializer[T]): InteractionVariableHistory[T] = {
    try {
      val deserializedStates = states.map(_.deserialize(serializer))
      InteractionVariableHistory[T](deserializedStates)
    } catch {
      case e: Throwable => InteractionVariableHistory[T](Set())
    }
  }

}

object InteractionVariableHistorySerialized {

  private given upickle.ReadWriter[UpdateImportance] = upickle.readwriter[String].bimap[UpdateImportance](_.toString, UpdateImportance.valueOf)

  private given upickle.ReadWriter[InteractionVariableStateSerialized] = upickle.macroRW

  private given upickle.ReadWriter[Set[InteractionVariableStateSerialized]] =
    upickle.readwriter[Seq[InteractionVariableStateSerialized]].bimap[Set[InteractionVariableStateSerialized]](_.toSeq, _.toSet)

  private given ivhs: upickle.ReadWriter[InteractionVariableHistorySerialized] = upickle.macroRW
  private given rivhs: upickle.ReadWriter[RichInteractionVariableHistorySerialized] = upickle.macroRW

  val serializer: Serializer[InteractionVariableHistorySerialized] = Serializer.fromUpickleJson(ivhs)


  def apply(str: String): InteractionVariableHistorySerialized = upickle.default.read(str)

  case class RichInteractionVariableHistorySerialized(syncKey: String, lastUpdate: String, lastValue: String, fullHistory: InteractionVariableHistorySerialized) {
    lazy val fullHistorySerialized: String = InteractionVariableHistorySerialized.serializer.serialize(fullHistory)
  }


  val serializerRich: Serializer[RichInteractionVariableHistorySerialized] = Serializer.fromUpickleJson(rivhs)


}
