package it.evadid.workbook.jsonFactory

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.ImageElement.LanguageMapBasedImageElement
import it.evadid.workbook.elements.displayElements.{CollapsibleInstructionElement, DisplayLangMapContent, ImageElement, LabeledWorkbookElement}
import it.evadid.workbook.elements.interactionElements.Turtle.{TurtleRecreateShapeInteraction, TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteractionLegacy}
import it.evadid.workbook.elements.interactionElements.qr.CreateQrCodeInteraction
import it.evadid.workbook.elements.interactionElements.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, MessagingInteraction, TextInteraction}
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.{CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.elements.interactionElements.gpt.GptInteractionElement
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingExercise, ProgrammingExerciseFullJava}
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.{Slideshow, SlideshowPanel}
import it.evadid.workbook.elements.interactionElements.sortingExercise.SortingInteraction
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.SortingReasonInteraction
import it.evadid.workbook.elements.structureElements.{ExerciseContainer, Workbook, WorkbookSection}
import it.evadid.workbook.elements.interactionElements.emailSimulator.{MailInteraction, MailEditor}
import upickle.default
import upickle.default.*

import scala.annotation.tailrec
import scala.collection.mutable

import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.neuron.ThresholdNeuronInteraction

import it.evadid.workbook.elements.interactionElements.table.AnswerTableInteraction

object WorkbookElementFactory {

  /** Compatibility base for leaf elements while keeping all serialization metadata in the factory. */
  trait SimpleWorkbookElementFactory[T <: WorkbookElement] extends NoRefsElementFactory[T] {
    protected def constructorFieldOrder: List[String]

    override lazy val elementMapAndOrderForConstructorLike: Map[Int, List[VariableDisplayConfig]] =
      constructorFieldOrder.zipWithIndex.map { case (field, index) =>
        index -> List(VariableDisplayConfig(field, index == 0))
      }.toMap

    override lazy val writerJsonRegularRefBased: Writer[T] =
      writer[ujson.Value].comap { element =>
        val base = WorkbookElementSerializable(element.elementId, element.getClass.getSimpleName, Map.empty)
        ujson.Obj.from(Map("elementId" -> writeJs(element.elementId)) ++ finishSerialization(base, element).allConstructorFields)
      }

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): T =
      finishDeserialization(element)

