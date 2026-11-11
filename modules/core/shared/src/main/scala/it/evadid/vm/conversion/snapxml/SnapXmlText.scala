package it.evadid.vm.conversion.snapxml

object SnapXmlText {
  def escape(value: String): String = value.replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

  /** Unknown DTD entities stay opaque. No network access or entity expansion is performed. */
  def decode(raw: String): String = {
    val entity = "&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);".r
    val out = new StringBuilder
    var previous = 0
    entity.findAllMatchIn(raw).foreach { m =>
      out.append(raw.substring(previous, m.start))
      val decoded = m.group(1) match {
        case "amp" => "&"
        case "lt" => "<"
        case "gt" => ">"
        case "quot" => "\""
        case "apos" => "'"
        case number =>
          val code = if number.startsWith("#x") then Integer.parseInt(number.drop(2), 16) else Integer.parseInt(number.drop(1))
          require(code > 0 && code <= 0x10ffff && !(code >= 0xd800 && code <= 0xdfff), "Invalid XML character reference.")
          if code <= 0xffff then code.toChar.toString
          else {
            val n = code - 0x10000
            (0xd800 + (n >> 10)).toChar.toString + (0xdc00 + (n & 0x3ff)).toChar.toString
          }
      }
      out.append(decoded)
      previous = m.end
    }
    out.append(raw.substring(previous)).toString
  }}
