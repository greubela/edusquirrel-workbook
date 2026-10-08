package it.evadid.workbook.elements.interactionElements.qr

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.model.qr.{QrCode, QrErrorCorrection}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

/** Requirements apply to payload bytes, not character count or padded codewords. */
case class QrCodeRequirements(minBytes: Int = 1, maxBytes: Option[Int] = None,
                              requiredVersion: Option[Int] = None,
                              minimumErrorCorrection: QrErrorCorrection = QrErrorCorrection.L,
                              requiredMask: Option[Int] = None) derives ReadWriter {
  require(minBytes >= 0, "Minimum byte count must be non-negative")
  require(maxBytes.forall(_ >= minBytes), "Maximum byte count must be at least the minimum")
  require(requiredVersion.forall(v => v >= 1 && v <= 40), "Required version must be between 1 and 40")
  require(requiredMask.forall(m => m >= 0 && m <= 7), "Required mask must be between 0 and 7")

  def evaluate(code: QrCode): Vector[QrCodeRequirementResult] = Vector(
    QrCodeRequirementResult("basic/qrMinBytes", code.byteCount >= minBytes, minBytes.toString),
    QrCodeRequirementResult("basic/qrMinimumErrorCorrection", code.config.errorCorrection.ordinal >= minimumErrorCorrection.ordinal, minimumErrorCorrection.toString)
  ) ++ maxBytes.map(n => QrCodeRequirementResult("basic/qrMaxBytes", code.byteCount <= n, n.toString)) ++
    requiredVersion.map(v => QrCodeRequirementResult("basic/qrRequiredVersion", code.config.version == v, v.toString)) ++
    requiredMask.map(m => QrCodeRequirementResult("basic/qrRequiredMask", code.config.mask == m, m.toString))

  def isSatisfiedBy(code: QrCode): Boolean = evaluate(code).forall(_.passed)
}
case class QrCodeRequirementResult(labelId: String, passed: Boolean, expected: String)

case class CreateQrCodeInteraction(elementId: String, requirements: QrCodeRequirements = QrCodeRequirements(),
                                   initialCode: QrCode = QrCode.fromText("")) extends WorkbookInteractionElement[QrCode] {
  override val defaultValue: QrCode = initialCode
  override val serializerInteractionContent: Serializer[QrCode] = Serializer.fromUpickleJson(summon[ReadWriter[QrCode]])
  override lazy val childrenOfThisElement: List[WorkbookElement] = Nil
  override val associatedFactory: WorkbookElementFactory[CreateQrCodeInteraction] = CreateQrCodeInteraction.factory
  def isPassed: Boolean = requirements.isSatisfiedBy(interactionVariable.currentValue)
}
object CreateQrCodeInteraction {
  val factory: WorkbookElementFactory.SimpleWorkbookElementFactory[CreateQrCodeInteraction] =
    new WorkbookElementFactory.SimpleWorkbookElementFactory[CreateQrCodeInteraction] {
      override protected val constructorFieldOrder = List("elementId", "requirements", "initialCode")
      override def finishSerialization(base: WorkbookElementSerializable, element: CreateQrCodeInteraction): WorkbookElementSerializable =
        base.withElementAddedAs("requirements", element.requirements).withElementAddedAs("initialCode", element.initialCode)
      override def finishDeserialization(element: WorkbookElementSerializable): CreateQrCodeInteraction =
        CreateQrCodeInteraction(element.elementId,
          element.getOptionalElementAs[QrCodeRequirements]("requirements", QrCodeRequirements()),
          element.getOptionalElementAs[QrCode]("initialCode", QrCode.fromText("")))
    }
}
