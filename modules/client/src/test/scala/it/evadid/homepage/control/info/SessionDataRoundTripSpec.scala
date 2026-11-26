package it.evadid.homepage.control.info

import it.evadid.core.datastructures.file.LoadedFile
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, UserConfig}
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.elements.structureElements.WorkbookSection.WorkbookSectionMetadata
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.meta.ExecutionContextInformation
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*

/** A downloaded session file brings the learner back to the same workbook with the same answers. */
class SessionDataRoundTripSpec extends EditorDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private val user = AllUserInfo.createNewUser("Student", "session@example.invalid", UserConfig(Nil, false))

  /** A fresh instance of a workbook definition, as each page load creates. */
  private def workbook(id: String, interactions: String*): AllWorkbookInfo = {
    val section = WorkbookSection("section", WorkbookSectionMetadata(LanguageMapContentId("spec/section")), interactions.map(TextInteraction(_)))
    val book = Workbook(id, WorkbookMetadata(Set.empty, Set.empty, LanguageMapContentId("spec/title"), Seq(AppLanguage.English)), Seq(section))
    AllWorkbookInfo(book, WorkbookConfig(Some(section)), Map.empty)
  }
  private def client(open: Option[AllWorkbookInfo]): FullInfo =
    FullInfo(HomepageInfo(AppLanguage.English, open, Some(user), AllDisplayInfo(false, None), LanguageMapStorage.empty))
  private def answers(info: AllWorkbookInfo): Map[String, String] =
    info.loadedWorkbook.allContainedInteractionsById.map((id, interaction) => id -> interaction.interactionVariable.currentValue.toString)
  private def answer(info: AllWorkbookInfo, id: String, value: String): Unit =
    info.loadedWorkbook.allContainedInteractionsById(id).asInstanceOf[TextInteraction].interactionVariable
      .setStateFromUserInteraction(value, UpdateImportance.MAJOR, context = ExecutionContextInformation.default)

  /** The text a download writes: answers of `id`'s interactions `first` and `second`. */
  private def downloaded(id: String = "workbook/part-1"): String = {
    val studied = workbook(id, "first", "second")
    answer(studied, "first", "my first answer")
    answer(studied, "second", "my second answer")
    val session = SessionData.createSessionData(HomepageInfo(AppLanguage.English, Some(studied), Some(user),
      AllDisplayInfo(false, None), LanguageMapStorage.empty)).get
    assert(!write(session).contains("signedBackendToken\":{"), "credentials are not exported")
    write(session)
  }

  test("an open instance of the same workbook is kept and receives the answers") {
    val file = downloaded()
    val open = workbook("workbook/part-1", "first", "second")
    val restoredClient = client(Some(open))
    answer(open, "second", "a newer local answer")
    restoredClient.userSessionControl.continueWithSession(read[SessionData](file)).map { _ =>
      assert(restoredClient.current.workbookInfo.get.loadedWorkbook eq open.loadedWorkbook)
      assertEquals(answers(open)("first"), "my first answer")
      assertEquals(answers(open)("second"), "a newer local answer", "the newest answer stays current")
      assertEquals(open.loadedWorkbook.allContainedInteractionsById("second").interactionVariable.currentHistory.allStates
        .map(_.newState.toString), Set("my second answer", "a newer local answer"))
    }
  }

  test("answers never leak into another open workbook that reuses the element ids") {
    val other = workbook("workbook/part-2", "first", "second")
    val restoredClient = client(Some(other))
    restoredClient.userSessionControl.continueWithSession(read[SessionData](downloaded())).map { _ =>
      assertEquals(restoredClient.current.workbookInfo.get.loadedWorkbook.workbookId, "workbook/part-1")
      assertEquals(answers(other), Map("first" -> "", "second" -> ""))
    }
  }

  test("a workbook restores only its own histories, and an unreadable one does not hide the others") {
    val session = read[SessionData](downloaded())
    val firstKey = session.knownUsageData.keys.find(_.keyForSerialisation == "first_history").get
    val broken = session.copy(knownUsageData = session.knownUsageData.updated(firstKey,
      session.knownUsageData(firstKey).copy(historyJson = "{\"not\": \"a history\"}")))
    val target = workbook("workbook/part-1", "first", "second")
    target.loadedWorkbook.restoreHistories(it.evadid.util.logging.BasicLogger(), broken.knownUsageData, ExecutionContextInformation.default)
    assertEquals(answers(target), Map("first" -> "", "second" -> "my second answer"))
    val elsewhere = workbook("workbook/part-2", "first", "second")
    elsewhere.loadedWorkbook.restoreHistories(it.evadid.util.logging.BasicLogger(), session.knownUsageData, ExecutionContextInformation.default)
    assertEquals(answers(elsewhere), Map("first" -> "", "second" -> ""))
  }

  test("the file name has no path separators and a file that is not a session fails the import") {
    val session = read[SessionData](downloaded("course/unit\\one"))
    assert(session.fileName.endsWith("-course-unit-one.eva"), session.fileName)
    assert(!session.fileName.exists(c => c == '/' || c == '\\'), session.fileName)
    val file = new LoadedFile {
      def description = null
      def data = "{\"not\": \"a session\"}".getBytes("UTF-8")
      override def toString = "spec file"
    }
    client(None).userSessionControl.tryContinueWithSessionFile(file).failed.map(error => assert(error != null))
  }
}
