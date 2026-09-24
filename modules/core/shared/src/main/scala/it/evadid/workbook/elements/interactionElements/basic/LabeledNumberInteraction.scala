package it.evadid.workbook.elements.interactionElements.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.basic.LabeledNumberInteraction.NumberType
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable


object LabeledNumberInteraction {

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

  val factory: SimpleWorkbookElementFactory[LabeledNumberInteraction] = new SimpleWorkbookElementFactory[LabeledNumberInteraction]() {
    override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: LabeledNumberInteraction): WorkbookElementSerializable = {
      baseElement
        .withContentIdAdded("numberLabel", infoElement.numberLabel)
        .withElementAdded("numberType", infoElement.numberType.toString)
        .withElementAdded("defaultNumber", infoElement.defaultValue)
    }

    override def finishDeserialization(element: WorkbookElementSerializable): LabeledNumberInteraction = {
      LabeledNumberInteraction(element.elementId,
        element.getElementAsContentId("numberLabel"),
        NumberType.valueOf(element.getOptionalElementAsString("numberType", "IntegerLike")),
        element.getOptionalElementAsString("defaultValue", "0")
      )
    }
  }

}

case class LabeledNumberInteraction(
                                     override val elementId: String,
                                     numberLabel: LanguageMapContentId,
                                     numberType: NumberType,
                                     override val defaultValue: String = "0",
                                   ) extends WorkbookInteractionElement[String] {
  override val associatedFactory = LabeledNumberInteraction.factory

  lazy val childrenOfThisElement: List[WorkbookElement] = List()
  override val serializerInteractionContent: Serializer[String] = Serializer.stringIO

}



