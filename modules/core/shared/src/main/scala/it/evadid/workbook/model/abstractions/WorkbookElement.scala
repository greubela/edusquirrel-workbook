package it.evadid.workbook.model.abstractions

import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.sync.UpdateImportance.*
import it.evadid.workbook.model.interaction.variable.InteractionVariable
import it.evadid.workbook.model.interaction.variable.InteractionVariableState.InteractionVariableStateChanged

import java.time.Duration

sealed trait WorkbookElement {
  lazy val childrenOfThisElement: List[WorkbookElement]

  lazy val allContainedInteractions: List[WorkbookInteractionElement[?]] =
    childrenOfThisElement.flatMap(_.allContainedInteractions) ++ this.match {
      case i: WorkbookInteractionElement[?] => List(i)
      case _ => List()
    }
}

trait WorkbookDisplayElement extends WorkbookElement {
  lazy val childrenOfThisElement: List[WorkbookElement] = List()
}

trait WorkbookStructureElement[T <: WorkbookElement] extends WorkbookElement {

  lazy val structureType: WorkbookStructuringType

  def groupElements: List[T]

  override lazy val childrenOfThisElement: List[WorkbookElement] = groupElements

}

trait WorkbookInteractionElement[T] extends WorkbookElement {
  val id: String
  override lazy val allContainedInteractions: List[WorkbookInteractionElement[?]] = List(this)
  lazy val isDisabledState: State[Boolean] = State(false)
  val defaultValue: T
  val serializer: Serializer[T]

  // needs to be lazy or defaultValue (from subclass) might not be inited!
  lazy val interactionVariable: InteractionVariable[T] = InteractionVariable[T](this)
}
