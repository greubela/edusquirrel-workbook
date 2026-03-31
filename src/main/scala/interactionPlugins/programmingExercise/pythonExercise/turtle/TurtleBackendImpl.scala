package interactionPlugins.programmingExercise.pythonExercise.turtle

import contentmanagement.model.color.{AppColor, RGBColor}
import contentmanagement.webElements.genericHtmlElements.canvas.{AppCanvas, WebCanvas}
import util.numbers.AlgebriteNumber

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportAll

@JSExportAll
final class TurtleBackendImpl(canvas: AppCanvas[?]) extends TurtleBackend {

  private case class TurtleState(
      id: Int,
      x: AlgebriteNumber,
      y: AlgebriteNumber,
      headingDeg: AlgebriteNumber,
      penDown: Boolean,
      penSize: AlgebriteNumber,
      penColor: String,
      fillColor: String,
      visible: Boolean
  )

  private val turtles = mutable.Map.empty[Int, TurtleState]
  private var nextId = 1
  private var titleText = "Turtle"
  private var bgColor = "#ffffff"
  private val defaultIdInternal = createTurtle()

  override def defaultTurtleId: Int = defaultIdInternal

  override def prepareForRun(): Unit = {
    screenClearScreen()
    createIfMissing(defaultIdInternal)
  }

  private def createIfMissing(id: Int): TurtleState = {
    turtles.getOrElseUpdate(
      id,
      TurtleState(
        id = id,
        x = AlgebriteNumber("0"),
        y = AlgebriteNumber("0"),
        headingDeg = AlgebriteNumber("0"),
        penDown = true,
        penSize = AlgebriteNumber("1"),
        penColor = "#000000",
        fillColor = "#000000",
        visible = true
      )
    )
  }

  private def withTurtle(id: Int)(f: TurtleState => TurtleState): Unit =
    turtles.update(id, f(createIfMissing(id)))

  private def toCanvasX(x: AlgebriteNumber): Double = canvas.getWidth / 2.0 + x.toDouble
  private def toCanvasY(y: AlgebriteNumber): Double = canvas.getHeight / 2.0 - y.toDouble

  private def maybeColor(str: String): Option[AppColor] = {
    val norm = str.trim.toLowerCase
    val mapped = norm match {
      case "black" => "#000000"
      case "white" => "#ffffff"
      case "red" => "#ff0000"
      case "green" => "#008000"
      case "blue" => "#0000ff"
      case "yellow" => "#ffff00"
      case other => other
    }
    if mapped.matches("#[0-9a-f]{6}") then Some(AppColor.fromWebStyleString(mapped)) else None
  }

  private def drawMove(oldState: TurtleState, newX: AlgebriteNumber, newY: AlgebriteNumber): Unit = {
    if oldState.penDown then {
      maybeColor(oldState.penColor).foreach(canvas.setStrokeColor)
      canvas.drawLine(
        toCanvasX(oldState.x),
        toCanvasY(oldState.y),
        toCanvasX(newX),
        toCanvasY(newY),
        oldState.penSize.toDouble
      )
    }
  }

  override def createTurtle(): Int = {
    val id = nextId
    nextId += 1
    createIfMissing(id)
    id
  }

  override def cloneTurtleObject(id: Int): Int = {
    val newId = createTurtle()
    turtles.update(newId, createIfMissing(id).copy(id = newId))
    newId
  }

  override def turtleForward(id: Int, distance: Double): Unit = {
    val d = AlgebriteNumber(distance.toString)
    val old = createIfMissing(id)
    val rad = old.headingDeg.toDouble * Math.PI / 180.0
    val newX = old.x + AlgebriteNumber((Math.cos(rad) * d.toDouble).toString)
    val newY = old.y + AlgebriteNumber((Math.sin(rad) * d.toDouble).toString)
    drawMove(old, newX, newY)
    withTurtle(id)(_.copy(x = newX, y = newY))
  }

