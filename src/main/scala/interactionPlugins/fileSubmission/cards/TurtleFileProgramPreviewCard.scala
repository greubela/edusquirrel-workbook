package interactionPlugins.fileSubmission.cards

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription
import contentmanagement.model.language.AppLanguage.German
import contentmanagement.model.language.{AppLanguage, HumanLanguage, TranslationMaps}
import contentmanagement.storage.{DataStorage, ImageStorage}
import interactionPlugins.fileSubmission.{TurtleStitchFacade, TurtleStitchFileFactory}
import org.scalajs.dom.URL
import util.HtmlHelper
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

case class TurtleFileProgramPreviewCard(workbookInfoVar: Var[WorkbookInfo], id: String, existingProject: URL) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchFileFactory.languageMapProvidedProjectLabel))
  )

  private val downloadButton: Element = button(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchFileFactory.languageMapDownloadButton)),
    onClick --> { _ =>
      HtmlHelper.downloadFromUrl("TurtleStitch_" + existingProject.pathname.split("/").last + ".xml", existingProject)
    }
  )

  private def mapDataSrcStringToElement(dataSrcString: Option[String]): Element = dataSrcString match {
    case Some(value) if value.startsWith("data:image") => img(src := value, styleAttr := "max-width: 100%")
    case Some(value) => span(value)
    case None => span(
      child <-- workbookInfoVar.signal.map(info => TranslationMaps.languageMapImageLoading.getInLanguage(info.config.currentWorkbookLanguage))
    )
  }

  private def getDisplayElement(humanLanguage: HumanLanguage, xml: Option[String]): Element = {
    if(xml.isEmpty) mapDataSrcStringToElement(None)
    else {
      val elVar = TurtleStitchFacade.programSvgDataSrcStorage.loadIntoVariable( (xml.get, humanLanguage) )(ExecutionContext.global)
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
      //child.text <-- TurtleStitchFacade.getProgramPngDataSrc(testXML, German).signal.map(_.getOrElse("loading")),
      child <-- {
        val languageSignal: Signal[HumanLanguage] = workbookInfoVar.signal.map(_.config.currentWorkbookLanguage)
        val xmlSignal: StrictSignal[Option[String]] = DataStorage.urlDataStore.loadIntoVariable(existingProject)(ExecutionContext.global).signal
        val combinedSignal: Signal[(HumanLanguage, Option[String])] = languageSignal.combineWith(xmlSignal)
        combinedSignal.map(tup => getDisplayElement(tup._1, tup._2))
      })
    ,
    downloadButton
  )

  private def getWorkshopInfoVar = workbookInfoVar

  override def getDomElement(): Element = domElement

  def getAsPreviewLine(): HtmlWorkbookElement = new HtmlWorkbookElement() {
    override def workbookInfoVar: L.Var[WorkbookInfo] = getWorkshopInfoVar

    private val myDomElement: L.Element = div(
      cls := "workbook-interaction preview-line",
      domElement
    )

    override def getDomElement(): L.Element = myDomElement
  }

}
