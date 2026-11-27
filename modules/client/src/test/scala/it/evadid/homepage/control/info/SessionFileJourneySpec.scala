package it.evadid.homepage.control.info

import it.evadid.workbook.session.{AllWorkbookInfo, WorkbookConfig}
import it.evadid.core.datastructures.file.LoadedFile
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, UserConfig}
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.util.DownloadToDisc
import it.evadid.util.logging.BasicLogger
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleGraphic, TurtleRecreateShapeInteraction}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.elements.structureElements.WorkbookSection.WorkbookSectionMetadata
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.meta.ExecutionContextInformation
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

/** A learner downloads their session and continues it elsewhere through the same entry points the page uses. */
class SessionFileJourneySpec extends EditorDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private val user = AllUserInfo.createNewUser("Student", "journey@example.invalid", UserConfig(Nil, false))
  private val square = TurtleGraphic.TurtleGraphicProgram(
    ProgrammingStateRaw(Python, "for _ in range(4):\n    forward(20)\n    left(90)\n").toBeExpression.turtleCommandExecutionInfo.completedCommands)
  /** A square drawn by a reporter and a predicate. */
  private val program = ProgrammingStateRaw(Python, "def side(n: float) -> float:\n    return n * 4\n\ndef more(i: float) -> bool:\n" +
    "    return i < 4\n\ni = 0\nwhile more(i):\n    forward(side(5))\n    left(90)\n    i = i + 1\n")

  private def workbook(): AllWorkbookInfo = {
    val interactions = List(TurtleRecreateShapeInteraction("square", ProgrammingStateRaw(Python, "pass\n"), square), TextInteraction("note"))
    val section = WorkbookSection("section", WorkbookSectionMetadata(LanguageMapContentId("journey/section")), interactions)
    AllWorkbookInfo(Workbook("journey/workbook", WorkbookMetadata(Set.empty, Set.empty, LanguageMapContentId("journey/title"),
      Seq(AppLanguage.English)), Seq(section)), WorkbookConfig(Some(section)), Map.empty)
  }
  private def client(open: Option[AllWorkbookInfo], signedIn: Option[AllUserInfo]): FullInfo =
    FullInfo(HomepageInfo(AppLanguage.English, open, signedIn, AllDisplayInfo(false, None), LanguageMapStorage.empty))
  private def interaction(info: FullInfo, id: String) = info.current.workbookInfo.get.loadedWorkbook.allContainedInteractionsById(id)

  /** Runs `download` and returns the file name and text the page would hand to the browser. */
  private def captureDownload(download: => Unit): Future[(String, String)] = {
    val url = js.Dynamic.global.URL
    val (create, revoke) = (url.createObjectURL, url.revokeObjectURL)
    val anchor = js.Dynamic.global.window.HTMLAnchorElement.prototype
    val click = anchor.click
    val blob = Promise[js.Dynamic]()
    val name = Promise[String]()
    url.updateDynamic("createObjectURL")(((value: js.Dynamic) => { blob.trySuccess(value); "blob:session" }): js.Function1[js.Dynamic, String])
    url.updateDynamic("revokeObjectURL")(((_: String) => ()): js.Function1[String, Unit])
    anchor.updateDynamic("click")((((self: js.Dynamic) => { name.trySuccess(self.download.asInstanceOf[String]); () }): js.ThisFunction0[js.Dynamic, Unit]))
    try download finally {
      url.updateDynamic("createObjectURL")(create)
      url.updateDynamic("revokeObjectURL")(revoke)
      anchor.updateDynamic("click")(click)
    }
    for {
      file <- name.future
      content <- blob.future.flatMap(value => value.applyDynamic("text")().asInstanceOf[js.Promise[String]].toFuture)
    } yield file -> content
  }

  private def uploaded(content: String): LoadedFile = new LoadedFile {
    def description = null
    def data = content.getBytes("UTF-8")
    override def toString = "uploaded session file"
  }

  test("a downloaded session file continues in a fresh page with nobody signed in: user, workbook, answers and grading") {
    val first = client(Some(workbook()), Some(user))
    val answer: ProgrammingState = program.toSnapXml
    interaction(first, "square").asInstanceOf[TurtleRecreateShapeInteraction].interactionVariable
      .setStateFromUserInteraction(answer, UpdateImportance.MAJOR, context = ExecutionContextInformation.default)
    interaction(first, "note").asInstanceOf[TextInteraction].interactionVariable
      .setStateFromUserInteraction("my reasoning", UpdateImportance.MAJOR, context = ExecutionContextInformation.default)
    val logger = BasicLogger()
    captureDownload(WorkbookUserDataAnalyzer().downloadAllData(logger, DownloadToDisc(logger), first.homepageInfoNow())).flatMap { (name, content) =>
      assert(name.endsWith("-journey-workbook.eva") && name.contains(user.user.id), name)
      assert(!content.contains("signedBackendToken\":{"), "the file holds no credentials")
      val second = client(None, None)
      second.userSessionControl.tryContinueWithSessionFile(uploaded(content)).flatMap { _ =>
        assertEquals(second.current.userInfo.map(_.user.id), Some(user.user.id), "the session's user is registered locally")
        assertEquals(second.current.workbookInfo.map(_.loadedWorkbook.workbookId), Some("journey/workbook"))
        val restored = interaction(second, "square").asInstanceOf[TurtleRecreateShapeInteraction]
        assertEquals(restored.interactionVariable.currentValue, answer)
        assertEquals(interaction(second, "note").asInstanceOf[TextInteraction].interactionVariable.currentValue, "my reasoning")
        restored.interactionVariable.control.getOrCalculateGrading(ExecutionContextInformation.default)
          .flatMap(_ => restored.interactionVariable.control.getOrCalculateGrading(ExecutionContextInformation.default, forceGrading = true)).map { graded =>
          assertEquals(graded.testsWithResults.head.gradingStatus, GradingStatus.Correct, "the restored reporter answer still draws the square")
          List(Python, Java).foreach { language =>
            val converted = restored.interactionVariable.currentValue.convertToLanguage(language).finalState.get
            assertEquals(converted.toBeExpression.turtleCommandExecutionInfo.completedCommands, square.toTurtleProgram.toList, language.toString)
          }
        }
      }
    }
  }

  test("uploading a file that is not a session leaves the page unchanged") {
    val page = client(Some(workbook()), Some(user))
    page.userSessionControl.tryContinueWithSessionFile(uploaded("PK\u0003\u0004 not a session")).failed.map { _ =>
      assertEquals(page.current.userInfo.map(_.user.id), Some(user.user.id))
      assertEquals(page.current.workbookInfo.map(_.loadedWorkbook.workbookId), Some("journey/workbook"))
    }
  }
}
