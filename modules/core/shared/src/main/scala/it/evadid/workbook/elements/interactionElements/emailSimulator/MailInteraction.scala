package it.evadid.workbook.elements.interactionElements.emailSimulator

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.emailSimulator.MailInteraction.mailInteractionSer
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

  private val mailInteractionRw: ReadWriter[InboxStateScaffolding] = macroRW
  val mailInteractionSer: Serializer[InboxStateScaffolding] = Serializer.fromUpickleJson(mailInteractionRw)
}
