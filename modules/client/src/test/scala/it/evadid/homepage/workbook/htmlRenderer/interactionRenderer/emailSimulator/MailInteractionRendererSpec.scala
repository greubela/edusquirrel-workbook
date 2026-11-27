package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import it.evadid.workbook.elements.interactionElements.emailSimulator.*
import munit.FunSuite

class MailInteractionRendererSpec extends FunSuite {
  test("simulator interactions share the same persisted state format") {
    val state = InboxStateScaffolding(InboxState.empty)
    val reader = MailInteraction("read")
    val editor = MailEditor("edit")
    assertEquals(reader.interactionFormat.stateSerializer.deserialize(editor.interactionFormat.stateSerializer.serialize(state)), state)
  }
}
