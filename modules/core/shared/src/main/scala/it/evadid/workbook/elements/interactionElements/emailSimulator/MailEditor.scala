package it.evadid.workbook.elements.interactionElements.emailSimulator

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{InteractionFormat, DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class MailEditor(elementId: String, initialInbox: InboxState = InboxState.empty,
                        account: String = "opa.jürgen@gmail.com", allowCompose: Boolean = true)
    extends WorkbookInteractionElement[InboxStateScaffolding] {

  override val associatedFactory = MailEditor.factory
  override lazy val childrenOfThisElement: List[WorkbookElement] = Nil
  override val defaultValue = InboxStateScaffolding(initialInbox.normalized)
  override val serializerInteractionContent = MailEditor.mailEditorSer
  def isPassed: Boolean = interactionVariable.currentValue.inboxState.sortingResult.passed
  override def format: InteractionFormat[InboxStateScaffolding, Unit, Unit] = MailEditor.format
}
object MailEditor extends InteractionFormat[InboxStateScaffolding, Unit, Unit] {
  val mailEditorSer: Serializer[InboxStateScaffolding] = Serializer.fromUpickleJson(summon[ReadWriter[InboxStateScaffolding]])
  val factory: WorkbookElementFactory.SimpleWorkbookElementFactory[MailEditor] =
    new WorkbookElementFactory.SimpleWorkbookElementFactory[MailEditor] {
      override protected val constructorFieldOrder = List("elementId", "initialInbox", "account", "allowCompose")
      override def finishSerialization(base: WorkbookElementSerializable, element: MailEditor): WorkbookElementSerializable =
        base.withElementAddedAs("initialInbox", element.initialInbox).withElementAddedAs("account", element.account)
          .withElementAddedAs("allowCompose", element.allowCompose)
      override def finishDeserialization(element: WorkbookElementSerializable): MailEditor =
        MailEditor(element.elementId, element.getOptionalElementAs[InboxState]("initialInbox", InboxState.empty),
          element.getOptionalElementAs[String]("account", "opa.jürgen@gmail.com"),
          element.getOptionalElementAs[Boolean]("allowCompose", true))
    }

  override def stateSerializer: Serializer[InboxStateScaffolding] = Serializer.fromImplicitRW
  override def historySerializer: Serializer[InteractionVariableHistory[InboxStateScaffolding, Unit, Unit]] = Serializer.fromImplicitRW
  override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[InboxStateScaffolding, Unit, Unit]] = Serializer.fromImplicitRW
}
