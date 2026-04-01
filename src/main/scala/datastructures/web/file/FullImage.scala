package datastructures.web.file

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import util.web.JsHelpers

sealed trait FullImage {
  def fileDescription: FileDescription

  def imageSourceString: String

  def download(): Unit

  def newDomImage: Image = img(
    src := imageSourceString
  )
  
}


object FullImage {

  case class LoadedFileImage(loadedFile: LoadedFile) extends FullImage{

    val fileDescription: FileDescription = loadedFile.description

    def download(): Unit = loadedFile.download()

    override lazy val imageSourceString: String = {
      val b64str = JsHelpers.byteArrayToBase64String(loadedFile.data)
      "data:image/" + loadedFile.description.extension + ";base64, " + b64str
    }
  }


  //def apply(path: SvgPathBuilder): FullImage = ???

  def apply(element: AppSvgElement): FullImage = {
    ???
  }

}
