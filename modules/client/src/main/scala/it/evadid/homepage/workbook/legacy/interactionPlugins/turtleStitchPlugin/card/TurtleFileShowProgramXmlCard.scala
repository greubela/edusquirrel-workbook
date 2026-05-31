package it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.card
/*
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.*
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.homepage.HtmlAppElement
import it.evadid.homepage.util.web.DownloadHelper
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.TurtleStitchWorkerFacade
import it.evadid.homepage.workbook.legacy.model.abstractions.HtmlWorkbookElement
import it.evadid.homepage.workbook.legacy.model.info.FullInfo
import it.evadid.workbook.plugins.TurtleStitch.TurtleStitchProjectState

import scala.concurrent.ExecutionContext




case class TurtleFileShowProgramXmlCard(
                                         fullInfo: FullInfo,
                                         desiredFilename: String,
                                         headlineLanguageMapId: String,
                                         nonexistingImageLanguageMapId: String,
                                         projectXmlVar: StrictSignal[Option[String]],
                                         downloadButtonMapId: String = "TurtleStitch/downloadButton"
                                       ) extends HtmlWorkbookElement {


  private val downloadButton: Element = button(
    text <-- fullInfo.signals.stringFromLanguageMapId(downloadButtonMapId),
    onClick --> { _ =>
      projectXmlVar.now().foreach(f = currentXml => {
        DownloadHelper.downloadFile(desiredFilename, currentXml)
      })
    }
  )


  private val domElement: Element =

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
      "TurtleStitch_" + fileDescription.filenameWithExtension,
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
      forUploadButton.interactionVariable.createInteractionSignal(),
      "TurtleStitch/redownloadProgram"
    )
  }

}
*/