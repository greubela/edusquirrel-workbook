package it.evadid.homepage.workbook

import it.evadid.core.datastructures.geometry.Point
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.LineToRender
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.*
import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchProgramModel.*
import it.evadid.homepage.workbook.legacy.model.feedback.FeedbackStatus
import munit.FunSuite
import upickle.default.*

class SystematicClientCodecSpec extends FunSuite {
  private def roundTrip[T: ReadWriter](value: T): Unit = {
    assertEquals(read[T](write(value)), value)
    assertEquals(readBinary[T](writeBinary(value)), value)
  }

  test("line geometry supplies its numeric context on decode") {
    roundTrip(LineToRender(Point(1.0, 2.0), Point(3.0, 4.0), jump = true))
  }

  test("session export records expose their default codec without eager initialization cycles") {
    import it.evadid.core.datastructures.user.*
    import it.evadid.core.datastructures.language.AppLanguage.English
    import it.evadid.core.datastructures.language.LanguageMapContentId
    import it.evadid.workbook.session.SessionData
    import it.evadid.workbook.elements.structure.Workbook
    import it.evadid.workbook.sync.SyncContext
    import it.evadid.workbook.sync.model.InteractionHistoryJson
    import Workbook.WorkbookMetadata
    val user = User("Fixture", "fixture", "fixture@example.com")
    val workbook = Workbook("fixture", WorkbookMetadata(Set(user), Set.empty, LanguageMapContentId("fixture/title"), List(English)), Nil)
    
    val value = SessionData(java.time.LocalDateTime.of(2026, 11, 7, 0, 0), Map.empty[SyncContext, InteractionHistoryJson], user, Some(UserConfig(Nil, isOnlineAccount = false)),
      it.evadid.workbook.session.AllWorkbookInfo(workbook, it.evadid.workbook.session.WorkbookConfig(None), Map.empty))
    roundTrip(value)
    assertEquals(read[SessionData](write(value)).timestampStorage, value.timestampStorage)
  }
}
