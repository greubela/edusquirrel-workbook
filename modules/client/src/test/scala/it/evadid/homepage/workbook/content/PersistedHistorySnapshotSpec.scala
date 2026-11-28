package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.language.{LanguageMap, WorkbookContent}
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.content.test.CreateTestWorkbook
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.abstractions.grading.{GradingResult, GradingStatus}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.history.InteractionVariableHistory
import it.evadid.workbook.history.events.*
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.sync.model.InteractionHistoryJson

import java.time.Instant
import scala.scalajs.js

/** Saved histories of every format used by a published workbook, compared with reference files. A difference is a
  * change of the persisted schema: existing records stop being readable. When that is intended (there is no legacy
  * data to keep), rewrite the files with `UPDATE_PERSISTED_SNAPSHOTS=1 sbt client/testOnly *PersistedHistorySnapshotSpec`.
  */
class PersistedHistorySnapshotSpec extends EditorDomSuite {
  private val directory = "modules/client/src/test/resources/persisted-histories"
  private val fs = js.Dynamic.global.require("fs")
  private val update = js.Dynamic.global.process.env.UPDATE_PERSISTED_SNAPSHOTS.asInstanceOf[js.UndefOr[String]].contains("1")
  private val time = Instant.parse("2026-11-28T12:00:00.123456789Z")
  private def id(n: Int) = EventId(f"00000000-0000-4000-8000-$n%012d")

  // The test workbook first, so that the sample answers change with the fixtures rather than with course content.
  private val factories = ("design" -> CreateTestWorkbook.apply) +:
    DigitalWorkbookCatalog.entries.flatMap(entry => entry.native.map(native => entry.id -> native.factory))
  private lazy val samples: Seq[WorkbookInteractionElement[? <: InteractionFormat]] =
    factories.flatMap((_, factory) => factory(null).createWorkbook.allInteractionsInThisInstance.sortBy(_.keyForSerialization))
      .distinctBy(_.interactionFormat.name)

  /** An answer, a grading start and result, and a hint for `interaction`'s default answer, with fixed IDs and times. */
  private def sample[F <: InteractionFormat](interaction: WorkbookInteractionElement[F]): String = {
    val format = interaction.interactionFormat
    val answer = interaction.defaultValue
    val base = InteractionVariableHistory.empty(format).addEvent(AnswerChangedEvent[F](answer, time, UpdateImportance.MAJOR, id(1)))
    val reference = base.referenceOf(answer)
    val graded = interaction.gradingElements.headOption.fold(base) { grader =>
      base.addEvent(GradingStartedEvent[F](reference, time.plusNanos(1), grader.reference, eventId = id(2)))
        .addEvent(GradingCompletedEvent[F](GradingResult(grader, answer, GradingStatus.Incorrect,
          Some(WorkbookContent(LanguageMap.mapBasedLanguageMap(Map(English -> "Feedback")))), None, time.plusNanos(2)).record(reference),
          eventId = id(3)))
    }
    val hinted = interaction.scaffoldingElement.fold(graded) { scaffolder =>
      graded.addEvent(ScaffoldingCompletedEvent[F](ScaffoldingResult[InteractionFormat.State[F], InteractionFormat.Scaffolding[F]](answer,
        ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.mapBasedLanguageMap(Map(English -> "Hint"))), scaffolder.scaffoldingEntity, None,
        time.plusNanos(3)).record(reference), eventId = id(4)))
    }
    val json = ujson.read(InteractionHistoryJson.fromHistory(format, hinted).serialized)
    json("historyJson")("events")("values") = ujson.Arr.from(json("historyJson")("events")("values").arr.sortBy(_("eventId").str))
    json.render(indent = 2) + "\n"
  }

  test("published formats are covered") {
    assert(samples.size >= 10, samples.map(_.interactionFormat.name).toString)
  }

  test("saved histories keep their persisted schema for every format") {
    val differences = samples.flatMap { interaction =>
      val path = s"$directory/${interaction.interactionFormat.name}.json"
      val written = sample(interaction)
      val saved = if fs.existsSync(path).asInstanceOf[Boolean] then Some(fs.readFileSync(path, "utf8").asInstanceOf[String]) else None
      if update then { fs.writeFileSync(path, written); None }
      else Option.when(!saved.contains(written))(interaction.interactionFormat.name)
    }
    assertEquals(differences, Nil, "persisted schema changed for these formats; see the spec's comment")
    samples.foreach(interaction => assertEquals(InteractionHistoryJson.deserialize(sample(interaction)).format, interaction.interactionFormat))
  }
}
