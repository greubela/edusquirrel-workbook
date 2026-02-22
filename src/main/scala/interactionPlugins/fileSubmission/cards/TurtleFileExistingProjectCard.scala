package interactionPlugins.fileSubmission.cards

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription
import interactionPlugins.fileSubmission.{TurtleStitchFacade, TurtleStitchFileFactory}
import org.scalajs.dom.URL
import util.HtmlHelper
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

import scala.concurrent.ExecutionContext

case class TurtleFileExistingProjectCard(workbookInfoVar: Var[WorkbookInfo], filename: String, existingProjectImg: ImageDescription, existingProject: URL) extends HtmlWorkbookElement {

  private val testXML: String = "<project name=\"miniprog\" app=\"TurtleStitch 2.11, http://www.turtlestitch.org\" version=\"2\"><notes></notes><scenes select=\"1\"><scene name=\"miniprog\"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name=\"Bühne\" width=\"480\" height=\"360\" costume=\"0\" color=\"255,255,255,1\" tempo=\"60\" threadsafe=\"false\" penlog=\"false\" volume=\"100\" pan=\"0\" lines=\"round\" ternary=\"false\" hyperops=\"true\" codify=\"false\" inheritance=\"true\" sublistIDs=\"false\" id=\"6\"><<costumes><list struct=\"atomic\" id=\"7\"></list></costumes><sounds><list struct=\"atomic\" id=\"8\"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select=\"1\"><sprite name=\"Objekt\" idx=\"1\" x=\"0\" y=\"0\" heading=\"90\" scale=\"0.1\" volume=\"100\" pan=\"0\" rotation=\"1\" draggable=\"true\" hidden=\"true\" costume=\"0\" color=\"0,0,0,1\" pen=\"tip\" id=\"13\"><costumes><list struct=\"atomic\" id=\"14\"></list></costumes><sounds><list struct=\"atomic\" id=\"15\"></list></sounds><blocks></blocks><variables></variables><scripts><script x=\"179\" y=\"80\"><block s=\"receiveGo\"></block><block s=\"forward\"><l>75</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>\n<creator>anonymous</creator>\n<origCreator>anonymous</origCreator>\n<origName></origName>\n</project>"

  private val headline: Element = h3(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchFileFactory.languageMapProvidedProjectLabel))
  )

  private val downloadButton: Element = button(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchFileFactory.languageMapDownloadButton)),
    onClick --> { _ =>
      HtmlHelper.downloadFromUrl("TurtleStitch_" + filename + ".xml", existingProject)
    }
  )

  private val ec: ExecutionContext = ExecutionContext.global

  private val languageSignal: Signal[String] =
    workbookInfoVar.signal.map(_.config.currentWorkbookLanguage.nameAbbr).distinct

  private val imageDataSrcVar: Var[Option[String]] = Var(None)

  imageDataSrcVar.signal.foreach(newSignal => println("received to var: " + newSignal.toString))(unsafeWindowOwner)

  // Mount-owned binding: prevents updates from targeting detached nodes when the card is remounted
  private val loadImageOnLanguageChangeBinder: Modifier[HtmlElement] = onMountBind { _ =>
    EventStream
      .merge(EventStream.fromValue(()), languageSignal.changes.mapTo(()))
      .sample(languageSignal)
      .flatMapSwitch { lang =>
        EventStream
          .fromFuture(TurtleStitchFacade.calcProgramPngDataSrc(testXML, lang))(ec)
          .map(raw => Some(raw.replaceAll("\\s", "")))
          .recoverToTry
          .map {
            case scala.util.Success(value) => value
            case scala.util.Failure(error) => Some(s"Error: ${error.getMessage}")
          }
          .startWith(None)
      } --> imageDataSrcVar.writer
  }

  private val domElement: Element = div(
    cls := "preview-card",
    headline,
    div(
      cls := "preview-content",
      child <-- imageDataSrcVar.signal.map {
       /* case Some(value) if value.startsWith("data:image") => {
          println("new img with value!")
          img(src := value, styleAttr := "max-width: 100%; border: 1px solid #ccc;")
        }*/
        case Some(value) => {
          println("new span with value: " + value)
          span(value)
        }
        case None => {
          println("new span!")
          span("Loading…")
        }
      }
    ),
    loadImageOnLanguageChangeBinder,
    downloadButton
  )

  private def getWorkshopInfoVar = workbookInfoVar

  override def getDomElement(): Element = domElement

  def getAsPreviewLine: HtmlWorkbookElement = new HtmlWorkbookElement {
    override def workbookInfoVar: L.Var[WorkbookInfo] = getWorkshopInfoVar

    override def getDomElement(): L.Element = div(
      cls := "workbook-interaction preview-line",
      domElement
    )
  }

}
