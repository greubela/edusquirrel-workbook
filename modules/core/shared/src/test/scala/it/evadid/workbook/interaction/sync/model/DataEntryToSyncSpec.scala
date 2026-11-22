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
  test("generic entries round-trip as JSON and MessagePack for both directions") {
    SyncDirection.values.foreach { direction =>
      val entry = DataEntryToSync("answer", 42, direction)
      assertEquals(read[DataEntryToSync[String, Int]](write(entry)), entry)
      assertEquals(readBinary[DataEntryToSync[String, Int]](writeBinary(entry)), entry)
    }
  }

  test("a typed context and history envelope round-trip without extra serializer imports") {
    val history = InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(
      AnswerChangedEvent[TextInteractionFormat.type]("answer", LocalDateTime.of(2026, 11, 18, 12, 0, 0, 123456789), UpdateImportance.MAJOR))
    val entry = DataEntryToSync(UsageContext("program", "workbook", "user").toSyncContext("answer_history"),
      InteractionHistoryJson.fromHistory(TextInteractionFormat, history), SyncDirection.SERVER_TO_CLIENT)
    Seq(read[DataEntryToSync[SyncContext, InteractionHistoryJson]](write(entry)),
      readBinary[DataEntryToSync[SyncContext, InteractionHistoryJson]](writeBinary(entry))).foreach { restored =>
      assertEquals(restored, entry)
      assertEquals(restored.dataValue.typedAs(TextInteractionFormat), history)
    }
  }
}
