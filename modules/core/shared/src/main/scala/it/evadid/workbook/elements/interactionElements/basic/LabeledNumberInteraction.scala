package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}

/**
 * Describes the kind of numeric value a [[LabeledNumberInteraction]] edits.
 *
 * The value is stored as text so renderers can preserve exact algebraic input
 * such as "sin(3) + 4" while still offering numeric spinner controls.
 */
enum NumberType {
  case IntegerLike
  case FractionLike
  case AlgebraicLike
}

case class LabeledNumberInteraction(
                                     override val id: String,
                                     numberLabel: LanguageMapContentId,
                                     numberType: NumberType,
                                     override val defaultValue: String = "0",
                                     diff: BigDecimal = BigDecimal(1)
                                   ) extends WorkbookInteractionElement[String] {

  lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val serializer: Serializer[String] = Serializer.stringIO

}
