package it.evadid.workbook.interactions.reorderExercise

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookElement, WorkbookInteractionElement, *}
import it.evadid.workbook.serialization.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.serialization.WorkbookElementSerializable
import it.evadid.workbook.model.history.InteractionVariableHistory
import upickle.default.ReadWriter

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*


object ReorderInteraction {

  case class ReorderCodeInteraction(override val elementId: String, lines: Seq[String], programmingLanguage: ProgrammingLanguage, seed: Long = 0, hints: Seq[LanguageMapContentId] = List.empty, orderConstraints: Seq[(Int, Int)] = Nil)
    extends WorkbookInteractionElement[ReorderCodeInteractionFormat.type] {

    override val associatedFactory = ReorderCodeInteraction.factory
    require(orderConstraints.forall((first, second) => lines.indices.contains(first) && lines.indices.contains(second) && first != second),
      "Order constraints must reference distinct existing lines")
    override val gradingElements = Seq(ReorderCodeGrader(lines, orderConstraints))
    override def validateState(state: ReorderInteractionState[String]): Unit =
      require(state.elements == lines && state.correctOrder == lines.indices && state.elementType == ReorderType.CODELINES(programmingLanguage),
        "Stored reorder content does not match the exercise")

    override val defaultValue = ReorderInteractionState.initStateFromElementsAndSeed(lines, seed, ReorderType.CODELINES(programmingLanguage))

    /** Indices of the lines to move, see [[ReorderGrading.misplaced]]. */
    override def wrongParts(state: ReorderInteractionState[String]): Set[Int] =
      ReorderGrading.misplaced(state.sanitizedCurrentOrder, orderConstraints)

    override lazy val childrenOfThisElement: Seq[WorkbookElement] = List()

   }

  object ReorderCodeInteraction  {

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
          element.getOptionalElementAs[Seq[(Int, Int)]]("orderConstraints", Nil)
        )
      }
    }
  }

  case class ReorderMapIdInteraction(override val elementId: String, ids: Seq[LanguageMapContentId], seed: Long = 0) extends WorkbookInteractionElement[ReorderMapIdInteractionFormat.type] {

    override val associatedFactory = ReorderMapIdInteraction.factory
    override val gradingElements = Seq(ReorderMapIdGrader(ids))
    override def validateState(state: ReorderInteractionState[LanguageMapContentId]): Unit =
      require(state.elements == ids && state.correctOrder == ids.indices && state.elementType == ReorderType.LANGUAGE_MAP_IDS,
        "Stored reorder content does not match the exercise")

    override val defaultValue = ReorderInteractionState.initStateFromElementsAndSeed(ids, seed,ReorderType.LANGUAGE_MAP_IDS)

    /** Indices of the items to move, see [[ReorderGrading.misplaced]]. */
    override def wrongParts(state: ReorderInteractionState[LanguageMapContentId]): Set[Int] =
      ReorderGrading.misplaced(state.sanitizedCurrentOrder, Seq.empty)

    override lazy val childrenOfThisElement: Seq[WorkbookElement] = List()

  }

  object ReorderMapIdInteraction  {

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