    def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: T): WorkbookElementSerializable

    def finishDeserialization(element: WorkbookElementSerializable): T
  }

  trait NoContentElementFactory[T <: WorkbookElement] extends SimpleWorkbookElementFactory[T] {
    override protected val constructorFieldOrder: List[String] = List("elementId")

    override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: T): WorkbookElementSerializable = baseElement

    override def finishDeserialization(element: WorkbookElementSerializable): T = callConstructor(element.elementId)

    def callConstructor(elementId: String): T
  }

  trait SingleContentElementFactory[T <: WorkbookElement] extends SimpleWorkbookElementFactory[T] {
    override protected val constructorFieldOrder: List[String] = List("elementId", "content")

    def readContent(infoElement: T): it.evadid.core.datastructures.language.LanguageMapContentId

    override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: T): WorkbookElementSerializable =
      baseElement.withElementAddedAs("content", readContent(infoElement))

    override def finishDeserialization(element: WorkbookElementSerializable): T =
      finishDeserialization(element.elementId, element.getElementAs("content"))

    def finishDeserialization(elementId: String, content: it.evadid.core.datastructures.language.LanguageMapContentId): T
  }

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

  val serializerRegularJsonWorkbook: Serializer[Workbook] = serializerRefBasedJson.map(_.asInstanceOf[Workbook], _.asInstanceOf[WorkbookElement])

  val serializerConstructorLike: Serializer[WorkbookElement] = new Serializer[WorkbookElement]() {
    override def serialize(obj: WorkbookElement): String = {
      obj.associatedFactory.toStringConstructorLikeUnsafe(obj)
    }

    override def deserialize(str: String): WorkbookElement = {
      val ser = WorkbookElementSerializable.fromStringConstructorLike(str)
      parse(ser, Map())
    }
  }

  trait NoRefsElementFactory[T <: WorkbookElement] extends WorkbookElementFactory[T] {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set()

    override def serializedElementKeysThatContainOtherSerializations(element: WorkbookElementSerializable): Set[String] = Set()

    override def addElementsToSerialization(element: T): Map[String, ujson.Value] = Map()
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


    override def addElementsToSerialization(element: T): Map[String, ujson.Value] = Map()
  }


  private lazy val knownFactoriesMap: Map[String, WorkbookElementFactory[? <: WorkbookElement]] = Map(
    classOf[CreateQrCodeInteraction].getSimpleName -> CreateQrCodeInteraction.factory,
    classOf[MailInteraction].getSimpleName -> MailInteraction.factory,
    classOf[ChoiceInteraction].getSimpleName -> ChoiceInteraction.factory,
    classOf[AnswerTableInteraction].getSimpleName -> AnswerTableInteraction.factory,
    classOf[ThresholdNeuronInteraction].getSimpleName -> ThresholdNeuronInteraction.factory,
    classOf[MailEditor].getSimpleName -> MailEditor.factory,
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
    classOf[TurtleRecreateShapeInteraction].getSimpleName -> TurtleRecreateShapeInteraction.factory,
    classOf[TurtleStitchExploreProjectElement].getSimpleName -> TurtleStitchExploreProjectElement.factory,
    classOf[TurtleStitchRecreateShapeInteractionLegacy].getSimpleName -> TurtleStitchRecreateShapeInteractionLegacy.factory,
    classOf[ProgrammingExercise].getSimpleName -> ProgrammingExercise.factory,
    classOf[ProgrammingExerciseFullJava].getSimpleName -> ProgrammingExerciseFullJava.factory,
    //  classOf[ImageElement.FileBasedImageElement].getSimpleName -> ImageElement.FileBasedImageElement.factory,
    classOf[ImageElement.LanguageMapBasedImageElement].getSimpleName -> LanguageMapBasedImageElement.factory
  )

  private[workbook] def registeredElementTypes: Set[String] = knownFactoriesMap.keySet

  private[workbook] def factoryFor(elementType: String): WorkbookElementFactory[? <: WorkbookElement] =
    knownFactoriesMap.getOrElse(elementType, throw SerializedException(s"No factory known for WorkbookElement with type $elementType"))

  def parse(element: WorkbookElementSerializable, knownElements: Map[String, WorkbookElement] = Map()): WorkbookElement = {
    parseAll(List(element), knownElements).head
  }

  def parseAll(elementsInOrder: List[WorkbookElementSerializable], knownElements: Map[String, WorkbookElement] = Map()): List[WorkbookElement] = {
    parseAll(elementsInOrder, elementsInOrder, knownElements, knownFactoriesMap, Set.empty[String])._1
  }

  def parseAllAsMap(elementsInOrder: List[WorkbookElementSerializable], knownElements: Map[String, WorkbookElement] = Map()): Map[String, WorkbookElement] = {
    parseAll(elementsInOrder, elementsInOrder, knownElements, knownFactoriesMap, Set.empty[String])._2
  }

  @tailrec
  private def parseAll(
                        elementsInOrder: List[WorkbookElementSerializable],
                        open: List[WorkbookElementSerializable],
                        alreadyParsed: Map[String, WorkbookElement],
                        knownFactories: Map[String, WorkbookElementFactory[? <: WorkbookElement]],
                        expandedRegistryIds: Set[String]
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

      val expanded = mutable.HashSet.from(expandedRegistryIds)

      open.foreach(curOpenElement => {
        val factory = knownFactories.get(curOpenElement.elementType)
        if (factory.isEmpty) {
          throw SerializedException(s"No factory known for WorkbookElement with type ${curOpenElement.elementType}")
        } else if (expanded.add(curOpenElement.elementId)) {
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
        parseAll(elementsInOrder, stillOpen.toList ++ newlyProvided, alreadyParsed ++ newlyFinished, knownFactories, expanded.toSet)
      }

    }
  }
}

trait WorkbookElementFactory[T <: WorkbookElement] {

  lazy val elementMapAndOrderForConstructorLike: Map[Int, List[VariableDisplayConfig]] =
    Map(0 -> List(VariableDisplayConfig("elementId", true)))

  lazy val writerJsonRegularRefBased: Writer[T] = writer[ujson.Value].comap { element =>
    ujson.Obj.from(Map("elementId" -> writeJs(element.elementId)) ++ toSerializableElement(element).allConstructorFields)
  }
  /*
  element.getElementsAs[WorkbookElementSerializable]("serializedElements")(using )
   */

  def addElementsToSerialization(element: T): Map[String, ujson.Value] = Map.empty

  protected def toFactoryBase(element: T): WorkbookElementSerializable =
    WorkbookElementSerializable(element.elementId, element.getClass.getSimpleName, Map.empty)

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
    val serialized = toSerializableElement(element)
    ConstructorLikeSerializer.serializeFields(
      elementMapAndOrderForConstructorLike,
      serialized.allConstructorFields + ("elementId" -> writeJs(serialized.elementId)),
      serialized.elementType
    )
  }

  def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String]

  def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = Seq.empty

  def serializedElementKeysThatContainOtherSerializations(element: WorkbookElementSerializable): Set[String] = Set.empty

  def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): T

  def toSerializableElementUnsafe(element: WorkbookElement): WorkbookElementSerializable = {
    toSerializableElement(element.asInstanceOf[T])
  }

  def toStringConstructorLikeUnsafe(element: WorkbookElement): String =
    toStringConstructorLike(element.asInstanceOf[T])
}
