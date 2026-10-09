package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.blockchain.*
import it.evadid.workbook.elements.interactionElements.table.TableAnswer
import it.evadid.workbook.elements.interactionElements.choice.ChoiceAnswer
import it.evadid.workbook.model.blockchain.*
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
  test("original cabin history conserves 40 points and derives all four balances") {
    assertEquals(CreateBlockchainWorkbook.sourceBalances, Map("Anna" -> BigInt(0), "Lukas" -> BigInt(20), "Sara" -> BigInt(15), "Tom" -> BigInt(5)))
    assertEquals(CreateBlockchainWorkbook.sourceBalances.values.sum, BigInt(40))
    val table = CreateBlockchainWorkbook.balanceTable
    assert(table.grade(TableAnswer(List("0", "20", "15", "5"))).get.passed)
    assertEquals(table.grade(TableAnswer(List("4", "20", "15", "5"))).get.correct, 3)
    assertEquals(TeachingLedger.calculate(CreateBlockchainWorkbook.participants.map(_ -> BigInt(10)).toMap,
      CreateBlockchainWorkbook.sourceTransfers :+ LedgerTransfer("Anna", "Tom", 4)), Left(LedgerError.InsufficientFunds))
  }
  test("trust and privacy research tables have no invented automatic grades") {
    for (table <- List(CreateBlockchainWorkbook.paymentComparison, CreateBlockchainWorkbook.paymentArguments,
      CreateBlockchainWorkbook.storageComparison, CreateBlockchainWorkbook.bitcoinLedger, CreateBlockchainWorkbook.bitcoinPrivacy)) {
      assertEquals(table.grade(table.defaultValue), None)
      assert(table.editableCells.nonEmpty)
    }
  }
  test("Anna's overspend question has an objective answer") {
    val choice = CreateBlockchainWorkbook(null).createWorkbook.allChildrenFullSubtree.collectFirst {
      case c: it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction if c.elementId == "blockchain-ledger-overspend" => c
    }.get
    assertEquals(choice.isCorrect(ChoiceAnswer(List(1))), Some(true))
    assertEquals(choice.isCorrect(ChoiceAnswer(List(0))), Some(false))
  }
  test("SHA-256 chapter preserves the source comparison and manual prefix challenge") {
    val interactions = CreateBlockchainWorkbook(null).createWorkbook.allChildrenFullSubtree.collect {
      case s: Sha256Interaction => s
    }
    assertEquals(interactions.map(_.task), List(CompareSha256(), FindSha256Prefix(1), FindSha256Prefix(2)))
    assertEquals(interactions.head.initial, Sha256Answer("Informatik", "informatik"))
    assertEquals(interactions.head.isCorrect(interactions.head.initial), None)
    assertEquals(interactions(1).isCorrect(Sha256Answer("39")), Some(true))
    assertEquals(interactions(2).isCorrect(Sha256Answer("39")), Some(false))
  }
  test("256-bit hash space accepts powers and the exact decimal expansion") {
    val table = CreateBlockchainWorkbook.hashSpaceTable
    for (answer <- List("2^256", "2**256", "2²⁵⁶", BigInt(2).pow(256).toString))
      assert(table.grade(TableAnswer(List(answer))).get.passed)
    for (answer <- List("256", "512", "16^256", ""))
      assert(!table.grade(TableAnswer(List(answer))).get.passed)
  }
}
