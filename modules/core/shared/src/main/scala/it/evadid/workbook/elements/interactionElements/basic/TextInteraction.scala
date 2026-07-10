package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.UpdateImportance
import UpdateImportance.{MAJOR, MINOR, TEMPORARY}
import it.evadid.workbook.interaction.variable.InteractionVariableState.InteractionVariableStateChanged

import java.time.Duration

case class TextInteraction(id: String) extends WorkbookInteractionElement[String] {
  lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val defaultValue: String = ""
  override val serializer: Serializer[String] = Serializer.stringIO

}

object TextInteraction {

  def decideTextareaUpdateImportance(change: InteractionVariableStateChanged[String]): UpdateImportance =
    if (change.lastState.value == change.newState.value) TEMPORARY else {
      val lenNew: Int = change.newState.value.length
      val lenDiff: Int = change.newState.value.length - change.lastState.value.length // positive -> adding text
      val timeDiff: Duration = Duration.between(change.lastState.timestamp, change.newState.timestamp)
      if (timeDiff.abs().getSeconds > 10) MAJOR
      else if (lenNew > 0 && lenDiff == 1) {
        val last: Char = change.newState.value.last
        if ("\n\t.!?".contains(last)) MAJOR else if (" ,:-+=;&/|".contains(last)) MINOR else TEMPORARY
      } else {
        MINOR
      }
    }

}