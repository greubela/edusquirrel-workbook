package it.evadid.workbook.elements.interactionElements.programming.state.snap


object SnapGeneratedImages {

  def remove(xml: String): String = {
    val removableTags = Set("pentrails", "pentrail", "thumbnail")
    val result = new StringBuilder
    var keptFrom = 0
    var cursor = 0
    while cursor < xml.length do
      val opening = xml.indexOf('<', cursor)
      if opening < 0 then cursor = xml.length
      else if xml.startsWith("<!--", opening) || xml.startsWith("<![CDATA[", opening) || xml.startsWith("<?", opening) then
        val terminator = if xml.startsWith("<!--", opening) then "-->"
        else if xml.startsWith("<![CDATA[", opening) then "]]>" else "?>"
        val end = xml.indexOf(terminator, opening + 2)
        cursor = if end < 0 then xml.length else end + terminator.length
      else {
        var nameEnd = opening + 1
        while nameEnd < xml.length && (xml.charAt(nameEnd).isLetterOrDigit || "_-:.".contains(xml.charAt(nameEnd))) do
          nameEnd += 1
        val tag = xml.substring(opening + 1, nameEnd)
        if removableTags.contains(tag) then
          // Reuse Snap's matching-close scanner, including nested and self-closing nodes.
          SnapXmlParser.parseElementAt(xml, opening) match
            case Some(element) =>
              result.append(xml.substring(keptFrom, opening))
              cursor = element.end
              keptFrom = cursor
            case _ => cursor = opening + 1
        else cursor = opening + 1
      }
    result.append(xml.substring(keptFrom)).toString
  }

}
