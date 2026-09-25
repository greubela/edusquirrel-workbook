package it.evadid.homepage.workbook.content

import it.evadid.homepage.control.model.FullInfo
import it.evadid.core.datastructures.file.{CopyrightInfo, UrlFileDescription}
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class PlantWorkshopWorkbookRoundTripSpec extends FunSuite {
  test("plant workshop workbook survives serialization round trip") {
    val original = CreatePlantworkshopWorkbook(
      null.asInstanceOf[FullInfo],
      Some(path => UrlFileDescription(s"https://example.test/resources/$path", CopyrightInfo.unknownCopyrightInfo))
    ).createWorkbook
    val serialized = WorkbookElementFactory.workbookElementSerializer.serialize(original)
    val restored = WorkbookElementFactory.workbookElementSerializer.deserialize(serialized).asInstanceOf[Workbook]

    assertEquals(restored.workbookId, original.workbookId)
    assertEquals(restored.workbookTitle, original.workbookTitle)
    assertEquals(restored.availableLanguages, original.availableLanguages)
    assertEquals(restored.sections.map(_.sectionId), original.sections.map(_.sectionId))
    assertEquals(restored.allChildrenInSubtree.map(_.elementId), original.allChildrenInSubtree.map(_.elementId))
    assertEquals(
      WorkbookElementFactory.workbookElementSerializer.serialize(restored),
      serialized
    )
  }
}
