package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction
import it.evadid.workbook.elements.interactionElements.pixel.BinaryPixelInteraction
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.SortingReasonInteraction
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.model.compression.*

class CreateCompressionWorkbookSpec extends munit.FunSuite {
  test("compression retains all chapters, written answers and classification activities with native experiments") {
    val workbook = CreateCompressionWorkbook(null).createWorkbook
    assertEquals(workbook.sections.map(_.elementId), List("section0", "section1", "section2", "section3", "sectionSortingDemo", "section4"))
    val elements = workbook.allChildrenFullSubtree
    val experiments = elements.collect { case e: CompressionExperimentInteraction => e }
    assertEquals(experiments.size, 9)
    assertEquals(experiments.count(_.initial.isInstanceOf[RunLengthText]), 2)
    assertEquals(experiments.count(_.initial.isInstanceOf[ImageBlocks]), 2)
    assertEquals(elements.count(_.isInstanceOf[BinaryPixelInteraction]), 1)
    assert(elements.collect { case e: SortingReasonInteraction => e }.exists(_.elementId == "compression-efficiency"))
    assert(elements.count(_.isInstanceOf[TextInteraction]) >= 35)
    assertEquals(elements.map(_.elementId).distinct.size, elements.size)
  }
  test("complete compression workbook round-trips both definition formats") {
    val workbook = CreateCompressionWorkbook(null).createWorkbook
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook)
  }
  test("storage packages have independent selections and start above the card capacity") {
    val study = CreateCompressionWorkbook.storageStudy
    assert(study.packages.forall(_.megabytes > study.capacityMB))
    assertEquals(study.copy(selected = 1).current.label, it.evadid.core.datastructures.language.LanguageMapContentId("CompressionWorkbook/packageLogs"))
    assertEquals(study.selected, 0)
  }
}
