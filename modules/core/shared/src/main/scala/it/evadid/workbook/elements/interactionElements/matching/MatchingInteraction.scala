package it.evadid.workbook.elements.interactionElements.matching

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.InteractionFormat.MatchingInteractionFormat
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

/** Connects every statement on the left with one category on the right. A category may be used by
 * several statements or by none, so a category list can contain distractors.
 *
 * @param expected the index of the correct category for every left item; `None` leaves the task ungraded
 */
case class MatchingInteraction(override val elementId: String, instruction: Option[LanguageMapContentId],
                               left: Seq[LanguageMapContentId], right: Seq[LanguageMapContentId],
                               expected: Option[Seq[Int]])
  extends WorkbookInteractionElement[MatchingInteractionFormat.type] {

  require(left.nonEmpty && right.nonEmpty, "A matching task needs items on both sides")
  require(expected.forall(answers => answers.size == left.size && answers.forall(right.indices.contains)),
    "Every left item needs exactly one existing expected category")

  override val associatedFactory = MatchingInteraction.factory
  override lazy val childrenOfThisElement: Seq[WorkbookElement] = Nil
  override val defaultValue: MatchingInteractionState = MatchingInteractionState(Seq.fill(left.size)(None))
  override val gradingElements = expected.map(answers => MatchingGrader(answers, right.size)).toSeq

  override def validateState(state: MatchingInteractionState): Unit =
    require(state == state.sanitized(left.size, right.size), "Stored matching does not belong to this exercise")

  def connect(state: MatchingInteractionState, leftIndex: Int, rightIndex: Option[Int]): MatchingInteractionState =
    state.withMatch(leftIndex, rightIndex, left.size, right.size)
}

object MatchingInteraction {
  val factory: SimpleWorkbookElementFactory[MatchingInteraction] = new SimpleWorkbookElementFactory[MatchingInteraction] {
    override protected val constructorFieldOrder = List("elementId", "instruction", "left", "right", "expected")

    override def finishSerialization(base: WorkbookElementSerializable, e: MatchingInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("instruction", e.instruction)
        .withElementAddedAs("left", e.left)
        .withElementAddedAs("right", e.right)
        .withElementAddedAs("expected", e.expected)

    override def finishDeserialization(e: WorkbookElementSerializable): MatchingInteraction =
      MatchingInteraction(e.elementId,
        e.getElementAs[Option[LanguageMapContentId]]("instruction"),
        e.getElementAs[Seq[LanguageMapContentId]]("left"),
        e.getElementAs[Seq[LanguageMapContentId]]("right"),
        e.getElementAs[Option[Seq[Int]]]("expected"))
  }
}
