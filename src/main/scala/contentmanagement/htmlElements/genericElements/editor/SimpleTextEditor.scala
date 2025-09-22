package contentmanagement.htmlElements.genericElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.interaction.Editor
import workbook.model.states.BasicVariableBasedState
import workbook.model.states.BasicVariableBasedState.*

case class SimpleTextEditor(initState: BasicStringState) extends Editor[BasicStringState] {

  val textboxValueVar: Var[String] = Var("")

  private val domElement = {
    div(
      cls := "simple-text-editor",
      textArea(
        rows := 8,
        cols := 80,
        controlled(
          value <-- textboxValueVar.signal,
          onInput.mapToValue --> textboxValueVar.writer
        )
      )
    )
  }


  override def getDomElement(): L.Element = domElement

  def getCurrentState(): BasicStringState = BasicVariableBasedState[String](textboxValueVar.now(), str => str)

  def loadState(stateToLoad: BasicStringState): Unit = {
    textboxValueVar.set(stateToLoad.getStateAsString())
  }

  def addObserver(observer: String => Any): Unit = {
    val obs = Observer[String](newTextareaText => observer.apply(newTextareaText))
    textboxValueVar.signal.addObserver(obs)(unsafeWindowOwner)

  }


}
