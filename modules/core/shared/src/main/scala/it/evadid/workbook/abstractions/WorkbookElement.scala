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
  private case class Snapshot(rootId: String, elements: List[WorkbookElementSerializable]) derives upickle.default.ReadWriter

  // Keep the factory wire formats, but include the registry needed to resolve a
  // standalone container's references and section prerequisites.
  given upickle.default.ReadWriter[WorkbookElement] = upickle.default.readwriter[Snapshot].bimap(
    element => {
      val collected = scala.collection.mutable.LinkedHashMap.empty[String, WorkbookElement]
      var pending = List(element)
      while (pending.nonEmpty) {
        val current = pending.head
        pending = pending.tail
        collected.get(current.elementId) match {
          case Some(previous) => require(previous == current, s"Conflicting workbook element id: ${current.elementId}")
          case None =>
            collected(current.elementId) = current
            val prerequisites = current match {
              case section: it.evadid.workbook.elements.structureElements.WorkbookSection =>
                section.metadata.sectionsRequiredBefore ++ section.metadata.sectionsRecommendedBefore
              case _ => Nil
            }
            pending = current.childrenOfThisElement ++ prerequisites ++ pending
        }
      }
      Snapshot(element.elementId, collected.valuesIterator.map(_.toSerialized).toList)
    }, snapshot => WorkbookElementFactory.parseAllAsMap(snapshot.elements).getOrElse(snapshot.rootId,
      throw new IllegalArgumentException(s"Missing workbook root: ${snapshot.rootId}")))

  private[workbook] def subtypeCodec[T <: WorkbookElement: scala.reflect.ClassTag]: upickle.default.ReadWriter[T] =
    upickle.default.readwriter[WorkbookElement].bimap(
      element => element,
      element => summon[scala.reflect.ClassTag[T]].unapply(element)
        .getOrElse(throw new IllegalArgumentException(s"Unexpected workbook element: ${element.getClass.getSimpleName}")))
}

sealed trait WorkbookElement {
  val elementId: String
  //  assert(elementId.matches("[a-zA-Z0-9.-]+"))
  lazy val asRef = WorkbookElementReference(elementId, Option(this.getClass.getSimpleName))

  lazy val childrenOfThisElement: List[WorkbookElement]
  lazy val allInteractionsInThisInstance: List[WorkbookInteractionUntyped] =
    childrenOfThisElement.flatMap(_.allInteractionsInThisInstance)

  lazy val allChildrenFullSubtree: List[WorkbookElement] = childrenOfThisElement ++ childrenOfThisElement.flatMap(_.allChildrenFullSubtree)

  val associatedFactory: WorkbookElementFactory[? <: WorkbookElement]

  lazy val toSerialized: WorkbookElementSerializable = associatedFactory.toSerializableElementUnsafe(this)

  lazy val toStringRegularJson: String = WorkbookElementFactory.serializerRefBasedJson.serialize(this)
  lazy val toStringConstructorLike: String = WorkbookElementFactory.serializerConstructorLike.serialize(this)

}

trait WorkbookDisplayElement extends WorkbookElement {
  lazy val childrenOfThisElement: List[WorkbookElement] = List()
}

object WorkbookDisplayElement {
  given ReadWriter[WorkbookDisplayElement] = WorkbookElement.subtypeCodec
}

trait WorkbookStructureElement[T <: WorkbookElement] extends WorkbookElement {
  lazy val structureType: WorkbookStructuringType
  override lazy val childrenOfThisElement: List[WorkbookElement] = groupElements

  def groupElements: List[T]

}

object WorkbookStructureElement {
  given [T <: WorkbookElement]: ReadWriter[WorkbookStructureElement[T]] = WorkbookElement.subtypeCodec
}

trait WorkbookInteractionElementFull[T: ReadWriter, GA: ReadWriter, SA: ReadWriter] extends WorkbookElement {

  override lazy val allInteractionsInThisInstance: List[WorkbookInteractionUntyped] =
    List(this) ++ childrenOfThisElement.flatMap(_.allInteractionsInThisInstance)
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

object WorkbookInteractionElementFull {
  given [T, GA, SA]: ReadWriter[WorkbookInteractionElementFull[T, GA, SA]] = WorkbookElement.subtypeCodec
}
