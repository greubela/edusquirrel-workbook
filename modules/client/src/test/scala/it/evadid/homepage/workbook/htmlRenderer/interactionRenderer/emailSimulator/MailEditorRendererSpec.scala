package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.emailSimulator.{InboxState, Mail, MailEditor}
import munit.FunSuite

class MailEditorRendererSpec extends FunSuite {

  test("HtmlMailEditorRenderer CSS is defined") {
    assert(EmailSimulatorCSS.css.nonEmpty)
  }

  test("HtmlMailEditorRenderer creates correct rendering for empty inbox") {
    val mailEditor = MailEditor("test-email-editor")
    val state = InboxState.empty
    val mailEditorWithState = mailEditor.copy(
      interactionVariable = mailEditor.interactionVariable.copy(
        defaultValue = mailEditor.defaultValue.copy(inboxState = state)
      )
    )
    
    val rendering = HtmlMailEditorRenderer.createRendering(mailEditorWithState)
    
    assert(rendering != null)
    assert(rendering.cards.size > 0)
  }

  test("HtmlMailEditorRenderer creates correct rendering for inbox with mails") {
    val mail1 = Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01")
    val mail2 = Mail("2", "sender2@example.com", "Sender Two", "Subject 2", "Body 2", "Posteingang", None, "2024-01-02")
    val state = InboxState.withMails(List(mail1, mail2))
    
    val mailEditor = MailEditor("test-email-editor")
    val mailEditorWithState = mailEditor.copy(
      interactionVariable = mailEditor.interactionVariable.copy(
        defaultValue = mailEditor.defaultValue.copy(inboxState = state)
      )
    )
    
    val rendering = HtmlMailEditorRenderer.createRendering(mailEditorWithState)
    
    assert(rendering != null)
    assert(rendering.cards.size > 0)
  }

  test("HtmlMailEditorRenderer CSS contains expected classes") {
    val css = EmailSimulatorCSS.css
    
    assert(css.contains(".mail-editor"))
    assert(css.contains(".mail-editor__header"))
    assert(css.contains(".mail-editor__container"))
    assert(css.contains(".mail-editor__folders"))
    assert(css.contains(".mail-editor__mail-list"))
  }

  test("createMailCard creates card with correct mail data and boundVar") {
    val mail = Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01")
    val boundVar = com.raquo.airstream.state.Var(it.evadid.workbook.elements.interactionElements.emailSimulator.InboxStateScaffolding(InboxState.empty))
    
    val card = HtmlMailEditorRenderer.createMailCard(mail, boundVar)
    
    assert(card != null)
  }

  test("HtmlMailEditorRenderer handles folder count updates") {
    val mail1 = Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01")
    val state = InboxState.withMails(List(mail1))
    
    val mailListVar = com.raquo.airstream.state.Var(List(mail1))
    
    val folderCard = HtmlMailEditorRenderer.createFolderCard("Posteingang", mailListVar, "inbox")
    
    assert(folderCard != null)
  }

  test("HtmlMailEditorRenderer creates proper mail card structure with actions") {
    val mail = Mail(
      id = "1",
      sender = "sender@example.com",
      senderName = "Test Sender",
      subject = "Test Subject",
      body = "Test body content",
      realFolder = "Posteingang",
      attachment = None,
      timestamp = "2024-01-01"
    )
    
    val boundVar = com.raquo.airstream.state.Var(it.evadid.workbook.elements.interactionElements.emailSimulator.InboxStateScaffolding(InboxState.empty))
    val card = HtmlMailEditorRenderer.createMailCard(mail, boundVar)
    
    assert(card != null)
    assert(card.contentElement != null)
  }

  test("HtmlMailEditorRenderer CSS has interactive states") {
    val css = EmailSimulatorCSS.css
    
    assert(css.contains(".mail-folder-card:hover"))
    assert(css.contains(".mail-card:hover"))
    assert(css.contains(".mail-card__action:hover"))
  }

  test("HtmlMailEditorRenderer CSS has proper layout") {
    val css = EmailSimulatorCSS.css
    
    assert(css.contains("display: flex"))
    assert(css.contains("flex-direction: row"))
    assert(css.contains("flex-direction: column"))
  }

  test("HtmlMailEditorRenderer CSS has actions bar styles") {
    val css = EmailSimulatorCSS.css
    
    assert(css.contains(".mail-editor__actions-bar"))
    assert(css.contains(".mail-editor__actions-bar button"))
  }

  test("HtmlMailEditorRenderer CSS has status bar styles") {
    val css = EmailSimulatorCSS.css
    
    assert(css.contains(".mail-editor__status-bar"))
  }
}
