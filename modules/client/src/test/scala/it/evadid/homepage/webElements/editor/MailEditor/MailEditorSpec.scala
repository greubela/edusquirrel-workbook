package it.evadid.homepage.webElements.editor.MailEditor

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.workbook.elements.interactionElements.emailSimulator.{MailEditor as _, *}
import org.scalajs.dom

class MailEditorSpec extends UiDomSuite {
  private val htmlBody = """<p style="text-align: center">Dear customer,</p><script>alert(1)</script>""" +
    """<div style="font-size:10pt; background: red">Please <a href="https://bank.example.invalid/login">sign in</a> now.</div>""" +
    """<img src="pics/logo.png" alt="Bank logo"><img src="https://tracker.invalid/p.gif" alt="tracking pixel">""" +
    """<section>kept text</section><a>no target</a>"""
  private val phishing = Mail("m1", "security@bank.example.invalid", "Bank Security", "Account locked", htmlBody,
    MailFolder.Inbox, None, "2026-01-02 09:00", expectedFolder = Some(MailFolder.Trash), bodyHtml = true)
  private val invoice = Mail("m2", "anna@school.example", "Anna", "Invoice", "See the attached invoice.\nAnna",
    MailFolder.Inbox, Some("invoice.pdf"), "2026-01-03 10:00", expectedFolder = Some(MailFolder.Archive), recipients = Seq("me@school.example"))
  private val note = Mail("m3", "ben@school.example", "Ben", "Re: Lunch", "Noon?", MailFolder.Archive, None, "2026-01-04 11:00", read = true)
  private def inbox = Var(InboxStateScaffolding(InboxState.withMails(Seq(phishing, invoice, note))))

  private def items(root: dom.Element) = all(root, ".mail-list-item")
  private def mail(state: Var[InboxStateScaffolding], id: String) = state.now().inboxState.getMailById(id).get
  private def folderButton(root: dom.Element, folder: String) = withText(one(root, ".mail-folders"), "button", loadingText(s"emailSimulator/$folder")).head
  private def action(root: dom.Element, key: String) = buttonNamed(one(root, ".mail-toolbar"), loadingText(s"emailSimulator/$key"))

  test("the inbox lists its unread mails; opening one marks it read and shows sender, recipients and attachment") {
    val state = inbox
    mounted(MailEditor(state, account = "me@school.example").getDomElement()) { root =>
      assertEquals(items(root).map(_.querySelector(".mail-sender").textContent), List("● Bank Security", "● Anna"))
      assert(one(root, ".mail-folder-summary").textContent.contains(loadingText(s"emailSimulator/${MailFolder.Inbox}")))
      assertEquals(all(root, ".mail-count").map(_.textContent), List("2", "0", "0", "1", "0"))
      assert(root.textContent.contains(loadingText("emailSimulator/noSelection")))
      assert(isDisabled(action(root, "archive")), "nothing to archive without a selection")

      click(items(root)(1))
      assert(mail(state, "m2").read)
      assert(!items(root)(1).classList.contains("is-unread"))
      assertEquals(items(root)(1).getAttribute("aria-pressed"), "true")
      val viewer = one(root, ".mail-viewer")
      assertEquals(one(viewer, "h2").textContent, "Invoice")
      assert(viewer.textContent.contains("Anna <anna@school.example>"))
      assert(viewer.textContent.contains("me@school.example"))
      assertEquals(one(viewer, ".mail-body").textContent, "See the attached invoice.\nAnna")
      assert(one(viewer, ".mail-body").classList.contains("mail-body--plain"))
      click(one(viewer, ".mail-attachment"))
      assert(one(root, ".mail-status").textContent.contains(loadingText("emailSimulator/attachmentSimulated")))
      assert(!isDisabled(action(root, "archive")))
      assert(isDisabled(action(root, "restore")), "inbox mails need no restoring")
    }
  }

