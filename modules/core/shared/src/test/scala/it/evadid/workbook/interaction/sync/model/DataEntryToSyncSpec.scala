package it.evadid.workbook.interaction.sync.model

import it.evadid.workbook.abstractions.InteractionFormat.TextInteractionFormat
import it.evadid.workbook.interaction.sync.{SyncContext, UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.AnswerChangedEvent
import java.time.LocalDateTime
import munit.FunSuite
import upickle.default.*

/** Companion-derived codecs must work without importing DataEntryToSync or global serializer givens. */
class DataEntryToSyncSpec extends FunSuite {
}
