package it.evadid.core.datastructures.font

import munit.FunSuite

class AppFontJvmTest extends FunSuite {
  test("JVM text measurement returns useful dimensions") {
    val dimensions = AppFont("Dialog", 16).measureText("EduSquirrel")

    assert(dimensions.width > 0.0)
    assert(dimensions.height > 0.0)
  }

  test("JVM text width grows with font size") {
    val small = AppFont("Dialog", 12).measureText("workbook")
    val large = AppFont("Dialog", 24).measureText("workbook")

    assert(large.width > small.width)
    assert(large.height > small.height)
  }
}
