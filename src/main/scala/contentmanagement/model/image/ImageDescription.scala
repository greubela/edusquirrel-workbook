package contentmanagement.model.image

import contentmanagement.model.FileInformation
import org.scalajs.dom.File

sealed trait ImageDescription {
  def getName: String
}

object ImageDescription {

  case class UploadImageDescription(file: File) extends ImageDescription {
    override def toString: String = "UploadImageDescription(" + file.name + ")"

    override def getName: String = file.name
  }

  case class SvgImageDescription(name: String, svgContent: String) extends ImageDescription {
    override def toString: String = "SvgImageDescription(" + name + ".svg, " + svgContent.length + " characters)"

    override def getName: String = name
  }

  case class ServerImageDescription(path: String) extends ImageDescription {
    override def toString: String = "ServerImageDescription(" + path + ")"

    private lazy val parts = FileInformation.nameParts(path)

    override def getName: String = parts(1) + "." + parts(2)
  }

}