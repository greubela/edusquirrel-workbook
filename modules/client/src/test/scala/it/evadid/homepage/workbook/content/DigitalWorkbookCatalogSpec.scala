package it.evadid.homepage.workbook.content

import munit.FunSuite

class DigitalWorkbookCatalogSpec extends FunSuite {
  test("the public catalogue contains all eight native entry pages and five standalone digital offerings") {
    val entries = DigitalWorkbookCatalog.entries
    assertEquals(entries.size, 13)
    assertEquals(entries.map(_.id).distinct.size, entries.size)
    val native = entries.flatMap(_.native)
    assertEquals(native.map(_.containerId).toSet, Set("workbookEvacuation", "workbookPlantWorkshop", "workbookBlockchain",
      "workbookImageRecognition", "workbookMonks", "workbookEmbroidery", "workbookCompression", "workbookPhishing"))
    assertEquals(native.map(_.page).distinct.size, 8)
    assertEquals(DigitalWorkbookCatalog.nativeWorkbook("workbookTest"), None)
    assertEquals(DigitalWorkbookCatalog.nativeWorkbook("unknown"), None)
    val evacuation = entries.find(_.id == "evacuation").get
    assert(evacuation.inProgress)
    assertEquals(evacuation.native.get.factory(null).createWorkbook.workbookId, "workbookEvacuation")
    entries.foreach { entry =>
      assert(!entry.target.toLowerCase.matches(".*\\.(pdf|zip)$"))
      assert(entry.titleKey.startsWith("workbookSelection/"))
      entry.native.foreach { workbook =>
        assertEquals(DigitalWorkbookCatalog.nativeWorkbook(workbook.containerId), Some(workbook))
        assertEquals(entry.target, "../" + workbook.page + "/")
      }
    }
  }
}
