package interactionPlugins.fileSubmission.cards

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription
import contentmanagement.model.language.AppLanguage.German
import contentmanagement.storage.ImageStorage
import interactionPlugins.fileSubmission.{TurtleStitchFacade, TurtleStitchFileFactory}
import org.scalajs.dom.URL
import util.HtmlHelper
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

case class TurtleFileExistingProjectCard(workbookInfoVar: Var[WorkbookInfo], id: String, filename: String, existingProjectImg: ImageDescription, existingProject: URL) extends HtmlWorkbookElement {

  private val testXML2: String = "<project name=\"miniprog\" app=\"TurtleStitch 2.11, http://www.turtlestitch.org\" version=\"2\"><notes></notes><scenes select=\"1\"><scene name=\"miniprog\"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name=\"Bühne\" width=\"480\" height=\"360\" costume=\"0\" color=\"255,255,255,1\" tempo=\"60\" threadsafe=\"false\" penlog=\"false\" volume=\"100\" pan=\"0\" lines=\"round\" ternary=\"false\" hyperops=\"true\" codify=\"false\" inheritance=\"true\" sublistIDs=\"false\" id=\"6\"><<costumes><list struct=\"atomic\" id=\"7\"></list></costumes><sounds><list struct=\"atomic\" id=\"8\"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select=\"1\"><sprite name=\"Objekt\" idx=\"1\" x=\"0\" y=\"0\" heading=\"90\" scale=\"0.1\" volume=\"100\" pan=\"0\" rotation=\"1\" draggable=\"true\" hidden=\"true\" costume=\"0\" color=\"0,0,0,1\" pen=\"tip\" id=\"13\"><costumes><list struct=\"atomic\" id=\"14\"></list></costumes><sounds><list struct=\"atomic\" id=\"15\"></list></sounds><blocks></blocks><variables></variables><scripts><script x=\"179\" y=\"80\"><block s=\"receiveGo\"></block><block s=\"forward\"><l>75</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>\n<creator>anonymous</creator>\n<origCreator>anonymous</origCreator>\n<origName></origName>\n</project>"

  private val testXML: String = "<project name=\"updown_forward\" app=\"TurtleStitch 2.11, http://www.turtlestitch.org\" version=\"2\"><notes></notes><scenes select=\"1\"><scene name=\"updown_forward\"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name=\"Bühne\" width=\"480\" height=\"360\" costume=\"0\" color=\"255,255,255,1\" tempo=\"60\" threadsafe=\"false\" penlog=\"false\" volume=\"100\" pan=\"0\" lines=\"round\" ternary=\"false\" hyperops=\"true\" codify=\"false\" inheritance=\"true\" sublistIDs=\"false\" id=\"6\"><costumes><list struct=\"atomic\" id=\"7\"></list></costumes><sounds><list struct=\"atomic\" id=\"8\"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select=\"1\"><sprite name=\"Objekt\" idx=\"1\" x=\"35.355339059326525\" y=\"-35.35533905932712\" heading=\"135\" scale=\"0.1\" volume=\"100\" pan=\"0\" rotation=\"1\" draggable=\"true\" hidden=\"true\" costume=\"0\" color=\"0,0,0,1\" pen=\"tip\" id=\"13\"><costumes><list struct=\"atomic\" id=\"14\"></list></costumes><sounds><list struct=\"atomic\" id=\"15\"></list></sounds><blocks></blocks><variables></variables><scripts><script x=\"70\" y=\"80\"><block s=\"receiveGo\"></block><block s=\"gotoXY\"><l>0</l><l>0</l></block><block s=\"setHeading\"><l>90</l></block><block s=\"clear\"></block><block s=\"runningStitch\"><l>5</l></block><block s=\"forward\"><l>100</l></block><block s=\"up\"></block><block s=\"gotoXY\"><l>0</l><l>0</l></block><block s=\"down\"></block><block s=\"turn\"><l>45</l></block><block s=\"zigzagStitch\"><l>5</l><l>5</l><l><bool>true</bool></l></block><block s=\"forward\"><l>50</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>\n<creator>anonymous</creator>\n<origCreator></origCreator>\n<origName>reset_forward</origName>\n</project>"

  private val headline: Element = h3(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchFileFactory.languageMapProvidedProjectLabel))
  )

  private val downloadButton: Element = button(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchFileFactory.languageMapDownloadButton)),
    onClick --> { _ =>
      HtmlHelper.downloadFromUrl("TurtleStitch_" + filename + ".xml", existingProject)
    }
  )


  private def mapDataSrcStringToElement(dataSrcString: Option[String]): Element = dataSrcString match {
    case Some(value) if value.startsWith("data:image") => img(src := value, styleAttr := "max-width: 100%; border: 1px solid #ccc;")
    case Some(value) => span(value)
    case None => span("Loading…")
  }

  private val domElement: Element = div(
    cls := "preview-card",
    headline,
    div(
      cls := "preview-content",
      //child.text <-- TurtleStitchFacade.getProgramPngDataSrc(testXML, German).signal.map(_.getOrElse("loading")),
      child <-- TurtleStitchFacade.getProgramPngDataSrc(testXML, workbookInfoVar.signal.map(_.config.currentWorkbookLanguage)).map(mapDataSrcStringToElement)
    ),
    div(
      cls := "preview-content",
      child <-- ImageStorage.loadFullImageIntoVar(existingProjectImg)(ExecutionContext.global).signal.map(fullImgOp => mapDataSrcStringToElement(fullImgOp.map(_.imgSourceString)))
    ),
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
