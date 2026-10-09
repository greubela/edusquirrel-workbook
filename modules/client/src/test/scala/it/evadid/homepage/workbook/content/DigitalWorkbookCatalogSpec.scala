package it.evadid.homepage.workbook.content

import munit.FunSuite

class DigitalWorkbookCatalogSpec extends FunSuite {
  test("the public catalogue contains all seven native entry pages and five standalone digital offerings") {
    val entries = DigitalWorkbookCatalog.entries
    assertEquals(entries.size, 12)
    assertEquals(entries.map(_.id).distinct.size, entries.size)
    val native = entries.flatMap(_.native)
    assertEquals(native.map(_.containerId).toSet, Set("workbookPlantWorkshop", "workbookBlockchain",
      "workbookImageRecognition", "workbookMonks", "workbookEmbroidery", "workbookCompression", "workbookPhishing"))
    assertEquals(native.map(_.page).distinct.size, 7)
    assertEquals(DigitalWorkbookCatalog.nativeWorkbook("workbookTest"), None)
    assertEquals(DigitalWorkbookCatalog.nativeWorkbook("unknown"), None)
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
