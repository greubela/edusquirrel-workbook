package it.evadid.workbook.elements.interactionElements.emailSimulator

import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.emailSimulator.MailEditor.{InboxStateScaffolding, mailEditorSer}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.NoContentElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.ReadWriter
import upickle.default.macroRW

case class MailEditor(override val elementId: String) extends WorkbookInteractionElement[InboxStateScaffolding] {
  override val associatedFactory: NoContentElementFactory[MailEditor] = MailEditor.factory

  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: InboxStateScaffolding = InboxStateScaffolding(InboxState.empty)

  override val serializerInteractionContent: Serializer[InboxStateScaffolding] = mailEditorSer
}

object MailEditor {

  val factory: NoContentElementFactory[MailEditor] = new NoContentElementFactory[MailEditor]() {
    override def callConstructor(elementId: String): MailEditor = MailEditor(elementId)
  }

  case class InboxStateScaffolding(inboxState: InboxState)

  private given inboxStateRw: ReadWriter[InboxState] = Serializer.fromUpickleJson(upickle.default.macroRW[InboxState])
  private val mailEditorRw: ReadWriter[InboxStateScaffolding] = macroRW
  private val mailEditorSer: Serializer[InboxStateScaffolding] = Serializer.fromUpickleJson(mailEditorRw)
}
