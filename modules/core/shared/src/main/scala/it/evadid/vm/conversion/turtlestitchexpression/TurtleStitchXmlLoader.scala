package it.evadid.vm.conversion.turtlestitchexpression

import TurtleStitchProgramModel.Project
import it.evadid.vm.code.errors.ProgrammingConversionException
import it.evadid.vm.conversion.snapxmlmodel.SnapScalaXmlCodec

/** Snap XML → program model, through the single lexical Snap reader. Blank text is an empty project,
  * a bare sequence of blocks is one script, and malformed XML is unparsable.
  */
object TurtleStitchXmlLoader {
  def load(xml: String): Project =
    if xml.trim.isEmpty then TurtleStitchModelMapper.EmptyProject
    else SnapScalaXmlCodec.parse(asDocument(xml.trim)).fold(
      message => throw ProgrammingConversionException.unparsable(message), TurtleStitchModelMapper.toProject)

  /** Several top-level blocks, as Snap copies them, form one script. */
  private def asDocument(xml: String): String =
    if (xml.startsWith("<block") && !xml.startsWith("<blocks")) || xml.startsWith("<custom-block") then s"<script>$xml</script>" else xml
}
