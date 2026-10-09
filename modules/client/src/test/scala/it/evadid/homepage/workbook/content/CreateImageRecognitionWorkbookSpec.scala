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
  test("initial opinion is ungraded; knowledge questions support single and multiple choice") {
    val choices = CreateImageRecognitionWorkbook(null).createWorkbook.allChildrenFullSubtree.collect { case c: ChoiceInteraction => c }
    assertEquals(choices.size, 3)
    assertEquals(choices.count(_.expected.isEmpty), 1)
    assertEquals(choices.count(_.allowMultiple), 1)
  }
}
