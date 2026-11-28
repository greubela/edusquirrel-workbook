package it.evadid.workbook.serialization

import it.evadid.workbook.interactions.compression.CompressionExperimentInteraction
import it.evadid.workbook.interactions.evacuation.EvacuationSimulationInteraction
import it.evadid.workbook.interactions.evacuation.EvacuationConstructFloorInteraction
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.display.WorkbookImageElement.LanguageMapBasedWorkbookImageElement
import it.evadid.workbook.elements.display.{CollapsibleInstructionElement, DisplayLangMapContent, LabeledWorkbookElement, TwoColumnPanel, WorkbookImageElement, WorkbookSubmissionElement}
import it.evadid.workbook.interactions.qr.CreateQrCodeInteraction
import it.evadid.workbook.interactions.basic.{LabeledCheckboxInteraction, LabeledNumberInteraction, MessagingInteraction, TextInteraction}
import it.evadid.workbook.interactions.codeTaskToggle.{CodeTaskToggleInteraction, SketchDownloadInteraction}
import it.evadid.workbook.interactions.gpt.GptInteractionElement
import it.evadid.workbook.interactions.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.workbook.interactions.sql.SqlCommandExercise
import it.evadid.workbook.interactions.reorderExercise.ReorderInteraction
import it.evadid.workbook.interactions.matching.{LikertScaleInteraction, MatchingInteraction}
import it.evadid.workbook.interactions.slideshow.*
import it.evadid.workbook.elements.structure.{ExerciseContainer, ExerciseGroup, Workbook, WorkbookSection}
import it.evadid.workbook.interactions.emailSimulator.{MailEditor, MailInteraction}
import upickle.default
import upickle.default.*

import scala.annotation.tailrec
import it.evadid.workbook.interactions.choice.*
import it.evadid.workbook.interactions.neuron.ThresholdNeuronInteraction
import it.evadid.workbook.interactions.table.AnswerTableInteraction
import it.evadid.workbook.interactions.plot.CoordinatePlotInteraction
import it.evadid.workbook.interactions.pixel.BinaryPixelInteraction
import it.evadid.workbook.interactions.blockchain.SquareMiddleHashInteraction
import it.evadid.workbook.interactions.blockchain.Sha256Interaction
import it.evadid.workbook.interactions.blockchain.BlockchainInteraction
import it.evadid.workbook.interactions.programming.programmingExerciseRegular.*
import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.{TurtleRecreateShapeInteraction, TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteractionLegacy}
import it.evadid.workbook.interactions.sorting.sortingExercise.SortingInteraction
import it.evadid.workbook.interactions.sorting.sortingReasonExercise.SortingReasonInteraction
import it.evadid.workbook.interactions.text.UnicodeComparisonInteraction

object WorkbookElementFactory {

  /** Compatibility base for leaf elements while keeping all serialization metadata in the factory. */
  trait SimpleWorkbookElementFactory[T <: WorkbookElement] extends NoRefsElementFactory[T] {
    protected def constructorFieldOrder: Seq[String]

    override lazy val elementMapAndOrderForConstructorLike: Map[Int, Seq[VariableDisplayConfig]] =
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
    override protected val constructorFieldOrder: Seq[String] = List("elementId")

    override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: T): WorkbookElementSerializable = baseElement

    override def finishDeserialization(element: WorkbookElementSerializable): T = callConstructor(element.elementId)

