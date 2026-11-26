package it.evadid.workbook.model.blockchain

/** Arithmetic under explicit classroom assumptions, not a measurement of a payment's marginal energy. */
case class MiningEnergyEstimate(annualKilowattHours: BigDecimal, transactionsPerBlock: Int, blockSeconds: Int, daysPerYear: Int = 365) {
  require(annualKilowattHours >= 0 && transactionsPerBlock > 0 && blockSeconds > 0 && daysPerYear > 0)
  val blocksPerYear: BigDecimal = BigDecimal(daysPerYear) * 86400 / blockSeconds
  val transactionsPerYear: BigDecimal = blocksPerYear * transactionsPerBlock
  val transactionsPerSecond: BigDecimal = BigDecimal(transactionsPerBlock) / blockSeconds
  val allocatedKilowattHoursPerTransaction: BigDecimal = annualKilowattHours / transactionsPerYear
}
object MiningEnergyEstimate {
  private def nonnegative(value: BigDecimal): BigDecimal = { require(value >= 0); value }
  def terawattHoursToKilowattHours(value: BigDecimal): BigDecimal = nonnegative(value) * BigDecimal(10).pow(9)
  def megatonnesToKilograms(value: BigDecimal): BigDecimal = nonnegative(value) * BigDecimal(10).pow(9)
  def cubicKilometresToLitres(value: BigDecimal): BigDecimal = nonnegative(value) * BigDecimal(10).pow(12)
  def rounded(value: BigDecimal): String = value.setScale(2, BigDecimal.RoundingMode.HALF_UP).toString
}
