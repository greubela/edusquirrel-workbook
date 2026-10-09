package it.evadid.workbook.elements.interactionElements.blockchain

import it.evadid.workbook.model.blockchain.MiningEnergyEstimate
import munit.FunSuite

class MiningEnergyEstimateSpec extends FunSuite {
  private val source = MiningEnergyEstimate(BigDecimal("173000000000"), 4000, 600)
  test("historical quantities convert exactly without double arithmetic") {
    assertEquals(MiningEnergyEstimate.terawattHoursToKilowattHours(BigDecimal(173)), BigDecimal("173000000000"))
    assertEquals(MiningEnergyEstimate.megatonnesToKilograms(BigDecimal(86)), BigDecimal("86000000000"))
    assertEquals(MiningEnergyEstimate.cubicKilometresToLitres(BigDecimal("1.65")), BigDecimal("1650000000000"))
  }
  test("explicit 365-day classroom scenario calculates throughput and average allocation") {
    assertEquals(source.blocksPerYear, BigDecimal(52560))
    assertEquals(source.transactionsPerYear, BigDecimal(210240000))
    assertEquals(MiningEnergyEstimate.rounded(source.transactionsPerSecond), "6.67")
    assertEquals(MiningEnergyEstimate.rounded(source.allocatedKilowattHoursPerTransaction), "822.87")
  }
  test("changing the period or throughput changes allocation, not the assumed annual energy") {
    val doubled = source.copy(transactionsPerBlock = 8000)
    assertEquals(doubled.transactionsPerYear, source.transactionsPerYear * 2)
    assertEquals(MiningEnergyEstimate.rounded(doubled.allocatedKilowattHoursPerTransaction), "411.43")
    val leap = source.copy(daysPerYear = 366)
    assertEquals(leap.blocksPerYear, BigDecimal(52704))
    assert(leap.allocatedKilowattHoursPerTransaction < source.allocatedKilowattHoursPerTransaction)
  }
  test("invalid denominators and negative quantities are rejected; zero energy is meaningful") {
    for (scenario <- List(() => source.copy(annualKilowattHours = -1), () => source.copy(transactionsPerBlock = 0),
      () => source.copy(blockSeconds = 0), () => source.copy(daysPerYear = 0)))
      intercept[IllegalArgumentException](scenario())
    for (convert <- List(MiningEnergyEstimate.terawattHoursToKilowattHours, MiningEnergyEstimate.megatonnesToKilograms,
      MiningEnergyEstimate.cubicKilometresToLitres)) intercept[IllegalArgumentException](convert(BigDecimal(-1)))
    assertEquals(source.copy(annualKilowattHours = 0).allocatedKilowattHoursPerTransaction, BigDecimal(0))
  }
}
