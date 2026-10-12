package it.evadid.workbook.model.qr

import upickle.default.ReadWriter

/** Semantic purpose of each module, independent of its color and masked dark/light value.
  * Format modules store the selected mask and error-correction level. The mask itself
  * is applied across all non-function modules, not stored in a separate data rectangle.
  */
enum QrCodeRegion derives ReadWriter {
  case Finder, Separator, Timing, Alignment, Format, Version, FixedDark
  case Encoding, Data, ErrorCorrection, Remainder
}

private[qr] case class QrCodeSymbol(modules: Vector[Vector[Boolean]], regions: Vector[Vector[QrCodeRegion]]) derives ReadWriter
