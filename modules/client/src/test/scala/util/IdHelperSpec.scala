package util

import munit.FunSuite

class IdHelperSpec extends FunSuite {

  test("getNextId returns prefixed ids") {
    val id = IdHelper.getNextId()
    assert(id.startsWith("IdHelper-"))
  }

  test("getNextId increments ids") {
    val first = IdHelper.getNextId()
    val second = IdHelper.getNextId()

    val firstNumber = first.stripPrefix("IdHelper-").toInt
    val secondNumber = second.stripPrefix("IdHelper-").toInt

    assertEquals(secondNumber, firstNumber + 1)
  }
}
