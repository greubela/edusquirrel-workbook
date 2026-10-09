package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.blockchain.*
import it.evadid.workbook.elements.interactionElements.table.TableAnswer
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class CreateBlockchainWorkbookSpec extends FunSuite {
  test("partial workbook round-trips both formats with unique ids") {
    val workbook = CreateBlockchainWorkbook(null).createWorkbook
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook)
    val ids = workbook.allChildrenFullSubtree.map(_.elementId)
    assertEquals(ids.distinct.size, ids.size)
  }
  test("source claims remain ungraded and allow two learner-authored claims") {
    val table = CreateBlockchainWorkbook.claimsTable
    assertEquals(table.editableCells.size, 12)
    assertEquals(table.grade(table.defaultValue), None)
    assertEquals(table.rows.take(3).map(_.head.getClass.getSimpleName), List.fill(3)("FixedTableCell"))
    assert(table.rows.drop(3).forall(_.forall(_.isInstanceOf[it.evadid.workbook.elements.interactionElements.table.EditableTableCell])))
  }
  test("source calculations grade all six square and hash answers") {
    val table = CreateBlockchainWorkbook.hashTable
    assert(table.grade(TableAnswer(List("1764", "76", "9801", "80", "169", "16"))).get.passed)
    assertEquals(table.grade(table.defaultValue).get.correct, 0)
  }
  test("calculator, collision and both original target hashes are authored") {
    val tasks = CreateBlockchainWorkbook(null).createWorkbook.allChildrenFullSubtree.collect {
      case e: SquareMiddleHashInteraction => e.task
    }
    assertEquals(tasks, List(ExploreSquareMiddleHash(), FindHashCollision(), FindHashPreimage("22"), FindHashPreimage("77")))
  }
}
