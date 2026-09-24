package it.evadid.workbook.jsonFactory

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.elements.displayElements.{CollapsibleInstructionElement, DisplayLangMapContent, LabeledWorkbookElement}
import it.evadid.workbook.elements.interactionElements.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, MessagingInteraction, TextInteraction}
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.{CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.elements.interactionElements.gpt.GptInteractionElement
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.sortingExercise.SortingInteraction
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.SortingReasonInteraction

import scala.annotation.tailrec
import scala.collection.mutable

object WorkbookElementFactory {

  def simple[T <: WorkbookElement](
      serialize: T => WorkbookElementSerializable,
      deserialize: WorkbookElementSerializable => T
  ): WorkbookElementFactory[T] = new WorkbookElementFactory[T] {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set.empty
    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = Seq.empty
    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): T = deserialize(element)
    override def toSerializableElement(element: T): WorkbookElementSerializable = serialize(element)
  }

  def unsupported[T <: WorkbookElement](elementName: String): WorkbookElementFactory[T] = new WorkbookElementFactory[T] {
    private def fail = throw UnsupportedOperationException(s"$elementName does not support workbook serialization")
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set.empty
    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = Seq.empty
    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): T = fail
    override def toSerializableElement(element: T): WorkbookElementSerializable = fail
  }

  val workbookElementSerializer: Serializer[WorkbookElement] = new Serializer[WorkbookElement]() {
    override def serialize(obj: WorkbookElement): String = {
      WorkbookElementSerializable.serializer.serialize(obj.toSerializableType)
    }

    override def deserialize(str: String): WorkbookElement = {
      parse(WorkbookElementSerializable.serializer.deserialize(str))
    }
  }

  private lazy val knownFactoriesMap: Map[String, WorkbookElementFactory[? <: WorkbookElement]] = Map(
    classOf[Workbook].getSimpleName -> Workbook.factory,
    classOf[LabeledWorkbookElement].getSimpleName -> LabeledWorkbookElement.factory,
    classOf[CollapsibleInstructionElement].getSimpleName -> CollapsibleInstructionElement.factory,
    classOf[DisplayLangMapContent].getSimpleName -> DisplayLangMapContent.factory,
    classOf[TextInteraction].getSimpleName -> TextInteraction.factory,
    classOf[MessagingInteraction].getSimpleName -> MessagingInteraction.factory,
    classOf[LabeledCheckboxInteraction].getSimpleName -> LabeledCheckboxInteraction.factory,
    classOf[LabeledNumberInteraction].getSimpleName -> LabeledNumberInteraction.factory,
    classOf[SketchDownloadInteraction].getSimpleName -> SketchDownloadInteraction.factory,
    classOf[CodeTaskToggleInteraction].getSimpleName -> CodeTaskToggleInteraction.factory,
    classOf[ReorderInteraction.ReorderCodeInteraction].getSimpleName -> ReorderInteraction.ReorderCodeInteraction.factory,
    classOf[ReorderInteraction.ReorderMapIdInteraction].getSimpleName -> ReorderInteraction.ReorderMapIdInteraction.factory,
    classOf[SortingInteraction].getSimpleName -> SortingInteraction.factory,
    classOf[SortingReasonInteraction].getSimpleName -> SortingReasonInteraction.factory,
    classOf[GptInteractionElement].getSimpleName -> GptInteractionElement.factory
  )


  def parse(element: WorkbookElementSerializable, knownElements: Map[String, WorkbookElement] = Map()): WorkbookElement = {
    parseAll(List(element), knownElements).head
  }

  def parseAll(elementsInOrder: List[WorkbookElementSerializable], knownElements: Map[String, WorkbookElement] = Map()): List[WorkbookElement] = {
    parseAll(elementsInOrder, elementsInOrder, knownElements, knownFactoriesMap)._1
  }

  def parseAllAsMap(elementsInOrder: List[WorkbookElementSerializable], knownElements: Map[String, WorkbookElement] = Map()): Map[String, WorkbookElement] = {
    parseAll(elementsInOrder, elementsInOrder, knownElements, knownFactoriesMap)._2
  }

  @tailrec
  private def parseAll(
                        elementsInOrder: List[WorkbookElementSerializable],
                        open: List[WorkbookElementSerializable],
                        alreadyParsed: Map[String, WorkbookElement],
                        knownFactories: Map[String, WorkbookElementFactory[? <: WorkbookElement]]
                      ): (List[WorkbookElement], Map[String, WorkbookElement]) = {
    if (open.isEmpty) {
      val notYetParsed = elementsInOrder.filter(el => !alreadyParsed.contains(el.elementId))
      if (notYetParsed.nonEmpty) throw SerializedException(s"Open is empty but ${notYetParsed} elements were not parsed yet (${notYetParsed.map(_.elementId)}")
      else {
        val resList = elementsInOrder.map(el => alreadyParsed(el.elementId))
        val resMap = alreadyParsed ++ resList.map(el => el.elementId -> el).toMap
        (resList, resMap)
      }
    } else {
      val newlyFinished: mutable.HashMap[String, WorkbookElement] = mutable.HashMap[String, WorkbookElement]()
      val stillOpen: mutable.ListBuffer[WorkbookElementSerializable] = mutable.ListBuffer()
      val newlyProvided: mutable.ListBuffer[WorkbookElementSerializable] = mutable.ListBuffer()

      open.foreach(curOpenElement => {
        val factory = knownFactories.get(curOpenElement.elementType)
        if (factory.isEmpty) {
          throw SerializedException(s"No factory known for WorkbookElement with type ${curOpenElement.elementType}")
        } else {
          newlyProvided ++= factory.get.serializedElementContainsOtherSerializations(curOpenElement)
          if (factory.get.idsRequiredForDeserialization(curOpenElement).forall(alreadyParsed.keySet.contains(_))) {
            val parsed: WorkbookElement = factory.get.fromSerializedElement(curOpenElement, alreadyParsed)
            newlyFinished += parsed.elementId -> parsed
          } else {
            stillOpen += curOpenElement
          }
        }
      })

      if (stillOpen.nonEmpty && newlyFinished.isEmpty && newlyProvided.isEmpty) {
        throw SerializedException(s"Iteration with no progress, likely because of a cyclic dependency, stop parsing! (still open: ${open.map(_.elementId)})")
      } else {
        parseAll(elementsInOrder, stillOpen.toList ++ newlyProvided, alreadyParsed ++ newlyFinished, knownFactories)
      }

    }
  }

}


trait WorkbookElementFactory[T <: WorkbookElement] {

  protected def toFactoryBase(element: T): WorkbookElementSerializable = WorkbookElementSerializable(
    element.elementId, element.getClass.getSimpleName, Map()
  )

  private def associatedSerializer(parsedElements: Map[String, WorkbookElement]): Serializer[T] = new Serializer[T]() {
    override def serialize(obj: T): String = {
      WorkbookElementSerializable.serializer.serialize((toSerializableElement(obj)))
    }

    override def deserialize(str: String): T = {
      fromSerializedElement(WorkbookElementSerializable.serializer.deserialize(str), parsedElements)
    }
  }

  def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String]

  def idsRequiredForDeserialization(element: T): Set[String] = idsRequiredForDeserialization(element.toSerializableType)

  def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable]

  def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): T

  def toSerializableElement(element: T): WorkbookElementSerializable

  def toSerializableElementUnsafe(element: WorkbookElement): WorkbookElementSerializable = {
    toSerializableElement(element.asInstanceOf[T])
  }
}
