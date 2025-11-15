package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.model.vm.types.*
import interactionPlugins.blockEnvironment.config.{BeTreeControllerConfig, BeTreeDisplayConfig}
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
      val treeDisplays = programs.map(curProg => HtmlBeTreeDisplay.forLibraryTab(curProg, editorState))

      val domSignals = treeDisplays.map(_.treeRenderingSignal.map(_._1))

      div(
        cls := "block-library-tab",
        children <-- Signal.sequence(domSignals)
      )
    })
  }


}

object HtmlBlockLibraryTab {


  def getDefaultLibraryPrograms(displayConfig: BeTreeDisplayConfig): List[BeProgram] = {


    val forwardName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "forward", AppLanguage.German -> "vorwärts"))
    val rotateName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "rotateClockwise", AppLanguage.German -> "dreheImUhrzeigersinn"))
    val distName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "distance", AppLanguage.German -> "distanz"))
    val degName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "degree", AppLanguage.German -> "grad"))

    val dayName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "addDays", AppLanguage.German -> "addiereTage"))
    val dateName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "date", AppLanguage.German -> "datum"))
    val dayNrName: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> "dayNr", AppLanguage.German -> "tageAnzahl"))

    List(

      BeProgram.createSimpleFunc(displayConfig, forwardName, List(distName), List(BeDataType.Numeric), List("100"), None),
      BeProgram.createSimpleFunc(displayConfig, rotateName, List(degName), List(BeDataType.Numeric), List("100"), None),
      BeProgram.createSimpleFunc(displayConfig, dayName, List(dateName, dayNrName), List(BeDataType.Date, BeDataType.Numeric), List("11.10.1999", "3"), None),
      //BeProgram.parseSimpleIf(),
      //BeProgram.parseSimpleWhile()
    )
  }


}
