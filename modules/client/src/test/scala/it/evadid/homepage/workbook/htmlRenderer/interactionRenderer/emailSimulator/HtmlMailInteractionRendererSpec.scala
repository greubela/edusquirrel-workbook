package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import it.evadid.workbook.elements.interactionElements.emailSimulator.*
import munit.FunSuite

class HtmlMailInteractionRendererSpec extends FunSuite {
  test("mail interaction serializes the shared mailbox state") {
    val interaction = MailInteraction("mail")
    val state = InboxStateScaffolding(InboxState.withMails(List(
      Mail("1", "sender@example.com", "Sender", "Subject", "Body", "Posteingang", None, "2026-06-22")
    )))
    assertEquals(interaction.serializerInteractionContent.deserialize(interaction.serializerInteractionContent.serialize(state)), state)
  }
}
