package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

import it.evadid.workbook.elements.interactionElements.emailSimulator.*
import munit.FunSuite

class MailEditorRendererSpec extends FunSuite {
  test("mail editor factory and default state are serializable without browser dependencies") {
    val editor = MailEditor("mail-editor")
    assertEquals(MailEditor.factory.fromSerializedElement(editor.toSerialized, Map.empty), editor)
    assertEquals(editor.serializerInteractionContent.deserialize(editor.serializerInteractionContent.serialize(editor.defaultValue)), editor.defaultValue)
  }
}
