package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.BeProgram

case class HtmlBlockLibraryTab(
                                editorState: TreeEditorState,
                                programFactory: BeDisplayConfig => List[BeProgram],
                                mainControllerTreeListener: Var[BeTreeControllerConfig]
                              ) {

  def getDisplays(displayConfig: BeDisplayConfig): List[BeProgram] = programFactory(displayConfig)

  lazy val toDomSignal: Signal[L.Element] = {
    editorState.displayConfigVar.signal.map(curDisplayConfig => {
      val programs = programFactory(curDisplayConfig)
      val treeDisplays = programs.map(curProg => HtmlBeTreeDisplay(Var(curProg).signal, editorState, mainControllerTreeListener ))

      val domSignals = treeDisplays.map(_.toDomSignal)

      div(
        cls := "block-library-tab",
        children <-- Signal.sequence(domSignals)
      )
    })
  }


}

object HtmlBlockLibraryTab {


  def getDefaultLibraryPrograms(displayConfig: BeDisplayConfig): List[BeProgram] = List(
    BeProgram.createOneParFunc(displayConfig, "move 100", "distance", BeDataType.Numeric, "100"),
    BeProgram.createOneParFunc(displayConfig, "rotate ↺", "degree", BeDataType.Numeric, "90"),
    BeProgram.createOneParFunc(displayConfig, "stringFunc", "someString", BeDataType.String, "This is a text :)"),
    BeProgram.createSimpleFunc(displayConfig, "add days", List("startDate", "daysToAdd"), List(BeDataType.Date, BeDataType.Numeric), List("11.10.1999", "3"), Some(Set(BeDataType.Date))),
  )


}
