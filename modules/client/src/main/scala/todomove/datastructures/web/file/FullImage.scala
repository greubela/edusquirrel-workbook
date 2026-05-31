package todomove.datastructures.web.file

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.*
import it.evadid.homepage.util.web.{DownloadHelper, JsHelpers}
import todomove.webElementsOld.webElements.svg.AppSvgElement

sealed trait FullImage {
  def fileDescription: FileDescription

  def imageSourceString: String

  def download(): Unit

  def newDomImage: Image = img(
    src := imageSourceString
  )

}


object FullImage {

  case class LoadedFileImage(loadedFile: LoadedFile) extends FullImage {

    val fileDescription: FileDescription = loadedFile.description

    def download(): Unit = DownloadHelper.downloadFile(loadedFile.description.filenameWithExtension, loadedFile.data)

    override lazy val imageSourceString: String = {
      val b64str = JsHelpers.byteArrayToBase64String(loadedFile.data)
      "data:image/" + loadedFile.description.extension + ";base64, " + b64str
    }
  }

  def apply(loadedFile: LoadedFile): FullImage = LoadedFileImage(loadedFile)


  //def apply(path: SvgPathBuilder): FullImage = ???

  def apply(element: AppSvgElement): FullImage = {
    ???
  }

}
