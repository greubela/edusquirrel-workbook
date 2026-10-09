package it.evadid.workbook.elements.interactionElements.blockchain

import it.evadid.workbook.model.blockchain.*
import munit.FunSuite

class TeachingLedgerSpec extends FunSuite {
  private val initial = Map("Anna" -> BigInt(10), "Tom" -> BigInt(10))
  test("empty history preserves initial issuance") {
    assertEquals(TeachingLedger.calculate(initial, Nil), Right(initial))
  }
  test("transfers conserve points and may spend the whole balance") {
    val transfers = List(LedgerTransfer("Anna", "Tom", 10), LedgerTransfer("Tom", "Anna", 20))
    assertEquals(TeachingLedger.calculate(initial, transfers), Right(Map("Anna" -> BigInt(20), "Tom" -> BigInt(0))))
    assertEquals(initial("Anna"), BigInt(10))
  }
  test("overspending is rejected at the time it occurs, even if later income would cover it") {
    val transfers = List(LedgerTransfer("Anna", "Tom", 11), LedgerTransfer("Tom", "Anna", 10))
    assertEquals(TeachingLedger.calculate(initial, transfers), Left(LedgerError.InsufficientFunds))
  }
  test("unknown parties do not create accounts or new points") {
    for (transfer <- List(LedgerTransfer("Unknown", "Tom", 1), LedgerTransfer("Anna", "Unknown", 1)))
      assertEquals(TeachingLedger.calculate(initial, List(transfer)), Left(LedgerError.UnknownAccount))
  }
  test("zero, negative and self transfers are invalid") {
    for (amount <- List(BigInt(0), BigInt(-1)))
      assertEquals(TeachingLedger.calculate(initial, List(LedgerTransfer("Anna", "Tom", amount))), Left(LedgerError.InvalidAmount))
    assertEquals(TeachingLedger.calculate(initial, List(LedgerTransfer("Anna", "Anna", 1))), Left(LedgerError.SelfTransfer))
  }
  test("initial issuance requires named accounts with nonnegative integer balances") {
    for (balances <- List(Map.empty[String, BigInt], Map("" -> BigInt(1)), Map(" Anna" -> BigInt(1)), Map("Anna" -> BigInt(-1))))
      assertEquals(TeachingLedger.calculate(balances, Nil), Left(LedgerError.InvalidInitialBalances))
    assertEquals(TeachingLedger.calculate(Map("Anna" -> BigInt(0)), Nil), Right(Map("Anna" -> BigInt(0))))
  }
  test("large balances remain exact on JVM and JavaScript") {
    val amount = BigInt(10).pow(80)
    assertEquals(TeachingLedger.calculate(Map("Anna" -> amount, "Tom" -> BigInt(0)), List(LedgerTransfer("Anna", "Tom", amount))),
      Right(Map("Anna" -> BigInt(0), "Tom" -> amount)))
  }
}
