package it.evadid.homepage.workbook.content

import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class PlantWorkshopWorkbookRoundTripSpec extends FunSuite {
  test("plant workshop workbook survives serialization round trip") {
    val original = CreatePlantworkshopWorkbook(
      null.asInstanceOf[FullInfo]
    ).createWorkbook
    val serialized = WorkbookElementFactory.serializerRegularJsonWorkbook.serialize(original)
    val restored = WorkbookElementFactory.serializerRegularJsonWorkbook.deserialize(serialized)

    assertEquals(restored.workbookId, original.workbookId)
    assertEquals(restored.metadata.workbookTitle, original.metadata.workbookTitle)
    assertEquals(restored.metadata.availableLanguages, original.metadata.availableLanguages)
    assertEquals(restored.sections.map(_.sectionId), original.sections.map(_.sectionId))
    assertEquals(restored.allChildrenFullSubtree.map(_.elementId), original.allChildrenFullSubtree.map(_.elementId))
    assertEquals(
      WorkbookElementFactory.serializerRegularJsonWorkbook.serialize(restored),
      serialized
    )
  }
}
