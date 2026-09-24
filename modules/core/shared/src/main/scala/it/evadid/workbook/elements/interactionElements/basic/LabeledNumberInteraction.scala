package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.basic.LabeledNumberInteraction.NumberType
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable


object LabeledNumberInteraction {
  def fromFactory(factory: WorkbookElementSerializable): LabeledNumberInteraction = {
    LabeledNumberInteraction(
      factory.elementId,
      factory.getElementAsContentId("numberLabel"),
      NumberType.valueOf(factory.getOptionalElementAsString("numberType", "IntegerLike")),
      factory.getOptionalElementAsString("defaultValue", "0"))
  }

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

  case class NumberInteractionConfig(numberType: NumberType, defaultDiff: BigDecimal)

}

case class LabeledNumberInteraction(
                                     override val elementId: String,
                                     numberLabel: LanguageMapContentId,
                                     numberType: NumberType,
                                     override val defaultValue: String = "0",
                                   ) extends WorkbookInteractionElement[String] {

  lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val serializerInteractionContent: Serializer[String] = Serializer.stringIO

}



