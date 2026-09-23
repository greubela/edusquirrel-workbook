package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.{AutoSerializable, Serializer}
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference}

sealed trait WorkbookElement extends AutoSerializable[WorkbookElement, WorkbookElementFactory] {
  val elementId: String

  lazy val asRef = WorkbookElementReference(elementId, this.getClass.getSimpleName)

  lazy val childrenOfThisElement: List[WorkbookElement]

  lazy val allContainedInteractions: List[WorkbookInteractionElement[?]] = {
    childrenOfThisElement.flatMap(_.allContainedInteractions) ++ WorkbookElement.this.match {
      case i: WorkbookInteractionElement[?] => List(i)
      case _ => List()
    }
  }

  lazy val serializer: Serializer[WorkbookElementFactory] = WorkbookElementFactory.serializer


  //def fromFactory(factoryVerifiedType: WorkbookElementFactory): WorkbookElement


  protected lazy val toFactoryBase: WorkbookElementFactory = WorkbookElementFactory(
    elementId, this.getClass.getSimpleName, Map()
  )

}

trait WorkbookDisplayElement extends WorkbookElement {
  lazy val childrenOfThisElement: List[WorkbookElement] = List()
}

trait WorkbookStructureElement[T <: WorkbookElement] extends WorkbookElement {

  lazy val structureType: WorkbookStructuringType
  override lazy val childrenOfThisElement: List[WorkbookElement] = groupElements

  def groupElements: List[T]

}

trait WorkbookInteractionElement[T] extends WorkbookElement {
  override lazy val allContainedInteractions: List[WorkbookInteractionElement[?]] = List(this)
  lazy val isDisabledState: State[Boolean] = State(false)
  // needs to be lazy or defaultValue (from subclass) might not be inited!
  lazy val interactionVariable: InteractionVariable[T] = InteractionVariable[T](this)

  val defaultValue: T
  val serializerInteractionContent: Serializer[T]
}