    def callConstructor(elementId: String): T
  }

  trait SingleContentElementFactory[T <: WorkbookElement] extends SimpleWorkbookElementFactory[T] {
    override protected val constructorFieldOrder: Seq[String] = List("elementId", "content")

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

  private lazy val knownFactoriesMap: Map[String, WorkbookElementFactory[? <: WorkbookElement]] = Map(
    classOf[TwoColumnPanel].getSimpleName -> TwoColumnPanel.factory,
    classOf[ExerciseGroup].getSimpleName -> ExerciseGroup.factory,
    classOf[CompressionExperimentInteraction].getSimpleName -> CompressionExperimentInteraction.factory,
    classOf[CreateQrCodeInteraction].getSimpleName -> CreateQrCodeInteraction.factory,
    classOf[MailInteraction].getSimpleName -> MailInteraction.factory,
    classOf[WorkbookContentChoiceInteraction].getSimpleName -> WorkbookContentChoiceInteraction.factory,
    classOf[AnswerTableInteraction].getSimpleName -> AnswerTableInteraction.factory,
    classOf[CoordinatePlotInteraction].getSimpleName -> CoordinatePlotInteraction.factory,
    classOf[BinaryPixelInteraction].getSimpleName -> BinaryPixelInteraction.factory,
    classOf[SquareMiddleHashInteraction].getSimpleName -> SquareMiddleHashInteraction.factory,
    classOf[Sha256Interaction].getSimpleName -> Sha256Interaction.factory,
    classOf[BlockchainInteraction].getSimpleName -> BlockchainInteraction.factory,
    classOf[EvacuationSimulationInteraction].getSimpleName -> EvacuationSimulationInteraction.factory,
    classOf[EvacuationConstructFloorInteraction].getSimpleName -> EvacuationConstructFloorInteraction.factory,
    classOf[UnicodeComparisonInteraction].getSimpleName -> UnicodeComparisonInteraction.factory,
    classOf[ThresholdNeuronInteraction].getSimpleName -> ThresholdNeuronInteraction.factory,
    classOf[MailEditor].getSimpleName -> MailEditor.factory,
    classOf[Workbook].getSimpleName -> Workbook.factory,
    classOf[LabeledWorkbookElement[?]].getSimpleName -> LabeledWorkbookElement.factory,
    classOf[CollapsibleInstructionElement].getSimpleName -> CollapsibleInstructionElement.factory,
    classOf[DisplayLangMapContent].getSimpleName -> DisplayLangMapContent.factory,
    classOf[WorkbookSubmissionElement].getSimpleName -> WorkbookSubmissionElement.factory,
    classOf[TextInteraction].getSimpleName -> TextInteraction.factory,
    classOf[MessagingInteraction].getSimpleName -> MessagingInteraction.factory,
    classOf[LabeledCheckboxInteraction].getSimpleName -> LabeledCheckboxInteraction.factory,
    classOf[LabeledNumberInteraction].getSimpleName -> LabeledNumberInteraction.factory,
    classOf[SketchDownloadInteraction].getSimpleName -> SketchDownloadInteraction.factory,
    classOf[CodeTaskToggleInteraction].getSimpleName -> CodeTaskToggleInteraction.factory,
    classOf[ReorderInteraction.ReorderCodeInteraction].getSimpleName -> ReorderInteraction.ReorderCodeInteraction.factory,
    classOf[ReorderInteraction.ReorderMapIdInteraction].getSimpleName -> ReorderInteraction.ReorderMapIdInteraction.factory,
    classOf[MatchingInteraction].getSimpleName -> MatchingInteraction.factory,
    classOf[LikertScaleInteraction].getSimpleName -> LikertScaleInteraction.factory,
    classOf[SortingInteraction].getSimpleName -> SortingInteraction.factory,
    classOf[SortingReasonInteraction].getSimpleName -> SortingReasonInteraction.factory,
    classOf[GptInteractionElement].getSimpleName -> GptInteractionElement.factory,
    classOf[TextInputWithScaffoldingAndGrading].getSimpleName -> TextInputWithScaffoldingAndGrading.factory,
    classOf[WorkbookSection].getSimpleName -> WorkbookSection.factory,
    classOf[ExerciseContainer].getSimpleName -> ExerciseContainer.factory,
    classOf[Slideshow].getSimpleName -> Slideshow.factory,
    classOf[TurtleRecreateShapeInteraction].getSimpleName -> TurtleRecreateShapeInteraction.factory,
    classOf[TurtleStitchExploreProjectElement].getSimpleName -> TurtleStitchExploreProjectElement.factory,
    classOf[TurtleStitchRecreateShapeInteractionLegacy].getSimpleName -> TurtleStitchRecreateShapeInteractionLegacy.factory,
    classOf[SqlCommandExercise].getSimpleName -> SqlCommandExercise.factory,
    classOf[ProgrammingExercise].getSimpleName -> ProgrammingExercise.factory,
    classOf[ProgrammingExerciseFullJava].getSimpleName -> ProgrammingExerciseFullJava.factory,
    classOf[SortListExercise].getSimpleName -> SortListExercise.factory,
    //  classOf[ImageElement.FileBasedImageElement].getSimpleName -> ImageElement.FileBasedImageElement.factory,
    classOf[WorkbookImageElement.LanguageMapBasedWorkbookImageElement].getSimpleName -> LanguageMapBasedWorkbookImageElement.factory
  )

  private[workbook] def registeredElementTypes: Set[String] = knownFactoriesMap.keySet

  private[workbook] def factoryFor(elementType: String): WorkbookElementFactory[? <: WorkbookElement] =
    knownFactoriesMap.getOrElse(elementType, throw SerializedException(s"No factory known for WorkbookElement with type $elementType"))

  def parse(element: WorkbookElementSerializable, knownElements: Map[String, WorkbookElement] = Map()): WorkbookElement = {
    parseAll(List(element), knownElements).head
  }

  def parseAll(elementsInOrder: Seq[WorkbookElementSerializable], knownElements: Map[String, WorkbookElement] = Map()): Seq[WorkbookElement] = {
    parseAll(elementsInOrder, elementsInOrder, knownElements, knownFactoriesMap, Set.empty[String])._1
  }

  def parseAllAsMap(elementsInOrder: Seq[WorkbookElementSerializable], knownElements: Map[String, WorkbookElement] = Map()): Map[String, WorkbookElement] = {
    parseAll(elementsInOrder, elementsInOrder, knownElements, knownFactoriesMap, Set.empty[String])._2
  }

  /** The outcome of one pass over the open elements. */
  private final case class ParsePass(
      finished: Map[String, WorkbookElement] = Map.empty,
      stillOpen: Vector[WorkbookElementSerializable] = Vector.empty,
      provided: Vector[WorkbookElementSerializable] = Vector.empty,
      expandedIds: Set[String]
  )

  @tailrec
  private def parseAll(
                        elementsInOrder: Seq[WorkbookElementSerializable],
                        open: Seq[WorkbookElementSerializable],
                        alreadyParsed: Map[String, WorkbookElement],
                        knownFactories: Map[String, WorkbookElementFactory[? <: WorkbookElement]],
                        expandedRegistryIds: Set[String]
                      ): (Seq[WorkbookElement], Map[String, WorkbookElement]) = {
    if (open.isEmpty) {
      val notYetParsed = elementsInOrder.filter(el => !alreadyParsed.contains(el.elementId))
      if (notYetParsed.nonEmpty) throw SerializedException(s"Open is empty but ${notYetParsed} elements were not parsed yet (${notYetParsed.map(_.elementId)}")
      else {
        val resList = elementsInOrder.map(el => alreadyParsed(el.elementId))
        val resMap = alreadyParsed ++ resList.map(el => el.elementId -> el).toMap
        (resList, resMap)
      }
    } else {
      val pass = open.foldLeft(ParsePass(expandedIds = expandedRegistryIds))(parseStep(knownFactories, alreadyParsed))
      if (pass.stillOpen.nonEmpty && pass.finished.isEmpty && pass.provided.isEmpty) {
        throw SerializedException(s"Iteration with no progress, likely because of a cyclic dependency, stop parsing! (still open: ${open.map(_.elementId)})")
      } else {
        parseAll(elementsInOrder, pass.stillOpen.toList ++ pass.provided, alreadyParsed ++ pass.finished, knownFactories, pass.expandedIds)
      }
    }
  }

  /** Provides an element's nested serializations the first time its ID is seen, then parses it if all its references are resolved. */
  private def parseStep(knownFactories: Map[String, WorkbookElementFactory[? <: WorkbookElement]], alreadyParsed: Map[String, WorkbookElement])
      (pass: ParsePass, element: WorkbookElementSerializable): ParsePass = {
    val factory = knownFactories.getOrElse(element.elementType,
      throw SerializedException(s"No factory known for WorkbookElement with type ${element.elementType}"))
    val expanded =
      if pass.expandedIds.contains(element.elementId) then pass
      else pass.copy(
        provided = pass.provided ++ factory.serializedElementKeysThatContainOtherSerializations(element).flatMap(element.getElementsAsSerializableElement),
        expandedIds = pass.expandedIds + element.elementId)
    if (factory.idsRequiredForDeserialization(element).forall(alreadyParsed.contains)) {
      val parsed: WorkbookElement = factory.fromSerializedElement(element, alreadyParsed)
      expanded.copy(finished = expanded.finished + (parsed.elementId -> parsed))
    } else expanded.copy(stillOpen = expanded.stillOpen :+ element)
  }
}

