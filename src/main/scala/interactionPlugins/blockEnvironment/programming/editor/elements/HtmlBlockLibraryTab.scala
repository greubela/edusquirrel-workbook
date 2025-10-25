package interactionPlugins.blockEnvironment.programming.editor.elements

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.AppFont
import contentmanagement.model.color.AppColorPalette
import contentmanagement.model.geometry.Dimension
import contentmanagement.model.language.AppLanguage
import interactionPlugins.blockEnvironment.programming.rendering.BeRendererConfig
import interactionPlugins.blockEnvironment.programming.{BeDataType, BeProgram}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class HtmlBlockLibraryTab(
                                libraryPrograms: List[BeProgram],
                                configSignal: Signal[BeRendererConfig],
                                treeListener: HtmlBeTreeListener
                              ) extends HtmlWorkbookElement {

  lazy val domElement: L.Element = {
    val config = Var(BeRendererConfig.default())
    val displays = libraryPrograms.zipWithIndex.map((curTree, index) => {

      HtmlBeTreeDisplay(Var(curTree.displayTree).signal, configSignal, treeListener)
    })
    div(
      cls := "block-library-tab",
      children <-- Signal.sequence(displays.map(_.domSignal))
    )
  }

  override def getDomElement(): L.Element = domElement

}

object HtmlBlockLibraryTab {

  def turtleLibraryTab(libraryTreeListener: HtmlBeTreeListener): HtmlBlockLibraryTab = {
    val programs: List[BeProgram] = List(
      HtmlBlockLibrary.functionWithOnePar("move 100", BeDataType.Numeric, "100"),
      HtmlBlockLibrary.functionWithOnePar("rotate ↺", BeDataType.Numeric, "90"),
      HtmlBlockLibrary.functionWithOnePar("stringFunc", BeDataType.String, "90"),
      HtmlBlockLibrary.functionWithOnePar("greaterThan", BeDataType.Boolean, "true"),
      HtmlBlockLibrary.functionWithOnePar("plusThreeDays", BeDataType.Date, "11.10.1999"),
    )

    val rendererConfig = BeRendererConfig(AppFont.defaultFont, Dimension[Double](5, 5), Dimension[Double](10, 10), AppColorPalette.defaultRGBYPalette25, AppLanguage.English)
    val configVar = Var[BeRendererConfig](rendererConfig)

    HtmlBlockLibraryTab(programs, configVar.signal, libraryTreeListener)
  }

}
