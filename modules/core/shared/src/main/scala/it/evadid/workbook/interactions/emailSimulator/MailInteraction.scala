package it.evadid.workbook.interactions.emailSimulator

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class MailInteraction(elementId: String, initialInbox: InboxState = InboxState.empty,
                           account: String = "opa.jürgen@gmail.com", allowCompose: Boolean = false)
  extends WorkbookInteractionElement[MailInteractionFormat.type] {

  override val associatedFactory = MailInteraction.factory
  override lazy val childrenOfThisElement: Seq[WorkbookElement] = Nil
  override val defaultValue = InboxStateScaffolding(initialInbox.normalized)


  def isPassed(answer: InteractionStateType): Boolean = answer.inboxState.sortingResult.passed

}

object MailInteraction {

  val mailInteractionSer: Serializer[InboxStateScaffolding] = Serializer.fromUpickleJson[InboxStateScaffolding]
  val factory: WorkbookElementFactory.SimpleWorkbookElementFactory[MailInteraction] =
    new WorkbookElementFactory.SimpleWorkbookElementFactory[MailInteraction] {
      override protected val constructorFieldOrder = List("elementId", "initialInbox", "account", "allowCompose")

      override def finishSerialization(base: WorkbookElementSerializable, element: MailInteraction): WorkbookElementSerializable =
        base.withElementAddedAs("initialInbox", element.initialInbox).withElementAddedAs("account", element.account)
          .withElementAddedAs("allowCompose", element.allowCompose)

      override def finishDeserialization(element: WorkbookElementSerializable): MailInteraction =
        MailInteraction(element.elementId, element.getOptionalElementAs[InboxState]("initialInbox", InboxState.empty),
          element.getOptionalElementAs[String]("account", "opa.jürgen@gmail.com"),
          element.getOptionalElementAs[Boolean]("allowCompose", false))
    }
}
