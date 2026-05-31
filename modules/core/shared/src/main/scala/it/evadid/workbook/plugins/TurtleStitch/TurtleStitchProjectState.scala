package it.evadid.workbook.plugins.TurtleStitch

case class TurtleStitchProjectState(programXml: Option[String] = None) {
  def asString: String = programXml.getOrElse("")
}