  override def turtleBackward(id: Int, distance: Double): Unit = turtleForward(id, -distance)
  override def turtleRight(id: Int, angle: Double): Unit = withTurtle(id)(s => s.copy(headingDeg = s.headingDeg - AlgebriteNumber(angle.toString)))
  override def turtleLeft(id: Int, angle: Double): Unit = withTurtle(id)(s => s.copy(headingDeg = s.headingDeg + AlgebriteNumber(angle.toString)))

  override def turtleGoTo(id: Int, x: Double, y: Double): Unit = {
    val old = createIfMissing(id)
    val newX = AlgebriteNumber(x.toString)
    val newY = AlgebriteNumber(y.toString)
    drawMove(old, newX, newY)
    withTurtle(id)(_.copy(x = newX, y = newY))
  }

  override def turtleTeleport(id: Int, x: Double, y: Double, fillGap: Boolean): Unit = withTurtle(id)(_.copy(x = AlgebriteNumber(x.toString), y = AlgebriteNumber(y.toString)))
  override def turtleSetX(id: Int, x: Double): Unit = turtleGoTo(id, x, turtleYCor(id))
  override def turtleSetY(id: Int, y: Double): Unit = turtleGoTo(id, turtleXCor(id), y)
  override def turtleSetHeading(id: Int, angle: Double): Unit = withTurtle(id)(_.copy(headingDeg = AlgebriteNumber(angle.toString)))
  override def turtleHome(id: Int): Unit = { turtleGoTo(id, 0, 0); turtleSetHeading(id, 0) }

  override def turtleCircle(id: Int, radius: Double, extent: js.UndefOr[Double], steps: js.UndefOr[Int]): Unit = {
    maybeColor(turtlePenColorGet(id)).foreach(canvas.setStrokeColor)
    val d = Math.abs(radius) * 2
    canvas.drawCircle(toCanvasX(createIfMissing(id).x), toCanvasY(createIfMissing(id).y), d, turtlePenSizeGet(id))
  }

  override def turtleDot(id: Int, size: js.UndefOr[Double], color: js.UndefOr[String]): Unit = {
    val s = size.getOrElse(6.0)
    color.toOption.flatMap(maybeColor).orElse(maybeColor(turtlePenColorGet(id))).foreach(canvas.setFillColor)
    val t = createIfMissing(id)
    canvas.fillCircle(toCanvasX(t.x), toCanvasY(t.y), s)
  }

