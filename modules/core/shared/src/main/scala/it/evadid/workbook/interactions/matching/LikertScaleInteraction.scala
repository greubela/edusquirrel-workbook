package it.evadid.workbook.interactions.matching

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.InteractionFormat.LikertScaleInteractionFormat
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.serialization.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.serialization.WorkbookElementSerializable

/** Ungraded survey matrix: every statement is rated on the same scale (for example a five point agreement scale).
 * The state stores the chosen scale index per statement; `None` means no answer.
 */
case class LikertScaleInteraction(override val elementId: String, instruction: Option[LanguageMapContentId],
                                  statements: Seq[LanguageMapContentId], scale: Seq[LanguageMapContentId])
  extends WorkbookInteractionElement[LikertScaleInteractionFormat.type] {

  require(statements.nonEmpty && scale.size >= 2, "A scale needs statements and at least two scale points")

  override val associatedFactory = LikertScaleInteraction.factory
  override lazy val childrenOfThisElement: Seq[WorkbookElement] = Nil
  override val defaultValue: MatchingInteractionState = MatchingInteractionState(Seq.fill(statements.size)(None))

  override def validateState(state: MatchingInteractionState): Unit =
    require(state == state.sanitized(statements.size, scale.size), "Stored ratings do not belong to this survey")

  def rate(state: MatchingInteractionState, statementIndex: Int, scaleIndex: Option[Int]): MatchingInteractionState =
    state.withMatch(statementIndex, scaleIndex, statements.size, scale.size)
}

object LikertScaleInteraction {
  val factory: SimpleWorkbookElementFactory[LikertScaleInteraction] = new SimpleWorkbookElementFactory[LikertScaleInteraction] {
    override protected val constructorFieldOrder = List("elementId", "instruction", "statements", "scale")

    override def finishSerialization(base: WorkbookElementSerializable, e: LikertScaleInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("instruction", e.instruction)
        .withElementAddedAs("statements", e.statements)
        .withElementAddedAs("scale", e.scale)

    override def finishDeserialization(e: WorkbookElementSerializable): LikertScaleInteraction =
      LikertScaleInteraction(e.elementId,
        e.getElementAs[Option[LanguageMapContentId]]("instruction"),
        e.getElementAs[Seq[LanguageMapContentId]]("statements"),
        e.getElementAs[Seq[LanguageMapContentId]]("scale"))
  }
}
