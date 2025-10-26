package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.AppFont
import contentmanagement.model.color.AppColorPalette
import contentmanagement.model.geometry.Dimension
import contentmanagement.model.language.AppLanguage
import interactionPlugins.blockEnvironment.config.{BeControllerState, BeDisplayConfig, BeRenderingConfig}
import interactionPlugins.blockEnvironment.programming.BeProgram
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.types.*

case class HtmlBlockLibraryTab(
                                libraryPrograms: List[BeProgram],
                                controllerStateVar: Var[BeControllerState],
                                renderingConfigSignal: Signal[BeRenderingConfig],
                                treeListener: HtmlBeTreeListener
                              ) extends HtmlWorkbookElement {

  //case class HtmlBeTreeDisplay(
  //                              treeSignal: Signal[BeBlockTree],
  //                              controllerStateSignal: Signal[BeControllerState],
  //                              displayConfigSignal: Signal[BeDisplayConfig],
  //                              renderingConfigSignal: Signal[BeRenderingConfig],
  //                              listener: HtmlBeTreeListener
  //                            ) {

  lazy val domElement: L.Element = {
    val config = Var(BeRenderingConfig.default())
    val displays = libraryPrograms.zipWithIndex.map((curProgram, index) => {
      val treeSignal = Var(curProgram.logicTree).signal
      val displayConfigSignal = Var(BeDisplayConfig.default()).signal

      HtmlBeTreeDisplay(treeSignal, controllerStateVar, displayConfigSignal, renderingConfigSignal, treeListener)
    })
    div(
      cls := "block-library-tab",
      children <-- Signal.sequence(displays.map(_.domSignal))
    )
  }

  override def getDomElement(): L.Element = domElement

}

object HtmlBlockLibraryTab {

  
  def turtleLibraryTab(displayConfig: BeDisplayConfig, controllerStateVar: Var[BeControllerState], libraryTreeListener: HtmlBeTreeListener): HtmlBlockLibraryTab = {

    val rendererConfig = BeRenderingConfig.default()
    val configVar = Var[BeRenderingConfig](rendererConfig)
    
    val programs: List[BeProgram] = List(
      BeProgram.createOneParFunc(displayConfig,"move 100", "distance", BeDataType.Numeric, "100"),
      BeProgram.createOneParFunc(displayConfig,"rotate ↺", "degree", BeDataType.Numeric, "90"),
      BeProgram.createOneParFunc(displayConfig,"stringFunc", "someString", BeDataType.String, "This is a text :)"),
      BeProgram.createSimpleFunc(displayConfig,"add days", List("startDate", "daysToAdd"), List(BeDataType.Date, BeDataType.Numeric), List("11.10.1999", "3")),
    )

    HtmlBlockLibraryTab(programs, controllerStateVar, configVar.signal, libraryTreeListener)
  }

}
