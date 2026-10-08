package it.evadid.workbook.elements.interactionElements.emailSimulator

import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.emailSimulator.MailInteraction.{InboxStateScaffolding, mailInteractionSer}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.NoContentElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.ReadWriter
import upickle.default.macroRW

case class MailInteraction(override val elementId: String) extends WorkbookInteractionElement[InboxStateScaffolding] {
  override val associatedFactory: NoContentElementFactory[MailInteraction] = MailInteraction.factory

  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: InboxStateScaffolding = InboxStateScaffolding(InboxState.empty)

  override val serializerInteractionContent: Serializer[InboxStateScaffolding] = mailInteractionSer
}

object MailInteraction {

  val factory: NoContentElementFactory[MailInteraction] = new NoContentElementFactory[MailInteraction]() {
    override def callConstructor(elementId: String): MailInteraction = MailInteraction(elementId)
  }

  case class InboxStateScaffolding(inboxState: InboxState)

  private given inboxStateRw: ReadWriter[InboxState] = Serializer.fromUpickleJson(upickle.default.macroRW[InboxState])
  private val mailInteractionRw: ReadWriter[InboxStateScaffolding] = macroRW
  private val mailInteractionSer: Serializer[InboxStateScaffolding] = Serializer.fromUpickleJson(mailInteractionRw)
}
