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
      val bounds = builder.bounds
      val width: String = math.max(100, builder.bounds.dimension.width).toString
      val height: String = math.max(100, builder.bounds.dimension.height).toString
      svg.svg(
        svg.width := width,
        svg.height := height,
        svg.viewBox := s"${builder.bounds.startPoint.x} ${builder.bounds.startPoint.y} ${width} ${height}",
        svg.rect(
          svg.x := builder.bounds.startPoint.x.toString,
          svg.y := builder.bounds.startPoint.y.toString,
          svg.width := width,
          svg.height := height,
          svg.fill := "#ffff00",
        ),
        svg.circle(
          svg.x := "-1",
          svg.y := "-1",
          svg.r := "1",
          svg.fill := "#aaaaaa"
        ),
        svg.path(
          svg.d := builder.toSvgPathD,
          svg.stroke := "#111111",
        )
        ,
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
