package it.evadid.homepage.webElements.editor.qr

import munit.FunSuite
import it.evadid.workbook.model.qr.{QrCode, QrCodeRegion}

class QrCodeViewSpec extends FunSuite {
  test("region paths partition dark modules without overlaps or altered geometry") {
    val code = QrCode.fromText("QR regions", version = Some(7))
    val paths = QrCodeRegion.values.toVector.map(region => QrCodeView.pathData(code, Some(region)))
    val cells = paths.flatMap(path => "M([0-9]+),([0-9]+)h1v1h-1z".r.findAllMatchIn(path).map(m =>
      (m.group(1).toInt - 4, m.group(2).toInt - 4)))
    assertEquals(cells.size, cells.distinct.size)
    assertEquals(cells.size, code.modules.map(_.count(identity)).sum)
    QrCodeRegion.values.foreach { region =>
      "M([0-9]+),([0-9]+)h1v1h-1z".r.findAllMatchIn(QrCodeView.pathData(code, Some(region))).foreach { m =>
        assertEquals(code.regionAt(m.group(1).toInt - 4, m.group(2).toInt - 4), region)
      }
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
