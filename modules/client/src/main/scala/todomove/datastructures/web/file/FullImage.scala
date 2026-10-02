package todomove.datastructures.web.file

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.canvas.AppImage
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.util.JsHelpers
import todomove.webElementsOld.webElements.svg.AppSvgElement

sealed trait FullImage extends AppImage {
  //def download(): Unit

  def newDomImage: Element

}


object FullImage {

  case class SvgBasedImage(builder: SvgPathBuilder[Double]) extends FullImage {

    override def newDomImage: L.Element = {
      getSvg
    }

    def getSvg: Element = {
      val pad = 8.0
      val b = builder.bounds
      val x = b.startPoint.x.toDouble - pad
      val y = b.startPoint.y.toDouble - pad
      val width = math.max(50.0, b.dimension.width.toDouble + 2 * pad)
      val height = math.max(50.0, b.dimension.height.toDouble + 2 * pad)
      svg.svg(
        svg.width := width.toString,
        svg.height := height.toString,
        svg.viewBox := s"$x $y $width $height",
        svg.path(
          svg.d := builder.toSvgPathD,
          svg.fill := "none",
          svg.stroke := "#111111",
          svg.strokeWidth := "1"
        )
      )
    }

    override def imageSourceString: String = {
      getSvg.ref.toString
    }
  }

  case class DataSourceImage(dataSource: String, fileFormat: String) extends FullImage {

    assert(dataSource.startsWith("data:image/"), "dataSource must start with 'data:image/'")

    override def imageSourceString: String = dataSource

    override def newDomImage: Element = img(
      src := imageSourceString
    )
    //override def download(): Unit = HomepageFileStore.downloadFile(s"unknown.$fileFormat", dataSource)
  }

  case class LoadedFileImage(loadedFile: LoadedFile) extends FullImage {

    //def download(): Unit = HomepageFileStore.downloadFile(loadedFile.description.filenameWithExtension, loadedFile.data)

    override lazy val imageSourceString: String = {
      val b64str = JsHelpers.byteArrayToBase64String(loadedFile.data)
      "data:image/" + loadedFile.description.structure.extensionOrEmpty + ";base64, " + b64str
    }

    override def newDomImage: Element = img(
      src := imageSourceString
    )
  }

  def apply(loadedFile: LoadedFile): FullImage = LoadedFileImage(loadedFile)


  def apply(path: SvgPathBuilder[Double]): FullImage = {
    SvgBasedImage(path)
  }

  def apply(element: AppSvgElement): FullImage = {
    ???
  }

}
