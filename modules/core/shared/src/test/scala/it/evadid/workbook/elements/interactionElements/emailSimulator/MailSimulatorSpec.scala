package it.evadid.workbook.elements.interactionElements.emailSimulator

import munit.FunSuite
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

class MailSimulatorSpec extends FunSuite {
  private val mail = Mail("1", "sender@example.com", "Sender", "Subject", "Body", MailFolder.Inbox, None, "date",
    expectedFolder = Some(MailFolder.Trash))
  private val other = mail.copy(id = "2", expectedFolder = Some(MailFolder.Marked))
  test("every folder transition has exclusive and idempotent membership") {
    for (from <- MailFolder.all; to <- MailFolder.all) {
      val start = InboxState.withMails(List(mail.copy(realFolder = from), other))
      val moved = start.moveMail("1", to)
      assertEquals(moved.mailList.map(_.id), List("1", "2"))
      assertEquals(moved.mailFolders.values.flatten.count(_ == "1"), 1)
      assertEquals(moved.getMailsInFolder(to).count(_.id == "1"), 1)
      assertEquals(moved.moveMail("1", to), moved)
      assertEquals(moved.getMailById("1").get.expectedFolder, mail.expectedFolder)
    }
  }
  test("unknown folders and IDs are no-ops") {
    val state = InboxState.withMails(List(mail))
    assertEquals(state.moveMail("1", "Unknown"), state)
    assertEquals(state.moveMail("missing", MailFolder.Trash), state)
    assertEquals(state.updateMail(mail.copy(realFolder = "Unknown")), state)
  }
  test("read and updates preserve ordering and folder membership") {
    val state = InboxState.withMails(List(mail, other)).archiveMail("1").markAsRead("1")
    assertEquals(state.mailList.map(_.id), List("1", "2"))
    assertEquals(state.getMailsInFolder(MailFolder.Archive).head.read, true)
    assertEquals(state.updateMail(mail.copy(realFolder = MailFolder.Archive, subject = "changed")).mailList.head.subject, "changed")
  }
  test("normalization repairs stale duplicate indexes using message placement") {
    val bad = InboxState(List(mail.copy(realFolder = MailFolder.Trash)), Map(MailFolder.Inbox -> List("1", "1", "ghost")))
    assertEquals(bad.getMailsInFolder(MailFolder.Inbox), Nil)
    assertEquals(bad.normalized.mailFolders(MailFolder.Trash), List("1"))
    assertEquals(bad.normalized.mailFolders.values.flatten.toList, List("1"))
  }
  test("reject duplicate IDs, invalid placement and invalid answer folders") {
    intercept[IllegalArgumentException](InboxState.withMails(List(mail, mail)))
    intercept[IllegalArgumentException](InboxState.withMails(List(mail.copy(realFolder = "unknown"))))
    intercept[IllegalArgumentException](InboxState.withMails(List(mail.copy(expectedFolder = Some(MailFolder.Sent)))))
  }
  test("grading uses final placement and excludes local practice mail") {
    val start = InboxState.withMails(List(mail, other, mail.copy(id = "sent", realFolder = MailFolder.Sent, expectedFolder = None)))
    assertEquals(start.sortingResult, MailSortingResult(2, 0, 0))
    assert(!InboxState.empty.sortingResult.passed)
    val wrong = start.archiveMail("1").markMail("2")
    assert(wrong.sortingResult.completed); assert(!wrong.sortingResult.passed)
    val right = wrong.deleteMail("1")
    assert(right.sortingResult.passed)
    assertEquals(right.deleteMail("1").sortingResult, MailSortingResult(2, 2, 2))
    assert(!right.moveMail("1", MailFolder.Inbox).sortingResult.completed)
  }
  test("draft validation catches missing and malformed inputs") {
    for (to <- List("", "abc", "a@b", "a@b.com,", "a@b.com;invalid", "a@b.com\nX", "Name <a@b.com>"))
      assertEquals(MailDraft(to, "subject", "body").validationError, Some("invalidRecipient"))
    assertEquals(MailDraft("a@b.com", " ", "body").validationError, Some("missingSubject"))
    assertEquals(MailDraft("a@b.com", "subject", " ").validationError, Some("missingBody"))
    assertEquals(MailDraft("opa.jürgen@gmail.com; a@b.com", "s", "b").validationError, None)
  }
  test("send validates, creates a local plain-text sent message and prevents ID reuse") {
    val state = InboxState.withMails(List(mail))
    assert(state.sendDraft(MailDraft(), "new", "me@example.com", "date").isLeft)
    val d = MailDraft("a@b.com, c@d.org", " subject ", "<script>plain text</script>")
    assert(state.sendDraft(d, "1", "me@example.com", "date").isLeft)
    val sent = state.sendDraft(d, "new", "me@example.com", "date").toOption.get
    val m = sent.getMailById("new").get
    assertEquals(m.realFolder, MailFolder.Sent); assertEquals(m.subject, "subject")
    assertEquals(m.recipients, List("a@b.com", "c@d.org")); assert(!m.bodyHtml)
    assertEquals(sent.sortingResult, state.sortingResult)
    assertEquals(upickle.default.read[InboxState](upickle.default.write(sent)), sent)
  }
  test("reply and forward preserve context without repeating subject prefixes") {
    val attached = mail.copy(subject = "Re: Hello", attachment = Some("invoice.pdf.exe"))
    val reply = MailDraft.reply(attached, "quoted")
    assertEquals(reply.to, attached.sender); assertEquals(reply.subject, "Re: Hello")
    assertEquals(reply.attachment, None); assert(reply.body.contains("quoted"))
    val forward = MailDraft.forward(attached.copy(subject = "Fwd: Hello"), "quoted")
    assertEquals(forward.to, ""); assertEquals(forward.subject, "Fwd: Hello")
    assertEquals(forward.attachment, attached.attachment)
  }
  test("registered workbook JSON serialization retains initial inbox and configuration") {
    val inbox = InboxState.withMails(List(mail, other))
    val elements = List(MailInteraction("interaction", inbox, "a@b.com", true), MailEditor("editor", inbox, "c@d.com", false))
    for (element <- elements; serializer <- List(WorkbookElementFactory.serializerRefBasedJson))
      assertEquals(serializer.deserialize(serializer.serialize(element)), element)
  }
  test("legacy element definitions default to empty inbox and disabled composing") {
    val legacy = it.evadid.workbook.jsonFactory.WorkbookElementSerializable("mail", "MailInteraction", Map.empty)
    assertEquals(WorkbookElementFactory.parse(legacy), MailInteraction("mail"))
  }
}
