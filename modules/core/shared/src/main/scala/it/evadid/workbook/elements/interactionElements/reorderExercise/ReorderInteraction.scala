package it.evadid.workbook.elements.interactionElements.reorderExercise

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{InteractionFormat, DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import upickle.default.ReadWriter

sealed trait ReorderInteraction[T] extends WorkbookInteractionElement[ReorderInteractionState[T]] {
  val elements: List[T]
}

object ReorderInteraction {

  case class ReorderCodeInteraction(override val elementId: String, lines: List[String], programmingLanguage: ProgrammingLanguage, seed: Long = 0, hints: List[LanguageMapContentId] = List.empty, orderConstraints: List[(Int, Int)] = Nil) extends ReorderInteraction[String] {

    override val associatedFactory = ReorderCodeInteraction.factory
    override val elements = lines
    override val defaultValue = ReorderInteractionState.initStateFromElementsAndSeed(lines, seed, ReorderType.CODELINES(programmingLanguage))

    override lazy val childrenOfThisElement: List[WorkbookElement] = List()
    override def format: InteractionFormat[ReorderInteractionState[String], Unit, Unit] = ReorderCodeInteraction
  }

  object ReorderCodeInteraction extends InteractionFormat[ReorderInteractionState[String], Unit, Unit] {
    override def stateSerializer: Serializer[ReorderInteractionState[String]] = Serializer.fromImplicitRW
    override def historySerializer: Serializer[InteractionVariableHistory[ReorderInteractionState[String], Unit, Unit]] = Serializer.fromImplicitRW
    override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[ReorderInteractionState[String], Unit, Unit]] = Serializer.fromImplicitRW
    val factory: SimpleWorkbookElementFactory[ReorderCodeInteraction] = new SimpleWorkbookElementFactory[ReorderCodeInteraction]() {
      override protected val constructorFieldOrder = List("elementId", "stringLines", "programmingLanguage", "seed", "hints", "orderConstraints")

      override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: ReorderCodeInteraction): WorkbookElementSerializable = {
        baseElement
          .withElementAddedAs("hints", infoElement.hints)
          .withElementAddedAs("orderConstraints", infoElement.orderConstraints)
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
          element.getElementsAs[LanguageMapContentId]("hints"),
          element.getOptionalElementAs[List[(Int, Int)]]("orderConstraints", Nil)
        )
      }
    }
  }

  case class ReorderMapIdInteraction(override val elementId: String, ids: List[LanguageMapContentId], seed: Long = 0) extends ReorderInteraction[LanguageMapContentId] {

    override val associatedFactory = ReorderMapIdInteraction.factory
    override val elements = ids
    override val defaultValue = ReorderInteractionState.initStateFromElementsAndSeed(ids, seed,ReorderType.LANGUAGE_MAP_IDS)

    override lazy val childrenOfThisElement: List[WorkbookElement] = List()
    override def format: InteractionFormat[ReorderInteractionState[LanguageMapContentId], Unit, Unit] = ReorderMapIdInteraction
  }

  object ReorderMapIdInteraction extends InteractionFormat[ReorderInteractionState[LanguageMapContentId], Unit, Unit] {
    override def stateSerializer: Serializer[ReorderInteractionState[LanguageMapContentId]] = Serializer.fromImplicitRW
    override def historySerializer: Serializer[InteractionVariableHistory[ReorderInteractionState[LanguageMapContentId], Unit, Unit]] = Serializer.fromImplicitRW
    override def exerciseSerializer: Serializer[WorkbookInteractionElementFull[ReorderInteractionState[LanguageMapContentId], Unit, Unit]] = Serializer.fromImplicitRW
    val factory: SimpleWorkbookElementFactory[ReorderMapIdInteraction] = new SimpleWorkbookElementFactory[ReorderMapIdInteraction]() {
      override protected val constructorFieldOrder = List("elementId", "contentToReorder", "seed")

      override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: ReorderMapIdInteraction): WorkbookElementSerializable = {
        baseElement
          .withElementsAddedAs("contentToReorder", infoElement.ids)
          .withElementAdded("seed", infoElement.seed.toString)
      }

      override def finishDeserialization(element: WorkbookElementSerializable): ReorderMapIdInteraction = {
        ReorderMapIdInteraction(
          element.elementId,
          element.getElementsAs[LanguageMapContentId]("contentToReorder"),
          element.getOptionalElementAs("seed", "0").toLongOption.getOrElse(0))
      }
    }
  }
}
