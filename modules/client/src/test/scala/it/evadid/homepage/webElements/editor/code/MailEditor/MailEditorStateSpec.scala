package it.evadid.homepage.webElements.editor.code.MailEditor

import com.raquo.laminar.api.L.*
import it.evadid.workbook.elements.interactionElements.emailSimulator.*
import it.evadid.homepage.workbook.content.{PhishingMailboxData, CreatePhishingWorkbook}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class MailEditorStateSpec extends FunSuite {
  private def controller(compose: Boolean = true): MailEditorState = new MailEditorState(
    Var(InboxStateScaffolding(PhishingMailboxData.initialInbox)), compose)
  test("selection reads a message; navigation clears selection without changing mail order") {
    val c = controller(); val ids = c.state.now().inboxState.mailList.map(_.id)
    c.selectMail(ids.head)
    assert(c.selectedNow.get.read)
    assertEquals(c.state.now().inboxState.mailList.map(_.id), ids)
    c.selectFolder(MailFolder.Trash); assertEquals(c.selectedNow, None)
    c.selectMail(ids.head); assertEquals(c.selection.now(), None)
    c.selectFolder("invalid"); assertEquals(c.folder.now(), MailFolder.Trash)
  }
  test("archive, mark, trash, unread and restore persist through the bound state") {
    val c = controller(); val id = c.state.now().inboxState.mailList.head.id
    for (target <- List(MailFolder.Archive, MailFolder.Marked, MailFolder.Trash, MailFolder.Inbox)) {
      c.selectMail(id); c.unread(); c.moveSelected(target)
      assertEquals(c.selection.now(), None)
      c.selectFolder(target); c.selectMail(id)
      assertEquals(c.selectedNow.get.realFolder, target)
    }
    val restored = upickle.default.read[InboxStateScaffolding](upickle.default.write(c.state.now()))
    val reopened = new MailEditorState(Var(restored), true)
    reopened.selectMail(id); assert(reopened.selectedNow.get.read)
  }
  test("stale selection cannot act on messages moved by synchronization") {
    val c = controller(); val id = c.state.now().inboxState.mailList.head.id
    c.selectMail(id)
    c.state.update(s => s.copy(inboxState = s.inboxState.deleteMail(id)))
    c.moveSelected(MailFolder.Archive); c.unread()
    assertEquals(c.state.now().inboxState.getMailById(id).get.realFolder, MailFolder.Trash)
  }
  test("composing is enforced by the controller as well as the toolbar") {
    val c = controller(false); val start = c.state.now()
    c.beginDraft(MailDraft("a@b.com", "s", "b")); assertEquals(c.draft.now(), None)
    c.draft.set(Some(MailDraft("a@b.com", "s", "b")))
    assert(!c.send("id", "a@b.com", "date")); assertEquals(c.state.now(), start)
  }
  test("invalid drafts stay visible; sending and cancellation only affect local state") {
    val c = controller(); val start = c.state.now()
    c.beginDraft(MailDraft()); assert(!c.send("new", "me@example.com", "date"))
    assertEquals(c.notice.now(), "invalidRecipient"); assert(c.draft.now().isDefined)
    assertEquals(c.state.now(), start)
    c.cancelDraft(); assertEquals(c.state.now(), start)
    c.beginDraft(MailDraft("a@b.com", "s", "b")); assert(c.send("new", "me@example.com", "date"))
    assertEquals(c.folder.now(), MailFolder.Sent); assertEquals(c.selectedNow.get.id, "new")
    assertEquals(c.draft.now(), None); assertEquals(c.state.now().inboxState.sortingResult.total, 15)
  }
  test("all 15 original examples retain independent expected placement and simulated attachments") {
    val inbox = PhishingMailboxData.initialInbox
    assertEquals(inbox.mailList.size, 15)
    assertEquals(inbox.getMailsInFolder(MailFolder.Inbox).size, 15)
    assert(inbox.mailList.forall(m => m.bodyHtml && m.expectedFolder.isDefined))
    assert(inbox.mailList.exists(_.attachment.exists(_.endsWith(".exe"))))
    val sorted = inbox.mailList.foldLeft(inbox)((s, m) => s.moveMail(m.id, m.expectedFolder.get))
    assert(sorted.sortingResult.passed)
  }
  test("local image paths exclude remote URLs, traversal and active formats") {
    assert(MailBody.allowedImage("pics/telekom.png"))
    for (src <- List("https://attacker/pics/x.png", "//attacker/x.png", "pics/../x.png", "pics/x.svg", "pics/x.png?secret", "data:image/png;base64,abc"))
      assert(!MailBody.allowedImage(src))
  }
  test("digital workbook is registered and serializes both mailbox interactions") {
    val wb = CreatePhishingWorkbook(null).createWorkbook
    val serializer = WorkbookElementFactory.serializerRegularJsonWorkbook
    assertEquals(serializer.deserialize(serializer.serialize(wb)), wb)
  }
}
