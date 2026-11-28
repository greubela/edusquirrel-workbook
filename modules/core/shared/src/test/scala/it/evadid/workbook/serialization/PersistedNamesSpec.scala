package it.evadid.workbook.serialization

import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.InteractionFormat.TextInputWithLlmGradingAndScaffoldingFormat
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.history.events.*
import it.evadid.workbook.interactions.slideshow.{SlideshowGrader, SlideshowState}
import it.evadid.workbook.sync.UpdateImportance
import munit.FunSuite
import upickle.default.*

import java.time.LocalDateTime

/** Saved histories carry these names; they are fixed literals, so renaming a class or object keeps saved data
  * readable. Changing one of them changes the persisted schema.
  */
class PersistedNamesSpec extends FunSuite {
  private type Grader = WorkbookInteractionGrader[SlideshowState, Unit]

  test("formats are saved under their declared names") {
    val names = InteractionFormat.knownFormats.map(_.name)
    assertEquals(names.distinct, names)
    assertEquals(InteractionFormat.knownFormats.map(format => InteractionFormat.fromName(format.name)), InteractionFormat.knownFormats)
    assert(names.contains("TextInteractionFormat") && names.contains("ProgrammingExerciseWithLlmScaffoldingAndGrading"))
  }

  test("grader types are saved under distinct declared names") {
    assertEquals(WorkbookInteractionGrader.knownCodecs.map(_.persistedType).toSet, Set("ChoiceGrader", "ReorderCodeGrader",
      "ReorderMapIdGrader", "TableCellGrader", "SlideshowGrader", "RecreateShapeGrader", "TurtleCommandUsageGrader",
      "ProgramStructureGrader", "TextInputLlmGrader", "ProgrammingUnitTestGrader", "ProgramDataTestGrader", "MatchingGrader"))
    assertEquals(WorkbookInteractionGrader.knownCodecs.map(_.persistedType).distinct.size, WorkbookInteractionGrader.knownCodecs.size)
    val json = writeJs[Grader](SlideshowGrader(true))
    assertEquals(json("serializedType").str, "SlideshowGrader")
    assertEquals(read[Grader](json), SlideshowGrader(true): Grader)
    intercept[Exception](read[Grader](ujson.Obj("serializedType" -> "RenamedGrader", "isRequired" -> true)))
  }

  test("events are saved under their declared tags") {
    val format = TextInputWithLlmGradingAndScaffoldingFormat
    val event: InteractionEvent[format.type] = AnswerChangedEvent[format.type]("answer", LocalDateTime.of(2026, 11, 28, 12, 0),
      UpdateImportance.MAJOR, EventId("00000000-0000-4000-8000-000000000001"))
    val json = ujson.read(format.codecs.eventSerializer.serialize(event))
    assertEquals(json("$type").str, "AnswerChangedEvent")
    assertEquals(format.codecs.eventSerializer.deserialize(ujson.write(json)), event)
  }
}
