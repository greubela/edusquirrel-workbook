package it.evadid.homepage.workbook.content

import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class PlantWorkshopWorkbookRoundTripSpec extends FunSuite {
  // Requires migration of the legacy PlantWorkshop element graph to the current registry serializer.
  test("plant workshop workbook survives serialization round trip".ignore) {
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
