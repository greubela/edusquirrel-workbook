package it.evadid.workbook.interactions.programming.programmingExerciseTurtle

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.workbook.interactions.programming.state.snap.ProgrammingEditorPalette

import upickle.default.*

object RecreateTurtleDidacticConfig {
  lazy val defaultDidacticConfig = RecreateTurtleDidacticConfig(
    List(), ProgrammingEditorPalette.BeginnerTurtle, MaxCommandUsage.noBounds
  )

  def defaultForPalette(palette: ProgrammingEditorPalette): RecreateTurtleDidacticConfig = {
    defaultDidacticConfig.copy(availablePalette = palette)
  }

}

case class RecreateTurtleDidacticConfig(
                                         private val restrictEditorToLanguages: Seq[ProgrammingLanguage] = List(),
                                         availablePalette: ProgrammingEditorPalette,
                                         maxCommandUsage: MaxCommandUsage
                                       ) derives ReadWriter {

  lazy val allowedEditorLanguages: Seq[ProgrammingLanguage] = if(restrictEditorToLanguages.nonEmpty) restrictEditorToLanguages else List(SnapLanguage, Python, Java)

}
