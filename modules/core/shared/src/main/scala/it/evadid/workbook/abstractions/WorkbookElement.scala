package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.*
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.abstractions.scaffolding.WorkbookInteractionScaffolder
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}
import upickle.default.*

import scala.reflect.ClassTag.Nothing

object WorkbookElement {


}

sealed trait WorkbookElement {
  val elementId: String
  //  assert(elementId.matches("[a-zA-Z0-9.-]+"))
  lazy val asRef = WorkbookElementReference(elementId, Option(this.getClass.getSimpleName))

  lazy val childrenOfThisElement: List[WorkbookElement]
  lazy val allInteractionsInThisInstance: List[WorkbookInteractionUntyped]

  lazy val allChildrenFullSubtree: List[WorkbookElement] = childrenOfThisElement ++ childrenOfThisElement.flatMap(_.allChildrenFullSubtree)

  val associatedFactory: WorkbookElementFactory[? <: WorkbookElement]

  lazy val toSerialized: WorkbookElementSerializable = associatedFactory.toSerializableElementUnsafe(this)

  lazy val toStringRegularJson: String = WorkbookElementFactory.serializerRefBasedJson.serialize(this)
  lazy val toStringConstructorLike: String = WorkbookElementFactory.serializerConstructorLike.serialize(this)

}

trait WorkbookDisplayElement extends WorkbookElement {
  lazy val childrenOfThisElement: List[WorkbookElement] = List()
  lazy val allInteractionsInThisInstance: List[WorkbookInteractionUntyped] = List()
}

trait WorkbookStructureElement[T <: WorkbookElement] extends WorkbookElement {
  lazy val structureType: WorkbookStructuringType
  override lazy val childrenOfThisElement: List[WorkbookElement] = groupElements

  def groupElements: List[T]

  override lazy val allInteractionsInThisInstance: List[WorkbookInteractionUntyped] = List()
}

trait WorkbookInteractionElementFull[T: ReadWriter, GA: ReadWriter, SA: ReadWriter] extends WorkbookElement {

  override lazy val allInteractionsInThisInstance: List[WorkbookInteractionUntyped] = List(this)
  lazy val isDisabledState: State[Boolean] = State(false)
  // needs to be lazy or defaultValue (from subclass) might not be inited!
  lazy val interactionVariable: InteractionVariable[T, GA, SA] = InteractionVariable[T, GA, SA](this)

  val defaultValue: T
  val serializerInteractionContent: Serializer[T] = Serializer.fromImplicitRW

  val gradingElements: List[WorkbookInteractionGrader[T, GA]] = List()
  val scaffoldingElement: Option[WorkbookInteractionScaffolder[T, SA]] = None
}

type WorkbookInteractionElement[T] = WorkbookInteractionElementFull[T, Any, Any]

type WorkbookInteractionUntyped = WorkbookInteractionElementFull[?, ?, ?]
