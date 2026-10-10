package it.evadid.workbook.abstractions

import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.*
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.abstractions.scaffolding.WorkbookInteractionScaffolder
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

object WorkbookElement {


}

sealed trait WorkbookElement {
  val elementId: String
  //  assert(elementId.matches("[a-zA-Z0-9.-]+"))
  lazy val asRef = WorkbookElementReference(elementId, Option(this.getClass.getSimpleName))

  lazy val childrenOfThisElement: List[WorkbookElement]
  lazy val allContainedInteractions: List[WorkbookInteractionElement[?]] = allChildrenFullSubtree.flatMap {
    case i: WorkbookInteractionElement[?] => List(i)
    case _ => List()
  }

  lazy val allChildrenFullSubtree: List[WorkbookElement] = childrenOfThisElement ++ childrenOfThisElement.flatMap(_.allChildrenFullSubtree)

  val associatedFactory: WorkbookElementFactory[? <: WorkbookElement]

  lazy val toSerialized: WorkbookElementSerializable = associatedFactory.toSerializableElementUnsafe(this)

  lazy val toStringRegularJson: String = WorkbookElementFactory.serializerRefBasedJson.serialize(this)
  lazy val toStringConstructorLike: String = WorkbookElementFactory.serializerConstructorLike.serialize(this)

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

  val gradingElements: List[WorkbookInteractionGrader[T]] = List()

  val scaffoldingElement: Option[WorkbookInteractionScaffolder[T]] = None

}
