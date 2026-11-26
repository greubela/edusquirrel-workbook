package it.evadid.workbook.elements.interactionElements.emailSimulator

import munit.FunSuite

class InboxStateSpec extends FunSuite {

  test("empty inbox has empty mailList and default folders") {
    val state = InboxState.empty

    assertEquals(state.mailList, List())
    assertEquals(state.mailFolders.size, 5)
    assertEquals(state.mailFolders("Posteingang"), List())
    assertEquals(state.mailFolders("Gesendet"), List())
    assertEquals(state.mailFolders("Markiert"), List())
    assertEquals(state.mailFolders("Archiv"), List())
    assertEquals(state.mailFolders("Papierkorb"), List())
  }

  test("withMails creates inbox with mails in posteingang folder") {
    val mail1 = Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01")
    val mail2 = Mail("2", "sender2@example.com", "Sender Two", "Subject 2", "Body 2", "Posteingang", None, "2024-01-02")

    val state = InboxState.withMails(List(mail1, mail2))

    assertEquals(state.mailList.size, 2)
    assertEquals(state.getMailsInFolder("Posteingang").size, 2)
    assertEquals(state.getMailsInFolder("Archiv").size, 0)
  }

  test("getMailsInFolder returns mails for a folder") {
    val state = InboxState(
      mailList = List(
        Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01"),
        Mail("2", "sender2@example.com", "Sender Two", "Subject 2", "Body 2", "Posteingang", None, "2024-01-02"),
        Mail("3", "sender3@example.com", "Sender Three", "Subject 3", "Body 3", "Archiv", None, "2024-01-03")
      ),
      mailFolders = Map(
        "Posteingang" -> List("1", "2"),
        "Gesendet" -> List(),
        "Markiert" -> List(),
        "Archiv" -> List("3"),
        "Papierkorb" -> List()
      )
    )

    val posteingangMails = state.getMailsInFolder("Posteingang")
    assertEquals(posteingangMails.size, 2)
    assert(posteingangMails.exists(_.id == "1"))
    assert(posteingangMails.exists(_.id == "2"))

    val archivMails = state.getMailsInFolder("Archiv")
    assertEquals(archivMails.size, 1)
    assertEquals(archivMails.head.id, "3")
  }

  test("getMailById returns correct mail") {
    val mail = Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01")
    val state = InboxState.withMails(List(mail))

    val found = state.getMailById("1")
    assertEquals(found, Some(mail))

    val notFound = state.getMailById("999")
    assertEquals(notFound, None)
  }

  test("markAsUnread marks mail as unread") {
    val mail = Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01", read = true)
    val state = InboxState.withMails(List(mail))

    val updatedState = state.markAsUnread("1")

    assertEquals(updatedState.getMailById("1").map(_.read), Some(false))
  }

  test("unmarkMail removes mail from starred folder") {
    val state = InboxState(
      mailList = List(
        Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01")
      ),
      mailFolders = Map(
        "Posteingang" -> List("1"),
        "Gesendet" -> List(),
        "Markiert" -> List("1"),
        "Archiv" -> List(),
        "Papierkorb" -> List()
      )
    )

    val unmarkedState = state.unmarkMail("1")

    assertEquals(unmarkedState.getMailsInFolder("Markiert").size, 0)
  }

  test("markAsRead handles non-existent mail") {
    val state = InboxState.empty

    val result = state.markAsRead("nonexistent")

    assertEquals(result, state)
  }

  test("markAsUnread handles non-existent mail") {
    val state = InboxState.empty

    val result = state.markAsUnread("nonexistent")

    assertEquals(result, state)
  }

  test("unmarkMail handles non-existent mail") {
    val state = InboxState.empty

    val result = state.unmarkMail("nonexistent")

    assertEquals(result, state)
  }

  test("getMailsInFolder handles non-existent folder") {
    val state = InboxState.empty

    val result = state.getMailsInFolder("NonExistent")

    assertEquals(result, List())
  }
}

