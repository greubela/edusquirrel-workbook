package it.evadid.workbook.model.text

/** A scalar value or an explicitly identified unpaired UTF-16 surrogate. */
case class UnicodeCharacter(position: Int, glyph: String, codePoint: Int, wellFormed: Boolean) {
  def hexadecimal: String = "U+" + f"$codePoint%04X"
}
object UnicodeText {
  /** No normalization: visually similar and canonically equivalent strings remain distinguishable. */
  def characters(text: String): List[UnicodeCharacter] = {
    val result = List.newBuilder[UnicodeCharacter]
    var offset = 0
    var position = 1
    while offset < text.length do {
      val first = text.charAt(offset).toInt
      val high = first >= 0xD800 && first <= 0xDBFF
      val paired = high && offset + 1 < text.length &&
        text.charAt(offset + 1) >= 0xDC00 && text.charAt(offset + 1) <= 0xDFFF
      val width = if paired then 2 else 1
      val point = if paired then 0x10000 + ((first - 0xD800) << 10) + text.charAt(offset + 1).toInt - 0xDC00 else first
      result += UnicodeCharacter(position, text.substring(offset, offset + width), point,
        paired || first < 0xD800 || first > 0xDFFF)
      offset += width
      position += 1
    }
    result.result()
  }
}
