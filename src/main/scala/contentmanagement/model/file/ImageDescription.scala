package contentmanagement.model.file

import org.scalajs.dom.File
import upickle.core.TraceVisitor.RootHasPath.path
/*
sealed trait ImageDescription {
  def nameWithType: String
}

object ImageDescription {

  case class UploadImageDescription(file: File) extends ImageDescription {
    override def toString: String = "UploadImageDescription(" + file.name + ")"

    override def nameWithType: String = file.name
  }

  case class SvgImageDescription(name: String, svgContent: String) extends ImageDescription {
    override def toString: String = "SvgImageDescription(" + name + ".svg, " + svgContent.length + " characters)"

    override def nameWithType: String = name
  }

  case class DataBasedImageDescription(name: String, imageType: String, imageData: String) extends ImageDescription {
    override def toString: String = "DataBasedImageDescription(" + name + "." + imageType + ", " + imageData.length + " characters)"
    
    override def nameWithType: String = name 
  }
  case class UrlImageDescription(url: URL) extends ImageDescription {
    override def toString: String = "ServerImageDescription(" + path + ")"

    private lazy val parts = LoadedFile.nameParts(path)

    override def nameWithType: String = parts(1) + "." + parts(2)
  }

  
  
}*/