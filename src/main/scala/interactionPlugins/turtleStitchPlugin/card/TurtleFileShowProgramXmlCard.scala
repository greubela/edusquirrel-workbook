package interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.core.language.{HumanLanguage, LanguageMap, TranslationMaps}
import datastructures.web.file.FileDescription
import datastructures.web.storage.AsyncDataCache
import interactionPlugins.turtleStitchPlugin.*
import util.web.DownloadHelper
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

import scala.concurrent.ExecutionContext

case class TurtleFileShowProgramXmlCard(
                                         workbookInfo: AllWorkbookInfo,
                                         desiredFilename: String,
                                         headlineLanguageMap: LanguageMap[HumanLanguage],
                                         nonexistingImageLanguageMap: LanguageMap[HumanLanguage],
                                         projectXmlVar: StrictSignal[Option[String]]
                                       ) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    text <-- workbookInfo.stringSignalFromLanguageMap(headlineLanguageMap)
  )

  private val downloadButton: Element = button(
    text <-- workbookInfo.stringSignalFromLanguageMap(TurtleStitchLanguageMaps.languageMapDownloadButton),
    onClick --> { _ =>
      projectXmlVar.now().foreach(f = currentXml => {
        DownloadHelper.downloadFile(desiredFilename, currentXml)
        /*val res = TurtleStitchFacade.programXmlRunOutputStorage.loadIntoVariable(currentXml)(ExecutionContext.global)
        println("res: " + res)
        
        val res2 = TurtleXmlParser.parse(currentXml)
        println("res2: " + res2)
        val res3 = TurtleRenderer.renderToPngDataUrl(res2)
        println("res3: " + res3)
        println("xml: " + currentXml)
        val res = TurtleStitchBeExpressionAdapter.fromXml(currentXml)
        println("python: " + res.getInLanguage(Python, English))
        println("res: " + res)*/
      })
    }
  )

  private def mapDataSrcStringToElement(dataSrcString: Option[String]): Element = dataSrcString match {
    case Some(value) if value.startsWith("data:image") => img(src := value, styleAttr := "max-width: 100%")
    case Some(value) => span(value)
    case None => span(
      text <-- workbookInfo.stringSignalFromLanguageMap(nonexistingImageLanguageMap)
      //.signal.map(info => nonexistingImageLanguageMap.getInLanguage(info.config.currentWorkbookLanguage))
      //
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

  private def getWorkshopInfoVar = workbookInfoVar

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
      TurtleStitchLanguageMaps.languageMapProvidedProjectLabel,
      TranslationMaps.languageMapImageLoading,
      workbookInfo.technicalElements.fileStore.loadIntoVariable(fileDescription)(ExecutionContext.global).signal.mapLazy(_.map(_.fileDataAsUtf8String))
    )
  }

  def apply(
             forUploadButton: TurtleStitchFileUploadButtonCard
           ): TurtleFileShowProgramXmlCard = {
    TurtleFileShowProgramXmlCard(
      forUploadButton.workbookInfo,
      "exercise" + forUploadButton.id,
      TurtleStitchLanguageMaps.languageMapShowUploadedProgramText,
      TurtleStitchLanguageMaps.languageMapShowEmptyPreview,
      forUploadButton.interactionVariable.interactionSignal.mapLazy(curVal => Some(curVal))
    )
  }

}

