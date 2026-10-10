package it.evadid.homepage.workbook.content

import munit.FunSuite

class DigitalWorkbookCatalogSpec extends FunSuite {
  test("the public catalogue contains all nine native entry pages and five standalone digital offerings") {
    val entries = DigitalWorkbookCatalog.entries
    assertEquals(entries.size, 14)
    assertEquals(entries.map(_.id).distinct.size, entries.size)
    val native = entries.flatMap(_.native)
    assertEquals(native.map(_.containerId).toSet, Set("workbookEvacuation", "workbookPlantWorkshop", "workbookBlockchain",
      "workbookImageRecognition", "workbookMonks", "workbookEmbroidery", "workbookEmbroideryPreview", "workbookCompression", "workbookPhishing"))
    assertEquals(native.map(_.page).distinct.size, 9)
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
