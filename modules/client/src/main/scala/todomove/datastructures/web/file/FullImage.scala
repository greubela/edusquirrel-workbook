package todomove.datastructures.web.file

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.*
import it.evadid.homepage.util.web.{DownloadHelper, JsHelpers}
import todomove.webElementsOld.webElements.svg.AppSvgElement

sealed trait FullImage extends it.evadid.core.datastructures.canvas.AppImage {

  def imageSourceString: String

  def download(): Unit

  def newDomImage: Image = img(
    src := imageSourceString
  )

}


object FullImage {

  case class DataSourceImage(dataSource: String, fileFormat: String) extends FullImage {

    assert(dataSource.startsWith("data:image/"), "dataSource must start with 'data:image/'")

    override def imageSourceString: String = dataSource

    override def download(): Unit = DownloadHelper.downloadFile(s"unknown.$fileFormat", dataSource)
  }

  case class LoadedFileImage(loadedFile: LoadedFile) extends FullImage {

    def download(): Unit = DownloadHelper.downloadFile(loadedFile.description.filenameWithExtension, loadedFile.data)

    override lazy val imageSourceString: String = {
      val b64str = JsHelpers.byteArrayToBase64String(loadedFile.data)
      "data:image/" + loadedFile.description.extensionOrEmpty + ";base64, " + b64str
    }
  }

  def apply(loadedFile: LoadedFile): FullImage = LoadedFileImage(loadedFile)


  //def apply(path: SvgPathBuilder): FullImage = ???

  def apply(element: AppSvgElement): FullImage = {
    ???
  }

}
