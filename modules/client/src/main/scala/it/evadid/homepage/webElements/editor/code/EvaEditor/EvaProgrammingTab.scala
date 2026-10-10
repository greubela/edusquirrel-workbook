package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState}

object EvaProgrammingTab {
  def tabFor(evaConfig: EvaEditorConfig, programmingLanguage: ProgrammingLanguage, centralState: Var[ProgrammingState], handleOnStateChanged: ProgrammingState => Unit): EvaEditorProgrammingTab[? <: ProgrammingState] =
    programmingLanguage.match {
      case Java => {
        EvaEditorProgrammingTab[ProgrammingStateJavaString](
          centralState.now(),
          programmingLanguage,
          curVar => {
            val bound: Var[String] = curVar.bimap[String](_.code)(ProgrammingStateJavaString(_))
            CodeMirrorEditor(bound, code => handleOnStateChanged(ProgrammingStateJavaString(code)), language = AppLanguage.Java)
          },
          _.toJava
        )
      }
      case Python => {
        EvaEditorProgrammingTab[ProgrammingStatePythonString](
          centralState.now(),
          programmingLanguage,
          curVar => {
            val bound: Var[String] = curVar.bimap[String](_.code)(ProgrammingStatePythonString(_))
            CodeMirrorEditor(bound, code => handleOnStateChanged(ProgrammingStatePythonString(code)), language = AppLanguage.Python)
          },
          _.toPython
        )
      }
      case SnapLanguage => {
        EvaEditorProgrammingTab[ProgrammingState](
          centralState.now(),
          programmingLanguage,
          curVar => SnapCodeEditor(curVar, evaConfig.snapConfig, handleOnStateChanged),
          _.toSnapXml
        )
      }

      case _ => throw UnsupportedOperationException(s"ProgrammingLanguage '${programmingLanguage}' not supported yet in EvaEditor!'")

    }

}

case class EvaEditorProgrammingTab[T <: ProgrammingState](
                                                           private val initState: ProgrammingState,
                                                           associatedLanguage: ProgrammingLanguage,
                                                           createEditor: Var[T] => HtmlAppElement,
                                                           convertFrom: ProgrammingState => T,
                                                         ) {

  val associatedVar: Var[T] = Var(convertFrom(initState))

  val editorElement: HtmlAppElement = createEditor(associatedVar)

  def canSetStateTo(state: ProgrammingState): Option[T] = try {
    Some(convertFrom(state))
  } catch case (err: Throwable) => {
    None
  }

  def setStateTo(state: ProgrammingState): Unit = canSetStateTo(state).foreach { next =>
    if associatedVar.now() != next then associatedVar.set(next)
  }

}