  test("an HTML mail is rebuilt inert: scripts vanish, links only show their target, untrusted images become text") {
    val state = inbox
    mounted(MailEditor(state).getDomElement()) { root =>
      click(items(root).head)
      val body = one(root, ".mail-body")
      assertEquals(all(body, "script").size, 0)
      assertEquals(all(body, "a").size, 0)
      assert(one(body, "p").classList.contains("mail-body--center"))
      val panel = one(body, "div")
      assert(panel.classList.contains("mail-body--small") && panel.classList.contains("mail-body--panel"))
      val images = all(body, "img")
      assertEquals(images.size, 1)
      assert(images.head.getAttribute("src").endsWith("E-Mail-Simulation/pics/logo.png"))
      assertEquals(images.head.getAttribute("alt"), "Bank logo")
      assert(body.textContent.contains("tracking pixel"), "a foreign image is replaced by its description")
      assertEquals(all(body, "span").map(_.textContent).filter(_.nonEmpty), List("tracking pixel", "kept text"))
      assert(one(root, ".mail-viewer header").textContent.contains("opa.jürgen@gmail.com"), "no recipients means the own account")

      val link = all(body, "button.mail-link").head
      assertEquals(link.getAttribute("title"), "https://bank.example.invalid/login")
      val status = one(root, ".mail-status")
      dispatch(link, "mouseenter")
      assert(status.textContent.contains("https://bank.example.invalid/login"), "hovering shows the real target")
      dispatch(link, "mouseleave")
      assert(!status.textContent.contains("bank.example"))
      dispatch(link, "focus")
      assert(status.textContent.contains("https://bank.example.invalid/login"))
      dispatch(link, "blur")
      click(link)
      assert(status.textContent.contains(loadingText("emailSimulator/linkSimulated")), "following a link is only simulated")
      click(all(body, "button.mail-link")(1))
      assertEquals(all(body, "button.mail-link")(1).getAttribute("title"), "")
      val quoted = MailBody.plainText(phishing)
      assert(quoted.contains("Dear customer,") && quoted.contains("Please sign in now."), "replies quote the text without markup")
      assert(!quoted.contains("<"), quoted)
    }
  }

  test("moving mails between folders, marking unread and restoring them") {
    val state = inbox
    mounted(MailEditor(state).getDomElement()) { root =>
      click(items(root).head)
      click(action(root, "delete"))
      assertEquals(mail(state, "m1").realFolder, MailFolder.Trash)
      assertEquals(items(root).size, 1)
      click(items(root).head)
      click(action(root, "unread"))
      assert(!mail(state, "m2").read)
      click(action(root, "archive"))
      assertEquals(mail(state, "m2").realFolder, MailFolder.Archive)
      assert(root.textContent.contains(loadingText("emailSimulator/emptyFolder")))

      click(folderButton(root, MailFolder.Archive))
      assertEquals(folderButton(root, MailFolder.Archive).getAttribute("aria-pressed"), "true")
      assert(folderButton(root, MailFolder.Archive).classList.contains("is-selected"))
      assertEquals(items(root).map(_.querySelector("span:nth-child(2)").textContent), List("Invoice", "Re: Lunch"))
      click(items(root).head)
      assert(!isDisabled(action(root, "restore")))
      click(action(root, "mark"))
      assertEquals(mail(state, "m2").realFolder, MailFolder.Marked)
      click(folderButton(root, MailFolder.Marked))
      click(items(root).head)
      click(action(root, "restore"))
      assertEquals(mail(state, "m2").realFolder, MailFolder.Inbox)
      assertEquals(all(root, ".mail-count").map(_.textContent), List("1", "0", "0", "1", "1"))
    }
  }

