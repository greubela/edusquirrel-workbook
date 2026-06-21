package it.evadid.evacuation.core.graphic.model

case class EvaFont(sizeInPx: Double, name: String, bold: Boolean = false, italic: Boolean = false) {


  def toCSSString: String = {
    var res = s"${sizeInPx}px \"$name\""
    if (bold && italic) "italic bold " + res
    else if (bold) "bold " + res
    else if (italic) "italic " + res
    else res
  }

}
