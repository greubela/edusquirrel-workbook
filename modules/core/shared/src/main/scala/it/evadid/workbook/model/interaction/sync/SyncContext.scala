package it.evadid.workbook.model.interaction.sync

import it.evadid.core.util.io.Serializer
import upickle.ReadWriter

case class SyncContext(
                        programId: String,
                        scenarioId: String,
                        userId: String,
                        keyForSerialisation: String
                      ) {


  def toUsageContext: UsageContext = UsageContext(programId, scenarioId, userId)

}

object SyncContext {

  private given rw: ReadWriter[SyncContext] = upickle.default.macroRW
  def serializer: Serializer[SyncContext] = Serializer.fromUpickleJson(rw)

}


