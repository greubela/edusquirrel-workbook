package it.evadid.workbook.jsonFactory

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.ImageElement.LanguageMapBasedImageElement
import it.evadid.workbook.elements.displayElements.{CollapsibleInstructionElement, DisplayLangMapContent, ImageElement, LabeledWorkbookElement}
import it.evadid.workbook.elements.interactionElements.TurtleStitch.{TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteraction}
import it.evadid.workbook.elements.interactionElements.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, MessagingInteraction, TextInteraction}
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.{CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.elements.interactionElements.gpt.GptInteractionElement
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.{Slideshow, SlideshowPanel}
import it.evadid.workbook.elements.interactionElements.sortingExercise.SortingInteraction
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.SortingReasonInteraction
import it.evadid.workbook.elements.structureElements.{ExerciseContainer, Workbook, WorkbookSection}
import upickle.default
import upickle.default.*

import scala.annotation.tailrec
import scala.collection.mutable

object WorkbookElementFactory {

  val serializerRefBasedJson: Serializer[WorkbookElement] = new Serializer[WorkbookElement]() {
    override def serialize(obj: WorkbookElement): String = {
      val ser = obj.associatedFactory.toSerializableElementUnsafe(obj)
      write(ser)(using WorkbookElementSerializable.regularSerializer)
    }

    override def deserialize(str: String): WorkbookElement = {
      val ser = read(str)(using WorkbookElementSerializable.regularSerializer)
      parse(ser, Map())
    }
  }

  val serializerConstructorLike: Serializer[WorkbookElement] = new Serializer[WorkbookElement]() {
    override def serialize(obj: WorkbookElement): String = {
      val ser = obj.associatedFactory.toSerializableElementUnsafe(obj)
      val ord = obj.associatedFactory.elementMapAndOrderForConstructorLike
      val wri = obj.associatedFactory.writerJsonRegularRefBased
      val con = obj.getClass.getSimpleName
      val res = ConstructorLikeSerializer.serialize(ord, obj, wri, con)
      res
    }

    override def deserialize(str: String): WorkbookElement = {
      val ser = WorkbookElementSerializable.fromStringConstructorLike(str)
      parse(ser, Map())
    }
  }

  trait NoRefsElementFactory[T <: WorkbookElement] extends WorkbookElementFactory[T] {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set()

    override def serializedElementKeysThatContainOtherSerializations(element: WorkbookElementSerializable): Set[String] = Set()

    def addElementsToSerialization(element: T): Map[String, ujson.Value] = Map()
  }

  def unsupportedFactory[T <: WorkbookElement](elementName: String): WorkbookElementFactory[T] = new WorkbookElementFactory[T] {
    private def unsupported(operation: String): Nothing =
      throw new UnsupportedOperationException(s"$elementName does not support workbook $operation")

    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set.empty

    override def serializedElementKeysThatContainOtherSerializations(element: WorkbookElementSerializable): Set[String] = Set()

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): T = unsupported("deserialization")

    override def toSerializableElement(element: T): WorkbookElementSerializable = unsupported("serialization")

    override lazy val elementMapAndOrderForConstructorLike: Map[Int, List[VariableDisplayConfig]] = ???
    override lazy val writerJsonRegularRefBased: default.Writer[T] = ???


    def addElementsToSerialization(element: T): Map[String, ujson.Value] = Map()
  }


  private lazy val knownFactoriesMap: Map[String, WorkbookElementFactory[? <: WorkbookElement]] = Map(
    classOf[Workbook].getSimpleName -> Workbook.factory,
    classOf[LabeledWorkbookElement[?]].getSimpleName -> LabeledWorkbookElement.factory,
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
    classOf[GptInteractionElement].getSimpleName -> GptInteractionElement.factory,
    classOf[WorkbookSection].getSimpleName -> WorkbookSection.factory,
    classOf[ExerciseContainer].getSimpleName -> ExerciseContainer.factory,
    classOf[Slideshow].getSimpleName -> Slideshow.factory,
    classOf[SlideshowPanel.TwoColumnImagePanel].getSimpleName -> SlideshowPanel.TwoColumnImagePanel.factory,
    classOf[SlideshowPanel.ImageSlide].getSimpleName -> SlideshowPanel.ImageSlide.factory,
    classOf[TurtleStitchExploreProjectElement].getSimpleName -> TurtleStitchExploreProjectElement.factory,
    classOf[TurtleStitchRecreateShapeInteraction].getSimpleName -> TurtleStitchRecreateShapeInteraction.factory,
    classOf[ProgrammingExercise].getSimpleName -> ProgrammingExercise.factory,
    //  classOf[ImageElement.FileBasedImageElement].getSimpleName -> ImageElement.FileBasedImageElement.factory,
    classOf[ImageElement.LanguageMapBasedImageElement].getSimpleName -> LanguageMapBasedImageElement.factory
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
          newlyProvided ++= factory.get.serializedElementKeysThatContainOtherSerializations(curOpenElement).flatMap(curOtherKey => {
            curOpenElement.getElementsAsSerializableElement(curOtherKey)
          })
        }
        if (factory.get.idsRequiredForDeserialization(curOpenElement).forall(alreadyParsed.keySet.contains(_))) {
          val parsed: WorkbookElement = factory.get.fromSerializedElement(curOpenElement, alreadyParsed)
          newlyFinished += parsed.elementId -> parsed
        } else {
          stillOpen += curOpenElement
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

  lazy val elementMapAndOrderForConstructorLike: Map[Int, List[VariableDisplayConfig]]

  lazy val writerJsonRegularRefBased: Writer[T]
  /*
  element.getElementsAs[WorkbookElementSerializable]("serializedElements")(using )
   */

  def addElementsToSerialization(element: T): Map[String, ujson.Value]

  def toSerializableElement(element: T): WorkbookElementSerializable = {
    // val map = WorkbookElementSerializable.getAutoFieldsMap(element)(using writerJsonRegularRefBased)
    // WorkbookElementSerializable(element.elementId, element.getClass.getSimpleName, map)
    val res = WorkbookElementSerializable.getSerializedVersion(element)(using writerJsonRegularRefBased)
    res.copy(allConstructorFields = res.allConstructorFields ++ addElementsToSerialization(element))
  }

  def toStringRefBasedJson(element: T): String = {
    write(toSerializableElement(element))(using WorkbookElementSerializable.regularSerializer)
  }

  def toStringConstructorLike(element: T): String = {
    ConstructorLikeSerializer.serialize(elementMapAndOrderForConstructorLike, element, writerJsonRegularRefBased, element.getClass.getSimpleName)
  }

  def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String]

  def serializedElementKeysThatContainOtherSerializations(element: WorkbookElementSerializable): Set[String]

  def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): T

  def toSerializableElementUnsafe(element: WorkbookElement): WorkbookElementSerializable = {
    toSerializableElement(element.asInstanceOf[T])
  }
}
