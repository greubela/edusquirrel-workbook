package it.evadid.homepage.control.startup

import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.homepage.workbook.content.DigitalWorkbookCatalog

/** An entry page's container id decides which workbook the page opens. */
class HomepageStartupLogicSpec extends UiDomSuite {
  test("workbooks are found by their container id; unknown ids open nothing") {
    val page = HtmlFullWorkbookApp.fullInfo
    assertEquals(HomepageStartupLogic.loadWorkbookById("noSuchContainer"), None)
    assert(HomepageStartupLogic.loadWorkbookById("workbookTest").nonEmpty, "the design page opens the test workbook")
    DigitalWorkbookCatalog.entries.flatMap(_.native).take(2).foreach { native =>
      val opened = HomepageStartupLogic.loadWorkbookById(native.containerId).getOrElse(fail(native.containerId))
      assertEquals(opened.loadedWorkbook.workbookId, native.factory(page).createEverything.loadedWorkbook.workbookId)
    }
  }
}
