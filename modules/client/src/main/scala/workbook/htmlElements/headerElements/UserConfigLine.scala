package workbook.htmlElements.headerElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import datastructures.web.file.FileDescription
import org.scalajs.dom
import org.scalajs.dom.{File, HTMLButtonElement, HTMLInputElement}
import workbook.htmlElements.basic.HtmlButtonElement
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.FullInfo
import workbook.model.info.analyze.WorkbookUserDataAnalyzer

case class UserConfigLine(fullInfo: FullInfo) extends HtmlWorkbookElement {

  private val resetButton = HtmlButtonElement.withTextLabel(fullInfo, "basic/resetLocalStorage", event => fullInfo.control.saveAndResetAllInfo())
  private val downloadDataButton = HtmlButtonElement.withTextLabel(fullInfo, "basic/downloadEverything", event => fullInfo.control.downloadAllAvailableData())
  private val uploadButton = HtmlButtonElement.withTextLabel(fullInfo, "basic/uploadSession", event => uploadInput.ref.click())

  private lazy val uploadInput: ReactiveHtmlElement[HTMLInputElement] = input(
    styleAttr := "display:none;",
    typ := "file",
    accept := "json",
    onChange --> { event =>
      val inputElement = event.target.asInstanceOf[dom.html.Input]
      if (inputElement.files.length > 0) fileToUploadSelected(inputElement.files.item(0))
    }
  )

  private def fileToUploadSelected(file: File): Unit = {
    fullInfo.current.workbookUserData.foreach(_.upload(FileDescription(file)))
  }

  private val domElement: Element = div(
    styleAttr := "display:none;",
    uploadInput,
    resetButton.getDomElement(),
    downloadDataButton.getDomElement(),
    uploadButton.getDomElement(),
  )

  override def getDomElement(): Element = domElement
}
