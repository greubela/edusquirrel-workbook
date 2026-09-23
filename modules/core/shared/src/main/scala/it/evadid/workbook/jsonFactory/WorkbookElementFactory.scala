package it.evadid.workbook.jsonFactory

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.*
import it.evadid.workbook.elements.interactionElements.TurtleStitch.{TurtleStitchExploreProjectElement, TurtleStitchRecreateShapeInteraction}
import it.evadid.workbook.elements.interactionElements.basic.*
import it.evadid.workbook.elements.interactionElements.codeTaskToggle.*
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.gpt.GptInteractionElement
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.{Slideshow, SlideshowPanel}
import it.evadid.workbook.elements.interactionElements.sortingExercise.SortingInteraction
import it.evadid.workbook.elements.interactionElements.sortingReasonExercise.SortingReasonInteraction
import it.evadid.workbook.elements.structureElements.*
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.{refRW, refRWL, *}
import upickle.{ReadWriter, default, macroRW, readwriter}

object WorkbookElementFactory {

  val knownFactories: Map[String, WorkbookElementFactory => WorkbookElement] = Map(
    classOf[TurtleStitchRecreateShapeInteraction].getSimpleName -> TurtleStitchRecreateShapeInteraction.fromFactory,
    classOf[TurtleStitchExploreProjectElement].getSimpleName -> TurtleStitchExploreProjectElement.fromFactory,
    classOf[LabeledCheckboxInteraction].getSimpleName -> LabeledCheckboxInteraction.fromFactory,
    classOf[LabeledNumberInteraction].getSimpleName -> LabeledNumberInteraction.fromFactory,
    classOf[MessagingInteraction].getSimpleName -> MessagingInteraction.fromFactory,
    classOf[TextInteraction].getSimpleName -> TextInteraction.fromFactory,
    classOf[SketchDownloadInteraction].getSimpleName -> SketchDownloadInteraction.fromFactory,
    classOf[CodeTaskToggleInteraction].getSimpleName -> CodeTaskToggleInteraction.fromFactory,
    classOf[ReorderInteraction.ReorderCodeInteraction].getSimpleName -> ReorderInteraction.ReorderCodeInteraction.fromFactory,
    classOf[ReorderInteraction.ReorderMapIdInteraction].getSimpleName -> ReorderInteraction.ReorderMapIdInteraction.fromFactory,
    classOf[ProgrammingExercise].getSimpleName -> ProgrammingExercise.fromFactory,
    classOf[GptInteractionElement].getSimpleName -> GptInteractionElement.fromFactory,
    classOf[SortingInteraction].getSimpleName -> SortingInteraction.fromFactory,
    classOf[SortingReasonInteraction].getSimpleName -> SortingReasonInteraction.fromFactory,
    classOf[Slideshow].getSimpleName -> Slideshow.fromFactory,
    classOf[SlideshowPanel.TwoColumnImagePanel].getSimpleName -> SlideshowPanel.TwoColumnImagePanel.fromFactory,
    classOf[SlideshowPanel.ImageSlide].getSimpleName -> SlideshowPanel.ImageSlide.fromFactory,
    classOf[DisplayLangMapContent].getSimpleName -> DisplayLangMapContent.fromFactory,
    classOf[CollapsibleInstructionElement].getSimpleName -> CollapsibleInstructionElement.fromFactory,
    classOf[ImageElement.FileBasedImageElement].getSimpleName -> ImageElement.FileBasedImageElement.fromFactory,
    classOf[ImageElement.LanguageMapBasedImageElement].getSimpleName -> ImageElement.LanguageMapBasedImageElement.fromFactory,
    classOf[LabeledWorkbookElement[?]].getSimpleName -> LabeledWorkbookElement.fromFactory,
    classOf[ExerciseContainer].getSimpleName -> ExerciseContainer.fromFactory,
    classOf[WorkbookSection].getSimpleName -> WorkbookSection.fromFactory,
    classOf[Workbook].getSimpleName -> Workbook.fromFactory
  )

  def materialize(factory: WorkbookElementFactory): WorkbookElement =
    knownFactories.getOrElse(factory.elementType, throw SerializedException(s"Unknown workbook element type '${factory.elementType}'."))(factory)

  val prefix = "WorkbookElementFactory"

