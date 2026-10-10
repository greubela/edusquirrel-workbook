package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Element
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStatePythonString}

object EvaProgrammingTab {
  private def convert(language: ProgrammingLanguage, source: ProgrammingState): ProgrammingState = language match {
    case Java => source.toJava
    case Python => source.toPython
    case SnapLanguage => source.toSnapXml
    case _ => throw UnsupportedOperationException(s"ProgrammingLanguage '$language' not supported yet in EvaEditor!")
  }

  def tabFor(evaConfig: EvaEditorConfig, programmingLanguage: ProgrammingLanguage, centralState: Var[ProgrammingState],
      handleOnStateChanged: ProgrammingState => Unit): EvaEditorProgrammingTab[? <: ProgrammingState] =
    prepared(evaConfig, programmingLanguage, Var(convert(programmingLanguage, centralState.now())), handleOnStateChanged)

  private[code] def prepared(config: EvaEditorConfig, language: ProgrammingLanguage, localState: Var[ProgrammingState],
      onStateChanged: ProgrammingState => Unit): EvaEditorProgrammingTab[ProgrammingState] = {
    val create: Var[ProgrammingState] => HtmlAppElement = language match {
      case Java => current => new JavaFunctionBasedEditor(current, onStateEdited = onStateChanged)
      case Python => current => CodeMirrorEditor(
        current.bimap[String](_.toPython.code)(ProgrammingStatePythonString(_)),
        code => onStateChanged(ProgrammingStatePythonString(code)), language = AppLanguage.Python)
      case SnapLanguage => current => SnapCodeEditor(current, config.snapConfig, onStateChanged)
      case _ => throw UnsupportedOperationException(s"ProgrammingLanguage '$language' not supported yet in EvaEditor!")
    }
    EvaEditorProgrammingTab(localState, language, create, source => convert(language, source))
  }
}

case class EvaEditorProgrammingTab[T <: ProgrammingState](
    associatedVar: Var[T],
    associatedLanguage: ProgrammingLanguage,
    createEditor: Var[T] => HtmlAppElement,
    convertFrom: ProgrammingState => T
) extends FullscreenLifecycle {

  val editorElement: HtmlAppElement = createEditor(associatedVar)

  lazy val domElement: Element = editorElement.getDomElement()

  def captureSource(): ProgrammingState = editorElement match {
    case snap: SnapCodeEditor => snap.captureCurrentProject()
    case _ => associatedVar.now()
  }

  override def onFullscreenOpen(): Unit = editorElement match {
    case lifecycle: FullscreenLifecycle => lifecycle.onFullscreenOpen()
    case _ => ()
  }

  override def onFullscreenClose(): Unit = editorElement match {
    case lifecycle: FullscreenLifecycle => lifecycle.onFullscreenClose()
    case _ => ()
  }

  def canSetStateTo(state: ProgrammingState): Option[T] = try {
    Some(convertFrom(state))
  } catch case (err: Throwable) => {
    None
  }

  def setStateTo(state: ProgrammingState): Unit = canSetStateTo(state).foreach { next =>
    if associatedVar.now() != next then associatedVar.set(next)
  }

}
