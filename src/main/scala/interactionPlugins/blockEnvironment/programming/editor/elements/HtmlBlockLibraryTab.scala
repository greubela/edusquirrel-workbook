package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.BeProgram

case class HtmlBlockLibraryTab(
                                editorState: EditorState,
                                programFactory: BeTreeDisplayConfig => List[BeProgram],
                                mainControllerTreeListener: Var[BeTreeControllerConfig]
                              ) {

  def getDisplays(displayConfig: BeTreeDisplayConfig): List[BeProgram] = programFactory(displayConfig)

  lazy val toDomSignal: Signal[L.Element] = {
    val useSignal = editorState.libraryTreeDisplayConfig.signal
    useSignal.map(curDisplayConfig => {
      val programs = programFactory(curDisplayConfig)
      val treeDisplays = programs.map(curProg => HtmlBeTreeDisplay(Var(curProg).signal, editorState, mainControllerTreeListener, _.libraryTreeDisplayConfig ))

      val domSignals = treeDisplays.map(_.toDomSignal)

      div(
        cls := "block-library-tab",
        children <-- Signal.sequence(domSignals)
      )
    })
  }


}

object HtmlBlockLibraryTab {


  def getDefaultLibraryPrograms(displayConfig: BeTreeDisplayConfig): List[BeProgram] = List(
    BeProgram.createOneParFunc(displayConfig, "move 100", "distance", BeDataType.Numeric, "100"),
    BeProgram.createOneParFunc(displayConfig, "rotate ↺", "degree", BeDataType.Numeric, "90"),
    BeProgram.createOneParFunc(displayConfig, "stringFunc", "someString", BeDataType.String, "This is a text :)"),
    BeProgram.createSimpleFunc(displayConfig, "add days", List("startDate", "daysToAdd"), List(BeDataType.Date, BeDataType.Numeric), List("11.10.1999", "3"), Some(BeDataType.Date)),
  )


}
