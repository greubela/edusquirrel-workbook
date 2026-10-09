package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.evacuation.*
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class CreateEvacuationWorkbookSpec extends FunSuite {
  test("partial evacuation workbook includes independent construction, simulation and reflection tasks") {
    val workbook = CreateEvacuationWorkbook(null).createWorkbook
    assertEquals(workbook.sections.size, 5)
    val elements = workbook.allChildrenFullSubtree
    assertEquals(elements.collect { case e: EvacuationSimulationInteraction => e }.size, 1)
    assertEquals(elements.collect { case e: EvacuationConstructFloorInteraction => e }.size, 1)
    assert(elements.exists(_.elementId == "evacuation-time-derivation"))
    assert(elements.exists(_.elementId == "evacuation-model-limits"))
    assertEquals(elements.map(_.elementId).distinct.size, elements.size)
  }
  test("entire evacuation workbook round-trips through both definition formats") {
    val workbook = CreateEvacuationWorkbook(null).createWorkbook
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook)
  }
}
