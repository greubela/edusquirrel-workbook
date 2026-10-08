package it.evadid.workbook.interaction.sync

import upickle.default.*

import it.evadid.core.util.io.Serializer
import upickle.ReadWriter

case class SyncContext(
                        programId: String,
                        scenarioId: String,
                        userId: String,
                        keyForSerialisation: String
                      ) derives ReadWriter {


  def toUsageContext: UsageContext = UsageContext(programId, scenarioId, userId)

}

object SyncContext {

  def serializer: Serializer[SyncContext] = Serializer.fromImplicitRW[SyncContext]

}


