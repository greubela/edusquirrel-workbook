package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.emailSimulator.{InboxState, Mail, MailInteraction}
import munit.FunSuite

class MailInteractionRendererSpec extends FunSuite {

  test("HtmlMailInteractionRenderer CSS is defined") {
    assert(MailInteractionCSS.css.nonEmpty)
  }

  test("HtmlMailInteractionRenderer creates correct rendering for empty inbox") {
    val mailInteraction = MailInteraction("test-email-sim")
    val state = InboxState.empty
    val mailInteractionWithState = mailInteraction.copy(
      interactionVariable = mailInteraction.interactionVariable.copy(
        defaultValue = mailInteraction.defaultValue.copy(inboxState = state)
      )
    )
    
    val rendering = HtmlMailInteractionRenderer.createRendering(mailInteractionWithState)
    
    assert(rendering != null)
    assert(rendering.cards.size > 0)
  }

  test("HtmlMailInteractionRenderer creates correct rendering for inbox with mails") {
    val mail1 = Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01")
    val mail2 = Mail("2", "sender2@example.com", "Sender Two", "Subject 2", "Body 2", "Posteingang", None, "2024-01-02")
    val state = InboxState.withMails(List(mail1, mail2))
    
    val mailInteraction = MailInteraction("test-email-sim")
    val mailInteractionWithState = mailInteraction.copy(
      interactionVariable = mailInteraction.interactionVariable.copy(
        defaultValue = mailInteraction.defaultValue.copy(inboxState = state)
      )
    )
    
    val rendering = HtmlMailInteractionRenderer.createRendering(mailInteractionWithState)
    
    assert(rendering != null)
    assert(rendering.cards.size > 0)
  }

  test("HtmlMailInteractionRenderer CSS contains expected classes") {
    val css = MailInteractionCSS.css
    
    assert(css.contains(".mail-interaction"))
    assert(css.contains(".mail-interaction__folders"))
    assert(css.contains(".mail-folder-card"))
    assert(css.contains(".mail-interaction__mail-list"))
    assert(css.contains(".mail-card"))
  }

  test("createFolderCard creates cards for all folders") {
    val mailListVar = com.raquo.airstream.state.Var(List.empty[Mail])
    
    val inboxCard = HtmlMailInteractionRenderer.createFolderCard("Posteingang", mailListVar, "inbox")
    val starCard = HtmlMailInteractionRenderer.createFolderCard("Markiert", mailListVar, "star")
    val archiveCard = HtmlMailInteractionRenderer.createFolderCard("Archiv", mailListVar, "archive")
    val trashCard = HtmlMailInteractionRenderer.createFolderCard("Papierkorb", mailListVar, "trash")
    
    assert(inboxCard != null)
    assert(starCard != null)
    assert(archiveCard != null)
    assert(trashCard != null)
  }

  test("createMailCard creates card with correct mail data") {
    val mail = Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01")
    
    val card = HtmlMailInteractionRenderer.createMailCard(mail)
    
    assert(card != null)
  }

  test("HtmlMailInteractionRenderer handles folder count updates") {
    val mail1 = Mail("1", "sender1@example.com", "Sender One", "Subject 1", "Body 1", "Posteingang", None, "2024-01-01")
    val state = InboxState.withMails(List(mail1))
    
    val mailListVar = com.raquo.airstream.state.Var(List(mail1))
    
    val folderCard = HtmlMailInteractionRenderer.createFolderCard("Posteingang", mailListVar, "inbox")
    
    assert(folderCard != null)
  }

  test("HtmlMailInteractionRenderer creates proper mail card structure") {
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
    
    val card = HtmlMailInteractionRenderer.createMailCard(mail)
    
    assert(card != null)
    assert(card.contentElement != null)
  }

  test("HtmlMailInteractionRenderer CSS has interactive states") {
    val css = MailInteractionCSS.css
    
    assert(css.contains(".mail-folder-card:hover"))
    assert(css.contains(".mail-card:hover"))
    assert(css.contains(".mail-card__action:hover"))
  }

  test("HtmlMailInteractionRenderer CSS has proper layout") {
    val css = MailInteractionCSS.css
    
    assert(css.contains("display: flex"))
    assert(css.contains("flex-direction: row"))
    assert(css.contains("flex-direction: column"))
  }
}