trait WorkbookElementFactory[T <: WorkbookElement] {

  lazy val elementMapAndOrderForConstructorLike: Map[Int, Seq[VariableDisplayConfig]] =
    Map(0 -> List(VariableDisplayConfig("elementId", true)))

  lazy val writerJsonRegularRefBased: Writer[T] = writer[ujson.Value].comap { element =>
    ujson.Obj.from(Map("elementId" -> writeJs(element.elementId)) ++ toSerializableElement(element).allConstructorFields)
  }

  def addElementsToSerialization(element: T): Map[String, ujson.Value] = Map.empty

  protected def toFactoryBase(element: T): WorkbookElementSerializable =
    WorkbookElementSerializable(element.elementId, element.getClass.getSimpleName, Map.empty)

  def toSerializableElement(element: T): WorkbookElementSerializable = {
    val res = WorkbookElementSerializable.getSerializedVersion(element)(using writerJsonRegularRefBased)
    res.copy(allConstructorFields = res.allConstructorFields ++ addElementsToSerialization(element))
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

  def serializedElementKeysThatContainOtherSerializations(element: WorkbookElementSerializable): Set[String] = Set.empty

  def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): T

  def toSerializableElementUnsafe(element: WorkbookElement): WorkbookElementSerializable = {
    toSerializableElement(element.asInstanceOf[T])
  }

  def toStringConstructorLikeUnsafe(element: WorkbookElement): String =
    toStringConstructorLike(element.asInstanceOf[T])
}
