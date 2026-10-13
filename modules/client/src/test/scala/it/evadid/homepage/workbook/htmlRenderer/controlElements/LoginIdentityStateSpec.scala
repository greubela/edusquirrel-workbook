package it.evadid.homepage.workbook.htmlRenderer.controlElements

import munit.FunSuite

class LoginIdentityStateSpec extends FunSuite {
  test("name edits generate and update the suggested email") {
    val state = new LoginIdentityState("", "")
    state.changeName("First Last")
    assertEquals(state.email.now(), "first.last@student.hu-berlin.de")
    state.changeName("Other Name")
    assertEquals(state.email.now(), "other.name@student.hu-berlin.de")
    state.changeName("Other")
    assertEquals(state.email.now(), "")
  }

  test("an entered email is preserved and never rewrites the name") {
    val state = new LoginIdentityState("", "")
    state.changeName("First Last")
    state.changeEmail("personal.address@example.org")
    assertEquals(state.name.now(), "First Last")
    state.changeName("Other Name")
    assertEquals(state.email.now(), "personal.address@example.org")
  }

  test("restored addresses are preserved even if they match the old suggestion") {
    val state = new LoginIdentityState("First Last", "first.last@student.hu-berlin.de")
    state.changeName("Other Name")
    assertEquals(state.email.now(), "first.last@student.hu-berlin.de")
  }

  test("clearing an entered email allows suggestions on the next name edit") {
    val state = new LoginIdentityState("First Last", "personal@example.org")
    state.changeEmail("")
    state.changeName("Other Name")
    assertEquals(state.email.now(), "other.name@student.hu-berlin.de")
  }
}
