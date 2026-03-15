package interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.*
import contentmanagement.storage.DataStorage
import interactionPlugins.turtleStitchPlugin.*
import org.scalajs.dom.URL
import util.ReadOnlyVar
import util.web.DownloadHelper
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import contentmanagement.model.file.*

import scala.concurrent.ExecutionContext

case class TurtleFileShowProgramXmlCard(
                                         workbookInfoVar: Var[WorkbookInfo],
                                         desiredFilename: String,
                                         headlineLanguageMap: LanguageMap[HumanLanguage],
                                         nonexistingImageLanguageMap: LanguageMap[HumanLanguage],
                                         projectXmlVar: StrictSignal[Option[String]]
                                       ) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(headlineLanguageMap))
  )

  private val downloadButton: Element = button(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchLanguageMaps.languageMapDownloadButton)),
    onClick --> { _ =>
      projectXmlVar.now().foreach(currentXml => {
        DownloadHelper.downloadFile(desiredFilename, currentXml)
      })
    }
  )

  private def mapDataSrcStringToElement(dataSrcString: Option[String]): Element = dataSrcString match {
    case Some(value) if value.startsWith("data:image") => img(src := value, styleAttr := "max-width: 100%")
    case Some(value) => span(value)
    case None => span(
      child <-- workbookInfoVar.signal.map(_.languageStringFromMap(nonexistingImageLanguageMap))
      //.signal.map(info => nonexistingImageLanguageMap.getInLanguage(info.config.currentWorkbookLanguage))
      //
    )
  }

  private def getSvgProgramDisplayElement(humanLanguage: HumanLanguage, xml: Option[String]): Element = {
    if (xml.isEmpty) mapDataSrcStringToElement(None)
    else {
      val elVar = TurtleStitchFacade.programSvgDataSrcStorage.loadIntoVariable((xml.get, humanLanguage))(ExecutionContext.global)
      div(
        child <-- elVar.signal.map(mapDataSrcStringToElement)
      )
    }
  }

  private def getPngOutputDisplayElement(xml: Option[String]): Element = {
    if (xml.isEmpty) mapDataSrcStringToElement(None)
    else {
      val elVar = TurtleStitchFacade.programOutputDataSrcStorage.loadIntoVariable(xml.get)(ExecutionContext.global)
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
        combinedSignal.map(tup => getSvgProgramDisplayElement(tup._1, tup._2))
      }),
    downloadButton
  )

  private def getWorkshopInfoVar = workbookInfoVar

  override def getDomElement(): Element = domElement

  lazy val asWorkbookElement: HtmlWorkbookElement = new HtmlWorkbookElement() {
    override def workbookInfoVar: L.Var[WorkbookInfo] = getWorkshopInfoVar

    private val myDomElement: L.Element = div(
      cls := "workbook-interaction preview-line",
      domElement
    )

    override def getDomElement(): L.Element = myDomElement
  }

}

object TurtleFileShowProgramXmlCard {

  def apply(
             workbookInfoVar: Var[WorkbookInfo],
             fileDescription: FileDescription,
           ): TurtleFileShowProgramXmlCard = {
    TurtleFileShowProgramXmlCard(
      workbookInfoVar,
      "TurtleStitch_" + fileDescription.filename ,
      TurtleStitchLanguageMaps.languageMapProvidedProjectLabel,
      TranslationMaps.languageMapImageLoading,
      DataStorage.fileDataStore.loadIntoVariable(fileDescription)(ExecutionContext.global).signal.mapLazy(_.map(_.fileDataAsUtf8String))
    )
  }

  def apply(
             forUploadButton: TurtleStitchFileUploadButtonCard
           ): TurtleFileShowProgramXmlCard = {
    TurtleFileShowProgramXmlCard(
      forUploadButton.workbookInfoVar,
      "exercise" + forUploadButton.id,
      TurtleStitchLanguageMaps.languageMapShowUploadedProgramText,
      TurtleStitchLanguageMaps.languageMapShowEmptyPreview,
      forUploadButton.interactionVariable.interactionSignal.mapLazy(curVal => Some(curVal))
    )
  }

}

