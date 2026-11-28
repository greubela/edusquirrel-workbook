package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.user.{User, UserConfig}
import it.evadid.workbook.elements.structure.{Workbook, WorkbookSection}
import it.evadid.workbook.elements.structure.Workbook.WorkbookMetadata
import it.evadid.workbook.elements.structure.WorkbookSection.WorkbookSectionMetadata
import it.evadid.workbook.history.InteractionHistory
import it.evadid.workbook.history.events.{AnswerChangedEvent, EventId}
import it.evadid.workbook.interactions.basic.TextInteraction
import it.evadid.workbook.session.{AllWorkbookInfo, SessionData, WorkbookConfig}
import it.evadid.workbook.sync.{SyncContext, UpdateImportance}
import it.evadid.workbook.sync.model.InteractionHistoryJson
import upickle.default.*

import java.time.Instant
import scala.scalajs.js

/** Saved user configurations and session files, compared with reference files like [[PersistedHistorySnapshotSpec]].
  * Rewrite them with `UPDATE_PERSISTED_SNAPSHOTS=1 sbt client/testOnly *PersistedRecordSnapshotSpec`.
  */
class PersistedRecordSnapshotSpec extends munit.FunSuite {
  private val directory = "modules/client/src/test/resources/persisted-records"
  private val fs = js.Dynamic.global.require("fs")
  private val update = js.Dynamic.global.process.env.UPDATE_PERSISTED_SNAPSHOTS.asInstanceOf[js.UndefOr[String]].contains("1")
  private val time = Instant.parse("2026-11-28T12:00:00Z")

  private val config = UserConfig.defaults(isLocalAccount = false)
  private val session = {
    val answer = TextInteraction("answer")
    val section = WorkbookSection("section", WorkbookSectionMetadata(LanguageMapContentId("snapshot/section")), Seq(answer))
    val workbook = Workbook("snapshot-workbook", WorkbookMetadata(Set(User("Author", "author", "author@example.invalid")), Set.empty,
      LanguageMapContentId("snapshot/title"), Seq(AppLanguage.English)), Seq(section))
    val format = answer.interactionFormat
    val history = InteractionHistory.empty(format).addEvent(AnswerChangedEvent(answer.defaultValue, time, UpdateImportance.MAJOR,
      EventId("00000000-0000-4000-8000-000000000001")))
    SessionData(time, Map(SyncContext("program", "scenario", "student", answer.keyForSerialization) -> InteractionHistoryJson.fromHistory(format, history)),
      User("Student", "student", "student@example.invalid"), Some(config.withoutCredentials),
      AllWorkbookInfo(workbook, WorkbookConfig(Some(section)), Map("section" -> 5.0)))
  }

  private def matches(name: String, written: String): Boolean = {
    val path = s"$directory/$name.json"
    if update then { fs.writeFileSync(path, written); true }
    else fs.existsSync(path).asInstanceOf[Boolean] && fs.readFileSync(path, "utf8").asInstanceOf[String] == written
  }

  test("saved user configurations keep their persisted schema") {
    assert(matches("UserConfig", ujson.read(config.toJson).render(indent = 2) + "\n"), "persisted schema changed; see the spec's comment")
    assertEquals(read[UserConfig](config.toJson), config)
  }

  test("saved session files keep their persisted schema") {
    val json = write(session, indent = 2) + "\n"
    assert(matches("SessionData", json), "persisted schema changed; see the spec's comment")
    assertEquals(read[SessionData](json).knownUsageData, session.knownUsageData)
  }
}
