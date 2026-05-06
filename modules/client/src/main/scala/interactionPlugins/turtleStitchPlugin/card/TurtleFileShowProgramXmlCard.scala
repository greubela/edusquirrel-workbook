package interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.web.file.FileDescription
import interactionPlugins.turtleStitchPlugin.*
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.*
import util.web.DownloadHelper
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo

import scala.concurrent.ExecutionContext
case class TurtleFileShowProgramXmlCard(
                                         fullInfo: FullInfo,
                                         desiredFilename: String,
                                         headlineLanguageMapId: String,
                                         nonexistingImageLanguageMapId: String,
                                         projectXmlVar: StrictSignal[Option[String]],
                                         downloadButtonMapId: String = "TurtleStitch/downloadButton"
                                       ) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    text <-- fullInfo.signals.stringFromLanguageMapId(headlineLanguageMapId)
  )

  private val downloadButton: Element = button(
    text <-- fullInfo.signals.stringFromLanguageMapId(downloadButtonMapId),
    onClick --> { _ =>
      projectXmlVar.now().foreach(f = currentXml => {
        DownloadHelper.downloadFile(desiredFilename, currentXml)
      })
    }
  )

  private def mapDataSrcStringToElement(dataSrcString: Option[String]): Element = dataSrcString match {
    case Some(value) if value.startsWith("data:image") => img(src := value, styleAttr := "max-width: 100%")
    case Some(value) => span(value)
    case None => span(
      text <-- fullInfo.signals.stringFromLanguageMapId(nonexistingImageLanguageMapId)
    )
  }

  private def getPngProgramDisplayElement(humanLanguage: HumanLanguage, xml: Option[String]): Element = {
    if (xml.isEmpty) mapDataSrcStringToElement(None)
    else {
      val elState: State[Option[String]] = TurtleStitchWorkerFacade.getGreenFlagProgramSnapshotDataSrc(xml.get, humanLanguage)
      div(
        child <-- elState.toAirstreamVar.signal.map(mapDataSrcStringToElement)
      )
    }
  }


  private val domElement: Element = div(
    cls := "preview-card",
    headline,
    div(
      cls := "preview-content",
      child <-- {
        val xmlSignal: Signal[Option[String]] = projectXmlVar.signal
        val languageSignal: Signal[HumanLanguage] = fullInfo.signals.currentLanguage
        val combinedSignal: Signal[(HumanLanguage, Option[String])] = languageSignal.combineWith(xmlSignal)
        combinedSignal.map(tup => getPngProgramDisplayElement(tup._1, tup._2))
      }),
    downloadButton
  )

  override def getDomElement(): Element = domElement

  lazy val asWorkbookElement: HtmlWorkbookElement = new HtmlWorkbookElement() {

    override def fullInfo: FullInfo = TurtleFileShowProgramXmlCard.this.fullInfo

    private val myDomElement: L.Element = div(
      cls := "workbook-interaction preview-line",
      domElement
    )

    override def getDomElement(): L.Element = myDomElement
  }

}

object TurtleFileShowProgramXmlCard {

  def apply(
             fullInfo: FullInfo,
             fileDescription: FileDescription,
           ): TurtleFileShowProgramXmlCard = {
    TurtleFileShowProgramXmlCard(
      fullInfo,
      "TurtleStitch_" + fileDescription.filename,
      "TurtleStitch/providedProjectLabel",
      "basic/imageLoadingMap",
      fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global).toAirstreamVar.signal.mapLazy(_.map(_.fileDataAsUtf8String)),
      "TurtleStitch/downloadButton"
    )
  }

  def apply(
             forUploadButton: TurtleStitchFileUploadButtonCard
           ): TurtleFileShowProgramXmlCard = {
    TurtleFileShowProgramXmlCard(
      forUploadButton.fullInfo,
      "exercise" + forUploadButton.id,
      "TurtleStitch/showUploadedProgramText",
      "TurtleStitch/showEmptyPreview",
      forUploadButton.interactionVariable.interactionSignal,
      "TurtleStitch/redownloadProgram"
    )
  }

}
