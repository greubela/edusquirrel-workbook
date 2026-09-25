package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.UpdateImportance
import UpdateImportance.{MAJOR, MINOR, TEMPORARY}
import it.evadid.workbook.interaction.variable.InteractionVariableState.InteractionVariableStateChanged
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.{NoContentElementFactory, SimpleWorkbookElementFactory}
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

import java.time.Duration

case class TextInteraction(override val elementId: String) extends WorkbookInteractionElement[String] {
  override val associatedFactory = TextInteraction.factory
  lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val defaultValue: String = ""
  override val serializerInteractionContent: Serializer[String] = Serializer.stringIO
}
object TextInteraction {
  val factory: NoContentElementFactory[TextInteraction] = new NoContentElementFactory[TextInteraction]() {
    override def callConstructor(elementId: String): TextInteraction = TextInteraction(elementId)
  }



  def decideTextareaUpdateImportance(change: InteractionVariableStateChanged[String]): UpdateImportance =
    if (change.lastState.value == change.newState.value) TEMPORARY else {
      val lenNew = change.newState.value.length
      val lenDiff = lenNew - change.lastState.value.length
      val timeDiff = Duration.between(change.lastState.timestamp, change.newState.timestamp)
      if (timeDiff.abs().getSeconds > 10) MAJOR
      else if (lenNew > 0 && lenDiff == 1) { val last = change.newState.value.last; if ("\n\t.!?".contains(last)) MAJOR else if (" ,:-+=;&/|".contains(last)) MINOR else TEMPORARY }
      else MINOR
    }
}
