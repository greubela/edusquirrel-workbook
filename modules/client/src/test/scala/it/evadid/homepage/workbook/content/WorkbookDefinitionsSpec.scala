package it.evadid.homepage.workbook.content

import it.evadid.homepage.workbook.content.test.CreateTestWorkbook
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.elements.structureElements.Workbook
import upickle.default.*
import munit.FunSuite

class WorkbookDefinitionsSpec extends FunSuite {
  private val factories = DigitalWorkbookCatalog.entries.flatMap(entry => entry.native.map(native => entry.id -> native.factory)) :+
    ("design" -> CreateTestWorkbook.apply)

  test("catalogue identifiers and native entry points are unique") {
    val entries = DigitalWorkbookCatalog.entries
    assertEquals(entries.map(_.id).distinct.size, entries.size)
    val native = entries.flatMap(_.native)
    assertEquals(native.map(_.containerId).distinct.size, native.size)
    assertEquals(native.map(_.page).distinct.size, native.size)
    native.foreach(entry => assertEquals(DigitalWorkbookCatalog.nativeWorkbook(entry.containerId), Some(entry)))
    assertEquals(DigitalWorkbookCatalog.nativeWorkbook("unknown"), None)
  }

  factories.foreach { (name, factory) =>
    test(s"$name workbook definition has unique IDs and round-trips uPickle JSON and factory formats") {
      val workbook = factory(null).createWorkbook
      val ids = workbook.allChildrenFullSubtree.map(_.elementId)
      assert(ids.nonEmpty, workbook.workbookId)
      assertEquals(ids.distinct.size, ids.size, workbook.workbookId)
      assertEquals(read[Workbook](write[Workbook](workbook)), workbook, workbook.workbookId)
      for serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike) do
        assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook, workbook.workbookId)
    }
  }
}
