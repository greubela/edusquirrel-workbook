package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import it.evadid.workbook.elements.interactionElements.emailSimulator.*
import munit.FunSuite

class MailInteractionRendererSpec extends FunSuite {
  test("mail interaction factory preserves its identity") {
    val interaction = MailInteraction("inbox-example")
    assertEquals(MailInteraction.factory.fromSerializedElement(interaction.toSerialized, Map.empty), interaction)
  }
  test("simulator interactions share the same persisted state format") {
    val state = InboxStateScaffolding(InboxState.empty)
    val reader = MailInteraction("read")
    val editor = MailEditor("edit")
    assertEquals(reader.serializerInteractionContent.deserialize(editor.serializerInteractionContent.serialize(state)), state)
  }
}
