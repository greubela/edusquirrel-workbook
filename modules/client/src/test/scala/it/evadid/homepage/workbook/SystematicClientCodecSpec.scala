package it.evadid.homepage.workbook

import it.evadid.core.datastructures.geometry.Point
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.LineToRender
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch.TurtleStitchProgramModel.*
import it.evadid.homepage.workbook.legacy.model.feedback.FeedbackStatus
import todomove.webElementsOld.webElements.shapes.meta.*
import munit.FunSuite
import upickle.default.*

class SystematicClientCodecSpec extends FunSuite {
  private def roundTrip[T: ReadWriter](value: T): Unit = {
    assertEquals(read[T](write(value)), value)
    assertEquals(readBinary[T](writeBinary(value)), value)
  }

  test("feedback retains its original timestamp rather than recreating it during decode") {
    val value = UltrichsNewCoolFeedback.empty("print('学校')").copy(
      tests = Seq(PythonTestResult("test", false, "1", "2", Some("mismatch"))),
      displayHints = Seq("hint"), status = FeedbackStatus.FINISHED, timestampEpochMillis = 123456789L)
    roundTrip(value)
    assertEquals(read[UltrichsNewCoolFeedback](write(value)).timestampEpochMillis, 123456789L)
  }

  test("Snap project models retain nested scenes and constructor defaults") {
    val project = Project(name = "学校", scenes = Vector(Scene(name = "one"), Scene(name = "two")))
    roundTrip(project)
    assertEquals(read[Project]("{}"), Project())
  }

  test("line geometry supplies its numeric context on decode") {
    roundTrip(LineToRender(Point(1.0, 2.0), Point(3.0, 4.0), jump = true))
  }

  test("position policies retain both variants and subsequent movement") {
    val values: List[PositionInformation[Double]] = List(PositionUnknown(), PositionIsOffset(Point(2.0, 3.0)))
    values.foreach { value =>
      roundTrip(value)
      val decoded = read[PositionInformation[Double]](write(value))
      assertEquals(decoded.setToOrMoveBy(Point(4.0, 5.0)), value.setToOrMoveBy(Point(4.0, 5.0)))
    }
    roundTrip(PositionUnknown[Double]())
    roundTrip(PositionIsOffset(Point(2.0, 3.0)))
  }

  test("session export records expose their default codec without eager initialization cycles") {
    import it.evadid.core.datastructures.user.*
    import it.evadid.core.datastructures.language.AppLanguage.English
    import it.evadid.core.datastructures.language.LanguageMapContentId
    import it.evadid.homepage.control.info.WorkbookUserDataAnalyzer
    import WorkbookUserDataAnalyzer.SessionData
    import it.evadid.workbook.elements.structureElements.Workbook
    import Workbook.WorkbookMetadata
    val user = User("Fixture", "fixture", "fixture@example.com")
    val workbook = Workbook("fixture", WorkbookMetadata(Set(user), Set.empty, LanguageMapContentId("fixture/title"), List(English)), Nil)
    val value = SessionData(AllUserInfo(user, None, UserConfig(Nil, false)), Map.empty, workbook, 123456789L)
    roundTrip(value)
    assertEquals(writeJs(value), ujson.read(WorkbookUserDataAnalyzer.serializerSessionData.serialize(value)))
  }
}
