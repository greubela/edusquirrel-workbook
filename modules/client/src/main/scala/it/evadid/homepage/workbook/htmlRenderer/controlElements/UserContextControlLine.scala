package it.evadid.homepage.workbook.htmlRenderer.controlElements
/*
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.workbook.syncDestination.LocalStorageSync
import it.evadid.workbook.elements.structureElements.Workbook
import org.scalajs.dom
import org.scalajs.dom.{File, HTMLInputElement}
import todomove.datastructures.web.file.FileFactory


private case class UserContextControlLine(workbook: Workbook) extends HtmlAppElement {

  private val resetButton = HtmlButtonElement.withTextLabel(
    LanguageMapContentId("basic/resetLocalStorage"),
    event => onResetButton()
  )

  private val downloadDataButton = HtmlButtonElement.withTextLabel(
    LanguageMapContentId("basic/downloadEverything"),
    event => onDownloadButton()
  )

  private val uploadButton = HtmlButtonElement.withTextLabel(
    LanguageMapContentId("basic/uploadSession"),
    event => uploadInput.ref.click()
  )

  def onUploadButton(fileSelected: File): Unit = {
    val dataParsed: Unit = fullInfo.current.workbookUserData.foreach(_.upload(FileFactory.fromFile(fileSelected)))
  }

  def onDownloadButton(): Unit = {
    fullInfo.cacheControl.downloadAllAvailableData()
  }

  def onResetButton(): Unit = {
    onDownloadButton()
    LocalStorageSync.resetCompleteStorage()
  }

  private lazy val uploadInput: ReactiveHtmlElement[HTMLInputElement] = input(
    styleAttr := "display:none;",
    typ := "file",
    accept := "json",
    onChange --> { event =>
      val inputElement = event.target.asInstanceOf[dom.html.Input]
      if (inputElement.files.length > 0) onUploadButton(inputElement.files.item(0))
    }
  )

  private val domElement: Element = div(
   styleAttr := "display:none;",
    uploadInput,
    resetButton.getDomElement(),
    downloadDataButton.getDomElement(),
    uploadButton.getDomElement(),
  )

  override def getDomElement(): Element = domElement
}

*/