  /* val serializerPretty: Serializer[WorkbookElementFactory] = new Serializer[WorkbookElementFactory] {

   override def serialize(obj: WorkbookElementFactory): String = {
      val keyValuePairs = obj.additionalElements.toList.map(el => el._1 + " -> " + el._2)
      val mapString = keyValuePairs.mkString(", ")
      s"${prefix}(${obj.elementType},${obj.elementId}):${mapString})"
    }

    override def deserialize(str: String): WorkbookElementFactory = {
      val trimmed = str.trim
      if (trimmed.length <= prefix.length + 1 || !trimmed.startsWith(prefix) || !trimmed.endsWith(")"))
        throw SerializedException(s"WorkbookElementFactory json should start with ${prefix}")
      else {
        val withoutPrefix = trimmed
          .substring(0, trimmed.length - 1)
          .substring(prefix.length + 1, trimmed.length - 2)

      }
    }
  }*/

  private given refRW: default.ReadWriter[WorkbookElementReference] = macroRW
  //private given refRWL: default.ReadWriter[List[WorkbookElementReference]] = macroRW

  private given refRWL: ReadWriter[List[WorkbookElementReference]] =
    readwriter[List[WorkbookElementReference]].bimap[List[WorkbookElementReference]](_.toSeq, _.toList)


  given facRW: default.ReadWriter[WorkbookElementFactory] = macroRW
  val serializer: Serializer[WorkbookElementFactory] = Serializer.fromUpickleJson(facRW)
}


case class WorkbookElementFactory(
                                   elementId: String,
                                   elementType: String,
                                   additionalElements: Map[String, String]
                                 ) {

  def withElementAdded(key: String, value: String): WorkbookElementFactory = {
    withMapAdded(Map(key -> value))
  }

  def withMapAdded(map: Map[String, String]): WorkbookElementFactory = {
    WorkbookElementFactory(elementId, elementType, additionalElements ++ map)
  }

  def withElementAdded[T](key: String, element: T)(implicit rw: ReadWriter[T]): WorkbookElementFactory = {
    withElementAdded(key, element)(Serializer.fromUpickleJson(rw))
  }

  def withElementAdded[T](key: String, element: T)(serializer: Serializer[T]): WorkbookElementFactory = {
    WorkbookElementFactory(elementId, elementType, additionalElements ++ Map(key -> serializer.serialize(element)))
  }

  def withContentIdAdded(key: String, element: LanguageMapContentId): WorkbookElementFactory = {
    withElementAdded(key, element)(LanguageMapContentId.serializer)
  }

  def withElementsAdded(key: String, workbookElements: Seq[WorkbookElement]): WorkbookElementFactory = {
    withReferencesAdded(key, workbookElements.map(_.asRef))
  }

  def withElementAdded(key: String, workbookElement: WorkbookElement): WorkbookElementFactory = {
    withReferenceAdded(key, workbookElement.asRef)
  }

  def withReferenceAdded(key: String, workbookElement: WorkbookElementReference): WorkbookElementFactory = {
    withElementAdded(key, workbookElement)(using refRW)
  }

  def withReferencesAdded(key: String, workbookElement: Seq[WorkbookElementReference]): WorkbookElementFactory = {
    withElementAdded(key, workbookElement.toList)(using refRWL)
  }

  def withSerializedElementAdded(key: String, workbookElement: WorkbookElement): WorkbookElementFactory =
    withElementAdded(key, workbookElement.toSerializableType)(WorkbookElementFactory.serializer)

  def withSerializedElementsAdded(key: String, workbookElements: Seq[WorkbookElement]): WorkbookElementFactory =
    withElementAdded(key, workbookElements.map(_.toSerializableType).toList)(Serializer.fromUpickleJson(summon[ReadWriter[List[WorkbookElementFactory]]]))


  def getElementAsString(elementKey: String): String = additionalElements(elementKey)

  def getElementAs[T](elementKey: String)(serializer: Serializer[T]): T = {
    val elementValue = additionalElements.get(elementKey)
    if (elementValue.isEmpty) {
      throw SerializedException(s"Key ${elementKey} not present in Element ${elementId}!")
    } else try {
      serializer.deserialize(elementValue.get)
    } catch case (err: Throwable) => {
      throw SerializedException(s"Error at deserializing key ${elementKey} from Element ${elementId} (value ${elementValue.get})", Some(err))
    }
  }

  def getElementAsContentId(elementKey: String): LanguageMapContentId = {
    getElementAs[LanguageMapContentId](elementKey)(LanguageMapContentId.serializer)
  }

  def getElementAsSerializedElement(elementKey: String): WorkbookElement =
    WorkbookElementFactory.materialize(getElementAs[WorkbookElementFactory](elementKey)(WorkbookElementFactory.serializer))

  def getElementAsSerializedElements(elementKey: String): List[WorkbookElement] =
    getElementAs[List[WorkbookElementFactory]](elementKey)(Serializer.fromUpickleJson(summon[ReadWriter[List[WorkbookElementFactory]]])).map(WorkbookElementFactory.materialize)


}
