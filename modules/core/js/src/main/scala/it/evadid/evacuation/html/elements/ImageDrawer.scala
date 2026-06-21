package it.evadid.evacuation.html.elements

import it.evadid.evacuation.core.graphic.model.EvaImage
import it.evadid.evacuation.core.graphic.model.EvaImage.{DataBasedEvaImage, PathBasedEvaImage}
import it.evadid.evacuation.core.io.instances.string.Base64IO
import org.scalajs.dom
import org.scalajs.dom.html.Image
import org.scalajs.dom.{Element, document}

import scala.collection.mutable

object ImageDrawer {


  private case class DrawCommand(canvas: EvaWebCanvas, imageData: LoadableWebImage, x: Double, y: Double, width: Double, height: Double, alpha: Double) {
    def execute(): Unit = if (imageData.isReady) {
      canvas.drawLoadedImage(imageData.getImageElement, x, y, width, height, alpha)
    } else {
      imageData.addToQueue(this)
    }
  }

  private class LoadableWebImage(webImageElement: Image) {
    var isReady: Boolean = false
    private val drawingQueue = new mutable.ListBuffer[DrawCommand]

    def getImageElement: Image = webImageElement

    webImageElement.onload = (e: dom.Event) => {
      isReady = true
      drawingQueue.foreach(_.execute())
    }

    def addToQueue(drawCommand: DrawCommand): Unit = drawingQueue += drawCommand
  }

  private val cachedImages: mutable.Map[EvaImage, LoadableWebImage] = new mutable.HashMap[EvaImage, LoadableWebImage]

  private def createLoadableImage(evaImage: EvaImage): LoadableWebImage = evaImage match {
    case PathBasedEvaImage(fullFilePath) => {
      val element: Image = dom.document.createElement("img").asInstanceOf[Image]
      element.src = fullFilePath
      new LoadableWebImage(element)
    }
    case DataBasedEvaImage(fullFileName, fileType, fileData) => {
      val b64str = Base64IO.encode(fileData)
      val imgSrc = "data:image/" + fileType + ";base64, " + b64str

      val img: Element = document.createElement("img")
      img.setAttribute("src", imgSrc)

      new LoadableWebImage(img.asInstanceOf[Image])
    }
  }

  def drawImage(canvas: EvaWebCanvas, image: EvaImage, x: Double, y: Double, width: Double, height: Double, alpha: Double): Unit = {
    //println("drawImage(" + canvas.getClass + ", " + image + ", " + x + ", " + y + ", " + width + ", " + height + ", " + alpha + ")")
    val webImage: LoadableWebImage = {
      if (cachedImages.contains(image)) {
        cachedImages(image)
      }
      else {
        val res = createLoadableImage(image)
        cachedImages.put(image, res)
        res
      }
    }
    DrawCommand(canvas, webImage, x, y, width, height, alpha).execute()
  }


}

