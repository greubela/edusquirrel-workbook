package interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.core.language.HumanLanguage
import datastructures.web.file.FileDescription
import interactionPlugins.turtleStitchPlugin.*
import util.web.DownloadHelper
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import scala.concurrent.ExecutionContext

case class TurtleFileShowProgramXmlCard(
                                         workbookInfo: AllWorkbookInfo,
                                         desiredFilename: String,
                                         headlineLanguageMapId: String,
                                         nonexistingImageLanguageMapId: String,
                                         projectXmlVar: StrictSignal[Option[String]]
                                       ) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    text <-- workbookInfo.stringSignalFromLanguageMapId(headlineLanguageMapId)(ExecutionContext.global)
  )

  private val downloadButton: Element = button(
    text <-- workbookInfo.stringSignalFromLanguageMapId("TurtleStitch/downloadButton")(ExecutionContext.global),
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
      text <-- workbookInfo.stringSignalFromLanguageMapId(nonexistingImageLanguageMapId)(ExecutionContext.global)
    )
  }

  private def getPngProgramDisplayElement(humanLanguage: HumanLanguage, xml: Option[String]): Element = {
    if (xml.isEmpty) mapDataSrcStringToElement(None)
    else {
      val elVar: Var[Option[String]] = TurtleStitchFacade.getPngDataSrcOfGreenFlagProgramEditor(xml.get, humanLanguage)
      div(
        child <-- elVar.signal.map(mapDataSrcStringToElement)
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
        val languageSignal: Signal[HumanLanguage] = workbookInfoVar.signal.map(_.config.currentWorkbookLanguage)
        val combinedSignal: Signal[(HumanLanguage, Option[String])] = languageSignal.combineWith(xmlSignal)
        combinedSignal.map(tup => getPngProgramDisplayElement(tup._1, tup._2))
      }),
    downloadButton
  )

  override def getDomElement(): Element = domElement

  lazy val asWorkbookElement: HtmlWorkbookElement = new HtmlWorkbookElement() {

    override def workbookInfo: AllWorkbookInfo = TurtleFileShowProgramXmlCard.this.workbookInfo

    private val myDomElement: L.Element = div(
      cls := "workbook-interaction preview-line",
      domElement
    )

    override def getDomElement(): L.Element = myDomElement
  }

}

object TurtleFileShowProgramXmlCard {

  def apply(
             workbookInfo: AllWorkbookInfo,
             fileDescription: FileDescription,
           ): TurtleFileShowProgramXmlCard = {
    TurtleFileShowProgramXmlCard(
      workbookInfo,
      "TurtleStitch_" + fileDescription.filename,
      "TurtleStitch/providedProjectLabel",
      "basic/imageLoadingMap",
      workbookInfo.technicalElements.fileStore.loadIntoVariable(fileDescription)(ExecutionContext.global).signal.mapLazy(_.map(_.fileDataAsUtf8String))
    )
  }

  def apply(
             forUploadButton: TurtleStitchFileUploadButtonCard
           ): TurtleFileShowProgramXmlCard = {
    TurtleFileShowProgramXmlCard(
      forUploadButton.workbookInfo,
      "exercise" + forUploadButton.id,
      "TurtleStitch/showUploadedProgramText",
      "TurtleStitch/showEmptyPreview",
      forUploadButton.interactionVariable.interactionSignal.mapLazy(curVal => Some(curVal))
    )
  }

}
