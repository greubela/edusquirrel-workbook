package it.evadid.evacuation.js

import it.evadid.evacuation.core.graphic.model.{EvaColor, EvaFont, EvaImage}
import it.evadid.evacuation.shared.traits.graphic.{EvaCanvas, EvaMouseListener}
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.image.Image
import javafx.scene.input.MouseButton
import javafx.scene.paint.{Color, Paint}
import javafx.scene.shape.ArcType
import javafx.scene.text.Text

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class EvaFxCanvas(width: Double, height: Double) extends EvaCanvas[Canvas] {



  private var scalingFactor: Double = 1.0

  private val (canvas, ctx): (Canvas, GraphicsContext) = {
    val canvas = new Canvas(width, height)
    val ctxL = canvas.getGraphicsContext2D
    ctxL.setFill(Color.WHITE)
    ctxL.fillRect(0, 0, width, height)

    canvas.setOnMouseClicked(event => mouseListener.foreach(mL => mL.onMouseClicked(event.getX, event.getY, event.getButton == MouseButton.PRIMARY)))
    canvas.setOnMouseMoved(event => mouseListener.foreach(mL => mL.onMouseMoved(event.getX, event.getY)))
    canvas.setOnMouseEntered(event => mouseListener.foreach(mL => mL.onMouseEntered(event.getX, event.getY)))
    canvas.setOnMouseExited(event => mouseListener.foreach(mL => mL.onMouseExited(event.getX, event.getY)))

    (canvas, ctxL)
  }


  override def getDrawingWidth: Double = width

  override def getDrawingHeight: Double = height

  override def getCanvasElement: Canvas = canvas


  override def drawRect(x: Double, y: Double, width: Double, height: Double): Unit = ctx.strokeRect(x, y, width, height)

  override def fillRect(x: Double, y: Double, width: Double, height: Double): Unit =
    ctx.fillRect(x, y, width, height)

   def drawArc(x: Double, y: Double, width: Double, height: Double, startAngle: Double, arcAngle: Double): Unit = ctx.strokeArc(x, y, width, height, startAngle, arcAngle, ArcType.OPEN)

   def fillArc(x: Double, y: Double, width: Double, height: Double, startAngle: Double, arcAngle: Double): Unit = ctx.fillArc(x, y, width, height, startAngle, arcAngle, ArcType.OPEN)


  def drawCircle(x: Double, y: Double, diameter: Double): Unit = drawArc(x, y, diameter, diameter, 0, 360)

  def fillCircle(x: Double, y: Double, diameter: Double): Unit = fillArc(x, y, diameter, diameter, 0, 360)

  private val imageMap: mutable.Map[EvaImage, Image] = new mutable.HashMap[EvaImage, Image]()


  private def createOrGetImg(evaImg: EvaImage): Image = null
  /*if (imageMap.contains(evaImg)) {
 imageMap(evaImg)
} else {
 val img = new scalafx.scene.image.Image(evaImg.file)
 imageMap.put(evaImg, img)
 img
}*/

  override def drawImage(x: Double, y: Double, evaImg: EvaImage): Unit = {
  //  val img = createOrGetImg(evaImg)
  //  ctx.drawImage(img, x, y)
  }

  override def drawScaledImage(x: Double, y: Double, width: Double, height: Double, evaImg: EvaImage): Unit = {
/*    val img = createOrGetImg(evaImg)
    val iv = new ImageView(img)
    iv.setFitWidth(width)
    iv.setFitHeight(height)
    ctx.drawImage(iv.getImage, x, y)*/
  }

  override def drawStringCentered(x: Double, y: Double, content: String): Unit = {
    val text = new Text(content)
    val (width, height) = (text.getBoundsInLocal.getWidth, text.getBoundsInLocal.getHeight)
    println("size: " + width + " - " + height)
    ctx.strokeText(content, x - width / 2, y + height / 2) // @ToDo: check position y
  }

  /*
  override def getScalingFactor(): Double = scalingFactor

  override def setScalingFactor(scalingFactor: Double): Unit = this.scalingFactor = {
    // @ Todo
    ctx.scale(scalingFactor, scalingFactor)
    scalingFactor
  }*/


  override def setFillColor(color: EvaColor): EvaColor = {
    fillColor = color
    ctx.setFill(EvaFxCanvas.getPaint(color))
    color
  }



  protected var fillColor: EvaColor = setFillColor(EvaColor(0, 0, 0, 255))
  protected var strokeColor: EvaColor = setStrokeColor(EvaColor(0, 0, 0, 255))
  def getFillColor: EvaColor = fillColor
  def getStrokeColor: EvaColor = strokeColor

  override def setStrokeColor(color: EvaColor): EvaColor = {
    strokeColor = color
    ctx.setStroke(EvaFxCanvas.getPaint(color))
    color
  }

  override def drawLine(x1: Double, y1: Double, x2: Double, y2: Double, width: Double = 1.0): Unit = {
    val oldLineWidth = ctx.getLineWidth
    ctx.setLineWidth(width)
    ctx.strokeLine(x1, y1, x2, y2)
    ctx.setLineWidth(oldLineWidth)
  }

  private val mouseListener = new ListBuffer[EvaMouseListener]()

  override def addMouseListener(mL: EvaMouseListener): Unit = mouseListener += mL

  override def removeMouseListener(mL: EvaMouseListener): Unit = mouseListener -= mL

  override def drawImageWithAlpha(x: Double, y: Double, alphaUpTp255: Double, img: EvaImage): Unit = ???

  /**
   * Fills an Arc on the Canvas
   *
   * @param x          the x-Position of the circle-center
   * @param y          the y-Position of the circle-center
   * @param diameter   the diameter of the circle
   * @param startAngle the start angle (0-360, 0 = top)
   * @param arcAngle   the arc angle (0-360, positive = clockwise)
   */
  override def fillArc(x: Double, y: Double, diameter: Double, startAngle: Double, arcAngle: Double): Unit = ???

  override def drawCircle(x: Double, y: Double, diameter: Double, width: Double): Unit = ???

  override def setFont(font: EvaFont): Unit = ???

  override def getTextWidth(str: String, font: EvaFont): Double = ???
}

object EvaFxCanvas {

  def getPaint(evaColor: EvaColor): Paint = {
    Color.rgb(evaColor.red, evaColor.green, evaColor.blue, evaColor.alpha / 255.0)

  }

}
