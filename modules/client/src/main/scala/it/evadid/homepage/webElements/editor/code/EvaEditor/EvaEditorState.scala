package it.evadid.homepage.webElements.editor.code.EvaEditor

import it.evadid.core.datastructures.language.AppLanguage.{ProgrammingLanguage, SnapLanguage}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateSnapXml
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState}

case class EvaEditorState(currentLanguageSelected: ProgrammingLanguage, currentProgram: ProgrammingState) {


  def updateWith(newState: ProgrammingState): EvaEditorState = {
    EvaEditorState(newState.associatedLanguage, newState)
  }

  def updateWith(newLang: ProgrammingLanguage): EvaEditorState = {
    EvaEditorState(newLang, currentProgram.toLanguage(newLang))
  }

}



