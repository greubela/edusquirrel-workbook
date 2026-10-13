package it.evadid.workbook.model.feedback

/** Serializable expected/actual text prepared for a feedback consumer. */
final case class ExpectedActual(
    expectedLabel: String,
    expected: String,
    actualLabel: String,
    actual: String
  ) derives upickle.default.ReadWriter
