package it.evadid.homepage.webElements.editor.qr

import munit.FunSuite
import it.evadid.workbook.model.qr.{QrCode, QrCodeRegion, QrErrorCorrection}

class QrCodeViewSpec extends FunSuite {
  test("region paths partition dark modules for all 40 versions and every correction level") {
    val cellPattern = "M([0-9]+),([0-9]+)h1v1h-1z".r
    for version <- 1 to 40; ecc <- QrErrorCorrection.values do {
      val code = QrCode.fromText("ä", ecc, version = Some(version), mask = Some((version + ecc.ordinal) % 8))
      val cells = scala.collection.mutable.HashSet.empty[(Int, Int)]
      QrCodeRegion.values.foreach { region =>
        val path = QrCodeView.pathData(code, Some(region))
        cellPattern.findAllMatchIn(path).foreach { m =>
          val x = m.group(1).toInt - 4
          val y = m.group(2).toInt - 4
          assert(x >= 0 && y >= 0 && x < code.size && y < code.size, s"quiet zone v$version $ecc")
          assert(cells.add((x, y)), s"overlapping paths v$version $ecc ($x,$y)")
          assert(code.isDark(x, y), s"light module rendered v$version $ecc ($x,$y)")
          assertEquals(code.regionAt(x, y), region, s"wrong color region v$version $ecc ($x,$y)")
        }
      }
      assertEquals(cells.size, code.modules.map(_.count(identity)).sum, s"missing dark modules v$version $ecc")
    }
    assertEquals(QrCodeView.regionClass(QrCodeRegion.ErrorCorrection), "qr-region--error-correction")
  }
  test("SVG paths reproduce every dark module and preserve the four-module quiet zone") {
    val code = QrCode.fromText("Grüße 🐿")
    val path = QrCodeView.pathData(code)
    val cells = "M([0-9]+),([0-9]+)h1v1h-1z".r.findAllMatchIn(path).map(m =>
      (m.group(1).toInt, m.group(2).toInt)).toSet
    assertEquals(cells.size, code.modules.map(_.count(identity)).sum)
    for y <- 0 until code.size; x <- 0 until code.size do
      assertEquals(cells.contains((x + 4, y + 4)), code.isDark(x, y))
    assert(cells.forall((x, y) => x >= 4 && y >= 4 && x < code.size + 4 && y < code.size + 4))
  }
}
