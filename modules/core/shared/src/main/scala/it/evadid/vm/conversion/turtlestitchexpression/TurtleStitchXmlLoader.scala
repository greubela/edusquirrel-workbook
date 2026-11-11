package it.evadid.vm.conversion.turtlestitchexpression

import TurtleStitchProgramModel.Project

object TurtleStitchXmlLoader {
  def load(xml: String): Project =
    TurtleStitchXmlParser.parse(xml).document.map(TurtleStitchModelMapper.toProject)
      .getOrElse(TurtleStitchModelMapper.EmptyProject)
}