  override def turtleStamp(id: Int): Int = 0
  override def turtleClearStamp(id: Int, stampId: Int): Unit = ()
  override def turtleClearStamps(id: Int, count: js.UndefOr[Int]): Unit = ()
  override def turtleUndo(id: Int): Unit = ()
  override def turtleSpeedSet(id: Int, speed: Double): Unit = ()
  override def turtleSpeedGet(id: Int): Double = 0
  override def turtlePosition(id: Int): js.Array[Double] = js.Array(turtleXCor(id), turtleYCor(id))
  override def turtleTowards(id: Int, x: Double, y: Double): Double = Math.toDegrees(Math.atan2(y - turtleYCor(id), x - turtleXCor(id)))
  override def turtleXCor(id: Int): Double = createIfMissing(id).x.toDouble
  override def turtleYCor(id: Int): Double = createIfMissing(id).y.toDouble
  override def turtleHeading(id: Int): Double = createIfMissing(id).headingDeg.toDouble
  override def turtleDistance(id: Int, x: Double, y: Double): Double = Math.hypot(x - turtleXCor(id), y - turtleYCor(id))
  override def turtleDegrees(id: Int, fullCircle: js.UndefOr[Double]): Unit = ()
  override def turtleRadians(id: Int): Unit = ()
  override def turtlePenDown(id: Int): Unit = withTurtle(id)(_.copy(penDown = true))
  override def turtlePenUp(id: Int): Unit = withTurtle(id)(_.copy(penDown = false))
  override def turtlePenSizeSet(id: Int, width: Double): Unit = withTurtle(id)(_.copy(penSize = AlgebriteNumber(width.toString)))
  override def turtlePenSizeGet(id: Int): Double = createIfMissing(id).penSize.toDouble
  override def turtlePenState(id: Int): js.Object = js.Dynamic.literal(down = turtleIsDown(id), pensize = turtlePenSizeGet(id), pencolor = turtlePenColorGet(id), fillcolor = turtleFillColorGet(id)).asInstanceOf[js.Object]
  override def turtlePenStateApplyJson(id: Int, json: String): Unit = ()
  override def turtleIsDown(id: Int): Boolean = createIfMissing(id).penDown
  override def turtleColorGet(id: Int): js.Array[String] = js.Array(turtlePenColorGet(id), turtleFillColorGet(id))
  override def turtleColorSet(id: Int, penColor: String, fillColor: String): Unit = withTurtle(id)(_.copy(penColor = penColor, fillColor = fillColor))
  override def turtlePenColorGet(id: Int): String = createIfMissing(id).penColor
  override def turtlePenColorSet(id: Int, color: String): Unit = withTurtle(id)(_.copy(penColor = color))
  override def turtleFillColorGet(id: Int): String = createIfMissing(id).fillColor
  override def turtleFillColorSet(id: Int, color: String): Unit = withTurtle(id)(_.copy(fillColor = color))
  override def turtleFilling(id: Int): Boolean = false
  override def turtleBeginFill(id: Int): Unit = ()
  override def turtleEndFill(id: Int): Unit = ()
  override def turtleReset(id: Int): Unit = { turtles.remove(id); createIfMissing(id) }
  override def turtleClear(id: Int): Unit = canvas.clear(maybeColor(bgColor).getOrElse(RGBColor.white))
  override def turtleWrite(id: Int, text: String, move: Boolean, align: String, fontCss: String): Unit = ()
  override def turtleShowTurtle(id: Int): Unit = withTurtle(id)(_.copy(visible = true))
  override def turtleHideTurtle(id: Int): Unit = withTurtle(id)(_.copy(visible = false))
  override def turtleIsVisible(id: Int): Boolean = createIfMissing(id).visible
  override def turtleShapeGet(id: Int): String = "classic"
  override def turtleShapeSet(id: Int, name: String): Unit = ()
  override def turtleResizeModeGet(id: Int): String = "noresize"
  override def turtleResizeModeSet(id: Int, mode: String): Unit = ()
  override def turtleShapeSizeSet(id: Int, stretchWid: Double, stretchLen: Double, outline: Double): Unit = ()
  override def turtleShapeSizeGet(id: Int): js.Array[Double] = js.Array(1, 1, 1)
  override def turtleShearFactorSet(id: Int, shear: Double): Unit = ()
  override def turtleShearFactorGet(id: Int): Double = 0
  override def turtleTiltAngleSet(id: Int, angle: Double): Unit = ()
  override def turtleTiltAngleGet(id: Int): Double = 0
  override def turtleTilt(id: Int, angle: Double): Unit = ()
  override def turtleShapeTransformSet(id: Int, t11: Double, t12: Double, t21: Double, t22: Double): Unit = ()
  override def turtleShapeTransformGet(id: Int): js.Array[Double] = js.Array(1, 0, 0, 1)
  override def turtleGetShapePoly(id: Int): js.Array[js.Array[Double]] = js.Array()
  override def turtleOnClick(id: Int, callback: js.Any, button: js.UndefOr[Int], add: js.UndefOr[Boolean]): Unit = ()
  override def turtleOnRelease(id: Int, callback: js.Any, button: js.UndefOr[Int], add: js.UndefOr[Boolean]): Unit = ()
  override def turtleOnDrag(id: Int, callback: js.Any, button: js.UndefOr[Int], add: js.UndefOr[Boolean]): Unit = ()
  override def turtlePoly(id: Int): js.Array[js.Array[Double]] = js.Array()
  override def turtleBeginPoly(id: Int): Unit = ()
  override def turtleEndPoly(id: Int): Unit = ()
  override def turtleGetPoly(id: Int): js.Array[js.Array[Double]] = js.Array()
  override def turtleGetTurtle(id: Int): Int = id
  override def turtleGetScreen(id: Int): Int = 0
  override def turtleSetUndoBuffer(id: Int, size: js.UndefOr[Int]): Unit = ()
  override def turtleUndoBufferEntries(id: Int): Int = 0
  override def screenBgColorGet(): String = bgColor
  override def screenBgColorSet(color: String): Unit = { bgColor = color; canvas.clear(maybeColor(color).getOrElse(RGBColor.white)) }
  override def screenBgPicGet(): String = ""
  override def screenBgPicSet(name: String): Unit = ()
  override def screenClearScreen(): Unit = { turtles.clear(); canvas.clear(maybeColor(bgColor).getOrElse(RGBColor.white)) }
  override def screenResetScreen(): Unit = { screenClearScreen(); createIfMissing(defaultIdInternal) }
  override def screenSizeGet(): js.Array[Int] = js.Array(canvas.getWidth.toInt, canvas.getHeight.toInt)
  override def screenSizeSet(width: js.UndefOr[Int], height: js.UndefOr[Int], bg: js.UndefOr[String]): js.Array[Int] = { bg.foreach(screenBgColorSet); screenSizeGet() }
  override def screenSetWorldCoordinates(llx: Double, lly: Double, urx: Double, ury: Double): Unit = ()
  override def screenDelayGet(): Int = 0
  override def screenDelaySet(delay: Int): Unit = ()
  override def screenTracerGet(): Int = 1
  override def screenTracerSet(n: js.UndefOr[Int], delay: js.UndefOr[Int]): Int = n.getOrElse(1)
  override def screenUpdate(): Unit = ()
  override def screenListen(): Unit = ()
  override def screenOnKey(callback: js.Any, key: String, press: Boolean): Unit = ()
  override def screenOnClick(callback: js.Any, button: js.UndefOr[Int], add: js.UndefOr[Boolean]): Unit = ()
  override def screenOnTimer(callback: js.Any, millis: js.UndefOr[Int]): Unit = ()
  override def screenMainLoop(): Unit = ()
  override def screenModeGet(): String = "standard"
  override def screenModeSet(mode: String): String = mode
  override def screenColorModeGet(): Double = 255
  override def screenColorModeSet(mode: Double): Unit = ()
  override def screenGetCanvas(): js.Object = canvas match {
    case webCanvas: WebCanvas => webCanvas.getCanvas.ref.asInstanceOf[js.Object]
    case _ => js.Dynamic.literal().asInstanceOf[js.Object]
  }
  override def screenGetShapes(): js.Array[String] = js.Array("classic", "arrow", "turtle", "circle", "square", "triangle")
  override def screenRegisterShape(name: String, kind: String, payload: js.Any): Unit = ()
  override def screenTurtles(): js.Array[Int] = js.Array(turtles.keys.toSeq*)
  override def screenWindowHeight(): Int = canvas.getHeight.toInt
  override def screenWindowWidth(): Int = canvas.getWidth.toInt
  override def screenTextInput(title: String, prompt: String): js.UndefOr[String] = js.undefined
  override def screenNumInput(title: String, prompt: String, defaultValue: js.UndefOr[Double], minval: js.UndefOr[Double], maxval: js.UndefOr[Double]): js.UndefOr[Double] = js.undefined
  override def screenBye(): Unit = screenClearScreen()
  override def screenExitOnClick(): Unit = ()
  override def screenSave(filename: String, overwrite: Boolean): String = filename
  override def screenSetup(width: js.UndefOr[Double], height: js.UndefOr[Double], startx: js.UndefOr[Double], starty: js.UndefOr[Double]): Unit = ()
  override def screenTitleGet(): String = titleText
  override def screenTitleSet(title: String): Unit = titleText = title
}
