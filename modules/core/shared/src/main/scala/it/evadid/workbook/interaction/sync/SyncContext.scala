package it.evadid.workbook.interaction.sync

import it.evadid.core.util.io.Serializer
import upickle.ReadWriter
import upickle.default.*

case class SyncContext(
                        programId: String,
                        scenarioId: String,
                        userId: String,
                        keyForSerialisation: String
                      ) derives ReadWriter {


  def toUsageContext: UsageContext = UsageContext(programId, scenarioId, userId)

  def doesMatch(usageContext: UsageContext): Boolean = {
    toUsageContext == usageContext
  }

}

object SyncContext {

  def serializer: Serializer[SyncContext] = Serializer.fromImplicitRW[SyncContext]

}


