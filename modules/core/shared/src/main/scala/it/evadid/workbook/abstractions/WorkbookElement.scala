package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.{AutoSerializable, Serializer}
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

sealed trait WorkbookElement extends AutoSerializable[WorkbookElement, WorkbookElementSerializable] {
  val elementId: String

  lazy val asRef = WorkbookElementReference(elementId, this.getClass.getSimpleName)

  lazy val childrenOfThisElement: List[WorkbookElement]


  lazy val allContainedInteractions: List[WorkbookInteractionElement[?]] = allChildrenRec.flatMap {
    case i: WorkbookInteractionElement[?] => List(i)
    case _ => List()
  }

  lazy val allChildrenRec: List[WorkbookElement] = List(this) ++ childrenOfThisElement.flatMap(_.allChildrenRec)

  lazy val serializer: Serializer[WorkbookElementSerializable] = WorkbookElementSerializable.serializer


  //def fromFactory(factoryVerifiedType: WorkbookElementFactory): WorkbookElement

  val associatedFactory: WorkbookElementFactory[? <: WorkbookElement]

  lazy val toSerializableType: WorkbookElementSerializable = associatedFactory.toSerializableElementUnsafe(this)

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
