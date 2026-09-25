package it.evadid.workbook.elements.interactionElements.reorderExercise

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.default.ReadWriter

sealed trait ReorderInteraction[T] extends WorkbookInteractionElement[ReorderInteractionState[T]] {
  val elements: List[T]
}

object ReorderInteraction {


  case class ReorderCodeInteraction(override val elementId: String, lines: List[String], programmingLanguage: ProgrammingLanguage, seed: Long = 0, hints: List[LanguageMapContentId] = List.empty, orderConstraints: List[(Int, Int)] = Nil) extends ReorderInteraction[String] {
    override val associatedFactory = ReorderCodeInteraction.factory
    override val elements = lines
    override val defaultValue = ReorderInteractionState.initStateFromElementsAndSeed(lines, seed, Serializer.stringIO, ReorderType.CODELINES(programmingLanguage))
    override val serializerInteractionContent = defaultValue.serializer
    override lazy val childrenOfThisElement: List[WorkbookElement] = List()
  }

  object ReorderCodeInteraction {
    val factory: SimpleWorkbookElementFactory[ReorderCodeInteraction] = new SimpleWorkbookElementFactory[ReorderCodeInteraction]() {

      override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: ReorderCodeInteraction): WorkbookElementSerializable = {
        baseElement
          .withContentIdsAdded("hints", infoElement.hints)
          .withElementAddedAs[AppLanguage]("programmingLanguage", infoElement.programmingLanguage.asInstanceOf[AppLanguage])
          .withElementAdded("seed", infoElement.seed.toString)
          .withElementsAdded("stringLines", infoElement.lines.toSeq)

      }

      override def finishDeserialization(element: WorkbookElementSerializable): ReorderCodeInteraction = {
        ReorderCodeInteraction(
          element.elementId,
          element.getElementsAs("stringLines"),
          element.getElementAs[AppLanguage]("programmingLanguage").asInstanceOf[ProgrammingLanguage],
          element.getOptionalElementAs("seed", "0").toLongOption.getOrElse(0),
          element.getElementAsContentIds("hints")
        )
      }
    }
  }

  case class ReorderMapIdInteraction(override val elementId: String, ids: List[LanguageMapContentId], seed: Long = 0) extends ReorderInteraction[LanguageMapContentId] {
    override val associatedFactory = ReorderMapIdInteraction.factory
    override val elements = ids
    override val defaultValue = ReorderInteractionState.initStateFromElementsAndSeed(ids, seed, DefaultSerializer.serializerLangMapId, ReorderType.LANGUAGE_MAP_IDS)
    override val serializerInteractionContent = defaultValue.serializer
    override lazy val childrenOfThisElement: List[WorkbookElement] = List()
  }

  object ReorderMapIdInteraction {
    val factory: SimpleWorkbookElementFactory[ReorderMapIdInteraction] = new SimpleWorkbookElementFactory[ReorderMapIdInteraction]() {

      override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: ReorderMapIdInteraction): WorkbookElementSerializable = {
        baseElement
          .withContentIdsAdded("contentToReorder", infoElement.ids)
          .withElementAdded("seed", infoElement.seed.toString)
      }

      override def finishDeserialization(element: WorkbookElementSerializable): ReorderMapIdInteraction = {
        ReorderMapIdInteraction(
          element.elementId,
          element.getElementAsContentIds("contentToReorder"),
          element.getOptionalElementAs("seed", "0").toLongOption.getOrElse(0))
      }
    }
  }
}
