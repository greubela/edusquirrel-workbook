package it.evadid.workbook.jsonFactory

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable.{refRW, refRWL, serializerR, serializerRL}
import upickle.{ReadWriter, default, macroRW, readwriter}

import scala.annotation.tailrec
import scala.collection.mutable

object WorkbookElementSerializable {

  def parseAll(elementsInOrder: List[WorkbookElementSerializable]): List[WorkbookElement] = {
    parseAll(elementsInOrder, elementsInOrder, Map(), allKnownFactories)
  }

  @tailrec
  private def parseAll(
                        elementsInOrder: List[WorkbookElementSerializable],
                        open: List[WorkbookElementSerializable],
                        alreadyParsed: Map[String, WorkbookElement],
                        knownFactories: Map[String, WorkbookElementFactory[WorkbookElement]]
                      ): List[WorkbookElement] = {
    if (open.isEmpty) {
      val notYetParsed = open.filter(el => !alreadyParsed.contains(el.elementId))
      if (notYetParsed.nonEmpty) throw SerializedException(s"Open is empty but ${notYetParsed} elements were not parsed yet (${notYetParsed.map(_.elementId)}")
      else elementsInOrder.map(el => alreadyParsed(el.elementId))
    } else {

      def tryParse(element: WorkbookElementSerializable): Option[WorkbookElement] = {
        val factory: Option[WorkbookElementFactory[WorkbookElement]] = knownFactories.get(element.elementType)
        if (factory.isEmpty) throw SerializedException(s"No factory known for WorkbookElement with type ${element.elementType}")
        else if (factory.get.requireIds().exists(!alreadyParsed.keySet.contains(_))) None
        else Some(factory.get.createFromSerialized(element, alreadyParsed))
      }

      val newlyFinished: mutable.HashMap[String, WorkbookElement] = mutable.HashMap[String, WorkbookElement]()
      val stillOpen: mutable.ListBuffer[WorkbookElementSerializable] = mutable.ListBuffer()
      open.foreach(curOpenElement => tryParse(curOpenElement) match {
        case Some(workbookElement) => newlyFinished += curOpenElement.elementId -> workbookElement
        case None => stillOpen += curOpenElement
      })

      if (stillOpen.nonEmpty && newlyFinished.isEmpty) throw SerializedException(s"Iteration with no progress, likely because of a cyclic dependency, stop parsing! (still open: ${open.map(_.elementId)})")
      else parseAll(elementsInOrder, stillOpen.toList, alreadyParsed, knownFactories)
    }
  }

  val allKnownFactories: Map[String, WorkbookElementFactory[WorkbookElement]] = Map(
    /* classOf[TurtleStitchRecreateShapeInteraction].getSimpleName -> TurtleStitchRecreateShapeInteraction.fromFactory,
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
     */
  )

  val prefix = "WorkbookElementFactory"


  private given refRW: default.ReadWriter[WorkbookElementReference] = macroRW
  //private given refRWL: default.ReadWriter[List[WorkbookElementReference]] = macroRW

  private given refRWL: ReadWriter[List[WorkbookElementReference]] =
    readwriter[List[WorkbookElementReference]].bimap[List[WorkbookElementReference]](_.toSeq, _.toList)

  given facRW: default.ReadWriter[WorkbookElementSerializable] = macroRW

  val serializer: Serializer[WorkbookElementSerializable] = Serializer.fromUpickleJson(facRW)
  val serializerR: Serializer[WorkbookElementReference] = Serializer.fromUpickleJson(refRW)
  val serializerRL: Serializer[List[WorkbookElementReference]] = Serializer.fromUpickleJson(refRWL)


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
}


case class WorkbookElementSerializable(
                                        elementId: String,
                                        elementType: String,
                                        additionalElements: Map[String, String]
                                      ) {

  def withElementAdded(key: String, value: String): WorkbookElementSerializable = {
    withMapAdded(Map(key -> value))
  }

  def withMapAdded(map: Map[String, String]): WorkbookElementSerializable = {
    WorkbookElementSerializable(elementId, elementType, additionalElements ++ map)
  }

  def withElementAdded[T](key: String, element: T)(implicit rw: ReadWriter[T]): WorkbookElementSerializable = {
    withElementAdded(key, element)(Serializer.fromUpickleJson(rw))
  }

  def withElementAdded[T](key: String, element: T)(serializer: Serializer[T]): WorkbookElementSerializable = {
    WorkbookElementSerializable(elementId, elementType, additionalElements ++ Map(key -> serializer.serialize(element)))
  }

  def withContentIdAdded(key: String, element: LanguageMapContentId): WorkbookElementSerializable = {
    withElementAdded(key, element)(LanguageMapContentId.serializer)
  }

  def withElementsAdded(key: String, workbookElements: Seq[WorkbookElement]): WorkbookElementSerializable = {
    withReferencesAdded(key, workbookElements.map(_.asRef))
  }

  def withElementAdded(key: String, workbookElement: WorkbookElement): WorkbookElementSerializable = {
    withReferenceAdded(key, workbookElement.asRef)
  }

  def withReferenceAdded(key: String, workbookElement: WorkbookElementReference): WorkbookElementSerializable = {
    withElementAdded(key, workbookElement)(using refRW)
  }

  def withReferencesAdded(key: String, workbookElement: Seq[WorkbookElementReference]): WorkbookElementSerializable = {
    withElementAdded(key, workbookElement.toList)(using refRWL)
  }

  def getElementAsString(elementKey: String): String = additionalElements(elementKey)

  def getOptionalElementAsString(elementKey: String, defaultValue: String): String = {
    additionalElements.getOrElse(elementKey, defaultValue)
  }

  def getOptionalElementAs[T](elementKey: String, defaultValue: T)(serializer: Serializer[T]): T = {
    val elementValue = additionalElements.get(elementKey)
    if (elementValue.isEmpty) {
      defaultValue
    } else try {
      serializer.deserialize(elementValue.get)
    } catch case (err: Throwable) => {
      throw SerializedException(s"Error at deserializing key ${elementKey} from Element ${elementId} (value ${elementValue.get})", Some(err))
    }
  }

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

  def getElementAsWorkbookReference(elementKey: String): WorkbookElementReference = {
    getElementAs[WorkbookElementReference](elementKey)(serializerR)
  }

  def getElementsAsWorkbookReferences(elementKey: String): List[WorkbookElementReference] = {
    getOptionalElementAs[List[WorkbookElementReference]](elementKey, List())(serializerRL)
  }


}