  test("composing validates the draft, sends locally into Sent and opens the sent mail") {
    val state = inbox
    mounted(MailEditor(state, account = "me@school.example").getDomElement()) { root =>
      click(action(root, "compose"))
      val compose = one(root, "form.mail-compose")
      val List(to, subject) = all(compose, "input"): @unchecked
      typeInto(to, "not an address")
      typeInto(subject, "Question")
      typeInto(one(compose, "textarea"), "Is the bank mail real?")
      dispatch(compose, "submit")
      assert(one(root, ".mail-status").textContent.contains(loadingText("emailSimulator/invalidRecipient")))
      assertEquals(state.now().inboxState.getMailsInFolder(MailFolder.Sent), Nil)

      typeInto(to, "teacher@school.example")
      dispatch(compose, "submit")
      val sent = state.now().inboxState.getMailsInFolder(MailFolder.Sent)
      assertEquals(sent.map(m => (m.subject, m.body, m.recipients, m.sender)),
        List(("Question", "Is the bank mail real?", Seq("teacher@school.example"), "me@school.example")))
      assertEquals(folderButton(root, MailFolder.Sent).getAttribute("aria-pressed"), "true")
      assertEquals(one(root, ".mail-viewer h2").textContent, "Question")
      assert(one(root, ".mail-status").textContent.contains(loadingText("emailSimulator/sentLocally")))
    }
  }

  test("reply and forward prefill the draft; cancelling discards it") {
    val state = inbox
    mounted(MailEditor(state).getDomElement()) { root =>
      click(items(root)(1))
      click(action(root, "reply"))
      val inputs = all(root, "form.mail-compose input").map(valueOf)
      assertEquals(inputs, List("anna@school.example", "Re: Invoice"))
      assert(valueOf(one(root, "form.mail-compose textarea")).contains("Anna <anna@school.example>:\nSee the attached invoice."))
      click(buttonNamed(root, loadingText("emailSimulator/cancel")))
      assertEquals(all(root, "form.mail-compose").size, 0)
      assertEquals(one(root, ".mail-viewer h2").textContent, "Invoice")

      click(action(root, "forward"))
      assertEquals(all(root, "form.mail-compose input").map(valueOf), List("", "Fwd: Invoice"))
      assert(one(root, "form.mail-compose").textContent.contains("invoice.pdf"), "forwarding keeps the attachment")
    }
  }

  test("a read-only mailbox offers no compose, reply or forward and unmounting forgets the draft") {
    val state = inbox
    val editor = MailEditor(state, allowCompose = false)
    assert(!editor.dismissOnOutsideClick)
    mounted(editor.getDomElement()) { root =>
      click(items(root).head)
      List("compose", "reply", "forward").foreach(key => assert(isDisabled(action(root, key)), key))
    }
    val composing = Var(InboxStateScaffolding(InboxState.withMails(Seq(invoice))))
    val element = MailEditor(composing).getDomElement()
    mounted(element) { root => click(action(root, "compose")); assertEquals(all(root, "form").size, 1) }
    mounted(element) { root => assertEquals(all(root, "form").size, 0, "a remounted editor starts without the old draft") }
  }

  test("checking the sorting reports unfinished, wrong and passed results with the score") {
    val state = inbox
    mounted(MailEditor(state).getDomElement()) { root =>
      val check = buttonNamed(one(root, ".mail-feedback"), loadingText("emailSimulator/check"))
      def feedback = one(root, ".mail-feedback").textContent
      click(check)
      assert(feedback.contains(loadingText("emailSimulator/sortFirst")) && feedback.contains("(0/2)"), feedback)
      state.update(s => s.copy(inboxState = s.inboxState.moveMail("m1", MailFolder.Archive).moveMail("m2", MailFolder.Archive)))
      assert(feedback.contains(loadingText("emailSimulator/tryAgain")) && feedback.contains("(1/2)"), feedback)
      state.update(s => s.copy(inboxState = s.inboxState.moveMail("m1", MailFolder.Trash)))
      assert(feedback.contains(loadingText("emailSimulator/passed")) && feedback.contains("(2/2)"), feedback)
    }
    mounted(MailEditor(Var(InboxStateScaffolding(InboxState.withMails(Seq(note))))).getDomElement()) { root =>
      assert(isDisabled(buttonNamed(one(root, ".mail-feedback"), loadingText("emailSimulator/check"))), "no exercise mails, nothing to check")
    }
  }
}
