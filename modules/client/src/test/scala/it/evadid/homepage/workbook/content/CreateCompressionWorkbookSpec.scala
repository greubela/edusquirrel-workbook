package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.Slideshow
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.SortingReasonInteraction
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.model.compression.*

class CreateCompressionWorkbookSpec extends munit.FunSuite {
  test("all seven original chapters, every inventoried activity, answer and reflection are native") {
    val workbook = CreateCompressionWorkbook(null).createWorkbook
    assertEquals(workbook.sections.map(_.elementId), List("intro","section0","lossless","lossless-optional","lossy","filetypes","final"))
    val elements = workbook.allChildrenFullSubtree
    val experiments = elements.collect { case e: CompressionExperimentInteraction => e }
    assertEquals(experiments.count(_.initial.isInstanceOf[WrittenAnswer]), 41)
    assertEquals(experiments.count(_.initial.isInstanceOf[EthicalReflection]), 1)
    assertEquals(experiments.count(_.initial.isInstanceOf[PhotoBudget]), 1)
    assertEquals(experiments.count(_.initial.isInstanceOf[FileSimulation]), 4)
    assertEquals(experiments.count(_.initial.isInstanceOf[ImageBlocks]), 3)
    assertEquals(elements.count(_.isInstanceOf[ChoiceInteraction]), 7)
    assertEquals(elements.collect { case e: Slideshow => e }.head.panels.size, 6)
    assertEquals(elements.count(_.isInstanceOf[SortingReasonInteraction]), 2)
    val inventory = CompressionSourceContent.sections.flatMap(_.blocks).flatMap(_.nodes)
    assert(inventory.filter(n => Set("widget","answer","ethics","choice","hint","compare")(n.kind)).forall(n => elements.exists(_.elementId == n.id)))
    assertEquals(elements.map(_.elementId).distinct.size, elements.size)
  }
  test("complete workbook round-trips JSON and constructor definitions") {
    val workbook = CreateCompressionWorkbook(null).createWorkbook
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook)
  }
  test("source scenarios preserve logical file counts, sizes, metadata and independent state") {
    val scenarios = CompressionSourceData.scenarios
    assertEquals(scenarios.map(_.scenario), List("s0","s1","s2","s3"))
    assertEquals(scenarios.head.files.head.bytes, 18000L * 1024 * 1024)
    assertEquals(scenarios(2).files.take(42).map(_.count).sum,4200)
    assert(scenarios.forall(_.bytes > CompressionSimulation.capacity))
    assertEquals(scenarios(3).files.count(_.kind == "encrypted"),3)
    val changed = scenarios.head.choose("lossy").click(0)
    assert(changed.bytes < scenarios.head.bytes)
    assert(changed.hasProblematicMetadata)
    assert(changed.choose("convert").click(0).choose("convert").click(2).complete)
    assertEquals(scenarios.head.steps,0)
    assertEquals(CompressionSourceData.archiveFiles.size,10)
    assert(CreateCompressionWorkbook.sampleText.endsWith("ist nicht vorgesehen."))
  }
}
