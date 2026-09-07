package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.color.{RGBColor, WebColor}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.types.{BeDataValueLiteral, BeUseValueReference}
import it.evadid.workbook.elements.interactionElements.programming.SnapTurtleCatalog.SnapInputKind

/**
 * Snap-specific literal codec. Color slots stay here rather than becoming a VM value type:
 * Python stores quoted web-color strings, and Snap XML renders `<color>` only in color slots.
 */
object SnapInputCodec {

  def stripQuotes(value: String): String = {
    val trimmed = value.trim
    if trimmed.length >= 2 && (
      (trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
        (trimmed.startsWith("'") && trimmed.endsWith("'"))
    ) then trimmed.substring(1, trimmed.length - 1)
    else trimmed
  }

  def quotePython(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  def parseColor(raw: String): Option[RGBColor] = {
    val value = stripQuotes(raw).trim
    if value.isEmpty then None
    else parseSnapRgba(value).orElse(parseWebColor(value))
  }

  def parseSnapRgba(value: String): Option[RGBColor] = {
    val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
    if parts.length < 3 then None
    else
      val nums = parts.take(4).flatMap(part => scala.util.Try(part.toDouble).toOption)
      if nums.length < 3 then None
      else
        val red = nums(0).round.toInt
        val green = nums(1).round.toInt
        val blue = nums(2).round.toInt
        val alpha =
          if nums.length > 3 then
            val rawAlpha = nums(3)
            if rawAlpha <= 1.0 then math.round(rawAlpha * 255.0).toInt else rawAlpha.round.toInt
          else 255
        if inByte(red) && inByte(green) && inByte(blue) && inByte(alpha) then Some(RGBColor(red, green, blue, alpha))
        else None
  }

  def parseWebColor(value: String): Option[RGBColor] =
    scala.util.Try(WebColor(value).toRGB).toOption

  def pythonQuotedColor(color: RGBColor): String = {
    val hex = color.toHex("#").toLowerCase
    val named = WebColor.reverseNameMap.get(hex)
    quotePython(named.getOrElse(s"rgb(${color.red}, ${color.green}, ${color.blue})"))
  }

  def pythonQuotedColorFromRaw(raw: String): Option[String] =
    parseColor(raw).map(pythonQuotedColor)

  def snapRgbaString(color: RGBColor): String = {
    val alpha =
      if color.alpha >= 255 then "1"
      else if color.alpha <= 0 then "0"
      else BigDecimal(color.alpha / 255.0).bigDecimal.stripTrailingZeros.toPlainString
    s"${color.red},${color.green},${color.blue},$alpha"
  }

  def snapColorXml(color: RGBColor): String =
    s"<color>${snapRgbaString(color)}</color>"

  def escapeXml(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  def renderLiteral(kind: SnapInputKind, value: String): String =
    kind match
      case SnapInputKind.Color =>
        parseColor(value).map(snapColorXml).getOrElse(s"<l>${escapeXml(stripQuotes(value))}</l>")
      case SnapInputKind.Bool =>
        val normalized =
          if value.equalsIgnoreCase("true") || value == "True" then "true"
          else if value.equalsIgnoreCase("false") || value == "False" then "false"
          else value
        s"<l>${escapeXml(normalized)}</l>"
      case _ =>
        s"<l>${escapeXml(value)}</l>"

  def isValidForKind(kind: SnapInputKind, expression: BeExpression): Boolean =
    kind match
      case SnapInputKind.Color =>
        expression match
          case BeUseValue(BeDataValueLiteral(value), _) => parseColor(value).isDefined
          case BeUseValue(BeUseValueReference(_), _) => true
          case _: BeFunctionCall => false
          case _ => false
      case SnapInputKind.Bool =>
        SnapControlFlow.isSupportedCondition(expression) || SnapControlFlow.isSupportedValue(expression)
      case SnapInputKind.Numeric | SnapInputKind.String =>
        SnapControlFlow.isSupportedValue(expression)

  private def inByte(value: Int): Boolean =
    value >= 0 && value <= 255
}
