package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.homepage.control.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.contentIdStringSignal
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.TurtleStitchWorkerFacade
import it.evadid.workbook.plugins.TurtleStitch.TurtleStitchProjectState

import scala.concurrent.ExecutionContext

object HtmlTurtleStitchPreviewRenderer {

  def render(turtleStitchState: State[TurtleStitchProjectState]): Element = {
    val xmlSignal: Signal[Option[String]] = turtleStitchState.toAirstreamVar.signal.map(_.programXml)
    renderWithSignal(xmlSignal)
  }

  def render(fileDescription: FileDescription): Element = {
    val xmlSignal: Signal[Option[String]] = fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global).toAirstreamVar.signal.mapLazy(_.map(_.fileDataAsUtf8String))
    renderWithSignal(xmlSignal)
  }

  private def renderEmpty(): Element = {
    div(text <-- contentIdStringSignal(LanguageMapContentId("TurtleStitch/showEmptyPreview")))
  }

  private def renderLoading(): Element = {
    div(text <-- contentIdStringSignal(LanguageMapContentId("basic/imageLoadingMap")))
  }

  private def tryRenderStringAsImageSrc(strValue: String): Element = {
    if (strValue.trim.isEmpty) renderEmpty()
    else if (strValue.startsWith("data:image")) img(src := strValue, styleAttr := "max-width: 100%")
    else if (strValue.contains("Error")) span(strValue)
    //else span("[Error while rendering as image: " + value + "]")
    else renderLoading()
  }

  private def renderWithSignal(xmlSignal: Signal[Option[String]]): Element = {
    div(
      cls := "preview-card",
      div(
        cls := "preview-content",
        child <-- getImageSignal(xmlSignal, HtmlFullWorkbookApp.fullInfo.signals.currentLanguage)
      )
    )
  }

  private def getImageSignal(xmlSignal: Signal[Option[String]], languageSignal: Signal[HumanLanguage]): Signal[Element] = {
    xmlSignal.map(_.getOrElse("")).combineWith(languageSignal).flatMapSwitch(tup => {
      if (tup._1.trim.isEmpty) Var(renderEmpty()).signal
      else turtleStitchProjectXmlToProgramPngSrcState(tup._1, tup._2).toAirstreamVar.signal.map {
        case Some(imgSrc) => tryRenderStringAsImageSrc(imgSrc)
        case None => renderLoading()
      }
    })
  }

  private def turtleStitchProjectXmlToProgramPngSrcState(xml: String, humanLanguage: HumanLanguage): State[Option[String]] = {
    if (xml.trim.isEmpty) State(None)
    else try {
      TurtleStitchWorkerFacade.getGreenFlagProgramSnapshotDataSrc(xml, humanLanguage)
    } catch case e: Throwable =>
      e.printStackTrace()
      State(Some("[Error at loading image: " + e.getMessage + "]"))
  }


}
