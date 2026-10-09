package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.neuron.*
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.elements.interactionElements.table.*
import munit.FunSuite

class CreateImageRecognitionWorkbookSpec extends FunSuite {
  test("online image chapter survives both workbook serialization formats") {
    val workbook = CreateImageRecognitionWorkbook(null).createWorkbook
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook)
    val ids = workbook.allChildrenFullSubtree.map(_.elementId)
    assertEquals(ids.distinct.size, ids.size)
  }
  test("source week sums are 3,1,3,2,4 and a valid solution visits Wednesday and Friday") {
    val e = CreateImageRecognitionWorkbook.poolExercise
    assertEquals(e.examples.map(row => e.initial.weightedSum(row.inputs)), List(3.0, 1.0, 3.0, 2.0, 4.0))
    assertEquals(e.examples.map(_.expected), List(false, false, true, false, true))
    assert(!e.isPassed(e.initial)); assert(e.isPassed(NeuronParameters(List(1, 1, 0, 2), 4)))
  }
  test("binary table gives Monday and checks all sixteen remaining source-week inputs") {
    val table = CreateImageRecognitionWorkbook.binaryTable
    val week = CreateImageRecognitionWorkbook.poolExercise
    assertEquals(table.editableCells.size, 16)
    assert(table.rows.forall(_.head.isInstanceOf[FixedTableCell]))
    val solution = TableAnswer(week.inputLabels.indices.toList.flatMap(input =>
      week.examples.tail.map(_.inputs(input).toInt.toString)))
    assert(table.isAnswered(solution))
    assertEquals(table.grade(solution), Some(TableGrade(16, 16)))
    assertEquals(table.grade(solution.copy(values = solution.values.updated(0, "1"))), Some(TableGrade(15, 16)))
  }
  test("all ten source digits yield the worksheet top/middle detector groups") {
    val e = CreateImageRecognitionWorkbook.pixelExperiment
    assertEquals(e.presets.size, 10)
    assertEquals(e.presets.map(p => e.outputs(p.image)), List(
      List(true, false), List(false, false), List(true, true), List(true, true), List(false, true),
      List(true, true), List(false, true), List(true, false), List(true, true), List(true, true)))
    assert(e.presets.forall(p => p.image.rows == 5 && p.image.columns == 3))
    val recreation = CreateImageRecognitionWorkbook.pixelRecreation
    assertEquals(recreation.isCorrect(e.presets(7).image), Some(true))
    assertEquals(recreation.matchingPixels(recreation.initial), Some(8))
    assertEquals(e.isCorrect(e.initial), None)
  }
  test("initial opinion is ungraded; knowledge questions support single and multiple choice") {
    val choices = CreateImageRecognitionWorkbook(null).createWorkbook.allChildrenFullSubtree.collect { case c: ChoiceInteraction => c }
    assertEquals(choices.size, 4)
    assertEquals(choices.count(_.expected.isEmpty), 1)
    assertEquals(choices.count(_.allowMultiple), 1)
  }
  test("one-pixel detector separates the source 1 but fails on the disclosed variants") {
    val factory = CreateImageRecognitionWorkbook
    val e = factory.pixelRobustness
    assertEquals(e.outputs(factory.digitImages(1)), List(true))
    assertEquals(factory.digitImages.map(image => e.outputs(image).head), (0 until 10).map(_ == 1).toList)
    assertEquals(e.outputs(factory.oneWithoutFeature), List(false))
    assertEquals(e.outputs(factory.invertedOne), List(false))
    assertEquals(e.outputs(factory.digitImages(1).toggle(it.evadid.workbook.model.pixel.PixelPosition(0, 0))), List(true))
    val nonDigit = it.evadid.workbook.model.pixel.BinaryPixelImage.blank(5, 3)
      .toggle(it.evadid.workbook.model.pixel.PixelPosition(1, 1))
    assertEquals(e.outputs(nonDigit), List(true))
    assertEquals(e.isCorrect(e.initial), None)
    assertEquals(e.presets.size, 13)
  }
  test("research and school recommendations remain ungraded and preserve earlier chapters") {
    val factory = CreateImageRecognitionWorkbook
    val tables = List(factory.robustnessObservations, factory.chatbotResearch, factory.chatbotAssessment)
    assertEquals(tables.map(_.editableCells.size), List(9, 10, 12))
    tables.foreach { table =>
      val answer = TableAnswer(List.fill(table.editableCells.size)("Evidence, source and reason"))
      assert(table.isAnswered(answer))
      assertEquals(table.grade(answer), None)
      assertEquals(table.serializerInteractionContent.deserialize(table.serializerInteractionContent.serialize(answer)), answer)
    }
    assertEquals(factory(null).createWorkbook.sections.map(_.elementId), List(
      "image-introduction", "image-threshold-neuron", "image-binary-pixels", "image-generalization", "image-school-chatbots"))
    assertEquals(factory.binaryTable.editableCells.size, 16)
  }
}
