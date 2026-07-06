package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.workbook.model.interaction.basic.{LabeledNumberInteraction, NumberType}
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import org.scalajs.dom.HTMLLabelElement

object HtmlBasicNumberRenderer extends LineBasedRenderingFactory[LabeledNumberInteraction] {

  private val TrailingNumberPattern = """^(.*?)([+-]?\s*)((?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)\s*$""".r

  override protected def createRendering(lni: LabeledNumberInteraction): AtomarLineRendering = {

    val numberVar: Var[String] = lni.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MINOR).toAirstreamVar

    val dom: ReactiveHtmlElement[HTMLLabelElement] =
      label(
        cls := "simple-number-editor__body",
        span(
          cls := "simple-number-editor__label-text",
          text <-- contentIdStringSignal(lni.numberLabel)
        ),
        input(
          typ := "text",
          cls := "simple-number-editor__input",
          controlled(
            value <-- numberVar.signal,
            onInput.mapToValue --> numberVar.writer
          )
        ),
        div(
          cls := "simple-number-editor__spinner",
          button(
            typ := "button",
            cls := "simple-number-editor__spinner-button",
            aria.label := "Increase number",
            "▲",
            onClick.mapTo(adjust(numberVar.now(), lni.diff, lni.numberType)) --> numberVar.writer
          ),
          button(
            typ := "button",
            cls := "simple-number-editor__spinner-button",
            aria.label := "Decrease number",
            "▼",
            onClick.mapTo(adjust(numberVar.now(), -lni.diff, lni.numberType)) --> numberVar.writer
          )
        )
      )

    RenderingLine(true, dom, "simple-number-editor")

  }

  private def adjust(currentValue: String, rawDelta: BigDecimal, numberType: NumberType): String = {
    val delta = normalizeDelta(rawDelta, numberType)
    currentValue match {
      case TrailingNumberPattern(prefix, operator, numericSuffix) =>
        incrementTrailingNumber(prefix, operator, numericSuffix, delta, numberType)
      case value if numberType == NumberType.AlgebraicLike && value.trim.nonEmpty =>
        s"(${value.trim}) ${operatorFor(delta)} ${formatNumber(delta.abs, numberType)}"
      case _ =>
        formatNumber(delta, numberType)
    }
  }

  private def incrementTrailingNumber(prefix: String, operator: String, numericSuffix: String, delta: BigDecimal, numberType: NumberType): String = {
    val magnitude = BigDecimal(numericSuffix)
    val trimmedOperator = operator.trim
    val signedMagnitude = if trimmedOperator == "-" then -magnitude else magnitude
    val next = normalizeValue(signedMagnitude + delta, numberType)

    if prefix.isEmpty && (trimmedOperator.isEmpty || trimmedOperator == "-" || trimmedOperator == "+") then
      formatNumber(next, numberType)
    else {
      val displayMagnitude = next.abs
      val displayOperator = if next < 0 then "-" else if trimmedOperator.isEmpty then "" else "+"
      val spacing = if operator.exists(_.isWhitespace) then " " else ""
      s"$prefix${displayOperator}${spacing}${formatNumber(displayMagnitude, numberType)}"
    }
  }

  private def normalizeDelta(delta: BigDecimal, numberType: NumberType): BigDecimal = numberType match {
    case NumberType.IntegerLike => normalizeValue(delta, numberType)
    case NumberType.FractionLike | NumberType.AlgebraicLike => delta
  }

  private def normalizeValue(value: BigDecimal, numberType: NumberType): BigDecimal = numberType match {
    case NumberType.IntegerLike => value.setScale(0, BigDecimal.RoundingMode.HALF_UP)
    case NumberType.FractionLike | NumberType.AlgebraicLike => value
  }

  private def operatorFor(delta: BigDecimal): String = if delta < 0 then "-" else "+"

  private def formatNumber(value: BigDecimal, numberType: NumberType): String = numberType match {
    case NumberType.IntegerLike => value.setScale(0, BigDecimal.RoundingMode.HALF_UP).toBigInt.toString
    case NumberType.FractionLike | NumberType.AlgebraicLike => value.bigDecimal.stripTrailingZeros.toPlainString
  }
}
