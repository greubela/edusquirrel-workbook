package interactionPlugins.programmingExercise.pythonExercise.turtle

import scala.scalajs.js

/** Browser-side backend for the Python `turtle` compatibility layer.
 *
 * The backend owns all turtle and screen state. The Python shim is intended to be a thin facade that normalizes
 * Python arguments and forwards every documented operation to this trait.
 */
trait TurtleBackend {

  /** Internal helper: return the id of the implicit module-level turtle. */
  def defaultTurtleId: Int

  /** Internal helper: reset the backend to a clean state before executing a new Python snippet. */
  def prepareForRun(): Unit

  /** Create a new turtle and return its backend id. */
  def createTurtle(): Int

  /** Return a clone of the given turtle as a new backend id. */
  def cloneTurtleObject(id: Int): Int

  // Turtle motion ---------------------------------------------------------------------------------

  /** Move the turtle forward by the specified distance, in the direction the turtle is headed. */
  def turtleForward(id: Int, distance: Double): Unit

  /** Move the turtle backward by distance, opposite to the direction the turtle is headed. */
  def turtleBackward(id: Int, distance: Double): Unit

  /** Turn turtle right by angle units. */
  def turtleRight(id: Int, angle: Double): Unit

  /** Turn turtle left by angle units. */
  def turtleLeft(id: Int, angle: Double): Unit

  /** Move turtle to an absolute position. If the pen is down, draw a line. */
  def turtleGoTo(id: Int, x: Double, y: Double): Unit

  /** Move the turtle to an absolute position instantly. */
  def turtleTeleport(id: Int, x: Double, y: Double, fillGap: Boolean): Unit

  /** Set the turtle's first coordinate to x, leave the second coordinate unchanged. */
  def turtleSetX(id: Int, x: Double): Unit

  /** Set the turtle's second coordinate to y, leave the first coordinate unchanged. */
  def turtleSetY(id: Int, y: Double): Unit

  /** Set the orientation of the turtle to angle. */
  def turtleSetHeading(id: Int, angle: Double): Unit

  /** Move turtle to the origin and set its heading to its start-orientation. */
  def turtleHome(id: Int): Unit

  /** Draw a circle with given radius. If extent is given, draw only that portion of the circle. */
  def turtleCircle(id: Int, radius: Double, extent: js.UndefOr[Double], steps: js.UndefOr[Int]): Unit

  /** Draw a circular dot with diameter size, using color if supplied. */
  def turtleDot(id: Int, size: js.UndefOr[Double], color: js.UndefOr[String]): Unit

  /** Stamp a copy of the turtle shape onto the canvas and return its stamp id. */
  def turtleStamp(id: Int): Int

  /** Delete the stamp with the given stamp id. */
  def turtleClearStamp(id: Int, stampId: Int): Unit

  /** Delete all or some of the turtle's stamps. */
  def turtleClearStamps(id: Int, count: js.UndefOr[Int]): Unit

  /** Undo the turtle's last action if possible. */
  def turtleUndo(id: Int): Unit

  /** Set the turtle's animation speed to an integer in the range 0..10. */
  def turtleSpeedSet(id: Int, speed: Double): Unit

  /** Return the current speed setting as an integer-like number. */
  def turtleSpeedGet(id: Int): Double

  // Tell Turtle's state ---------------------------------------------------------------------------

  /** Return the turtle's current position as an `(x, y)` pair. */
  def turtlePosition(id: Int): js.Array[Double]

  /** Return the angle between the turtle's position and the given point. */
  def turtleTowards(id: Int, x: Double, y: Double): Double

  /** Return the turtle's x coordinate. */
  def turtleXCor(id: Int): Double

  /** Return the turtle's y coordinate. */
  def turtleYCor(id: Int): Double

  /** Return the turtle's current heading. */
  def turtleHeading(id: Int): Double

  /** Return the distance from the turtle to the given target. */
  def turtleDistance(id: Int, x: Double, y: Double): Double

  // Settings for measurement ----------------------------------------------------------------------

  /** Set angle measurement units to degrees, or to a full-circle value if supplied. */
  def turtleDegrees(id: Int, fullCircle: js.UndefOr[Double]): Unit

  /** Set angle measurement units to radians. */
  def turtleRadians(id: Int): Unit

  // Pen control -----------------------------------------------------------------------------------

  /** Pull the pen down. Drawing when moving. */
  def turtlePenDown(id: Int): Unit

  /** Pull the pen up. No drawing when moving. */
  def turtlePenUp(id: Int): Unit

  /** Set the line thickness to width if supplied, otherwise return the current pensize. */
  def turtlePenSizeSet(id: Int, width: Double): Unit

  /** Return the current pensize. */
  def turtlePenSizeGet(id: Int): Double

  /** Return the pen dictionary describing the turtle's current pen attributes. */
  def turtlePenState(id: Int): js.Object

  /** Apply pen attributes encoded as a JSON object string. */
  def turtlePenStateApplyJson(id: Int, json: String): Unit

  /** Return `true` if the pen is down, `false` if it is up. */
  def turtleIsDown(id: Int): Boolean

  // Color control ---------------------------------------------------------------------------------

  /** Return the pencolor and fillcolor as a pair. */
  def turtleColorGet(id: Int): js.Array[String]

  /** Set both pencolor and fillcolor. */
  def turtleColorSet(id: Int, penColor: String, fillColor: String): Unit

  /** Return the current pencolor. */
  def turtlePenColorGet(id: Int): String

  /** Set the pencolor. */
  def turtlePenColorSet(id: Int, color: String): Unit

  /** Return the current fillcolor. */
  def turtleFillColorGet(id: Int): String

  /** Set the fillcolor. */
  def turtleFillColorSet(id: Int, color: String): Unit

  // Filling ---------------------------------------------------------------------------------------

  /** Return `true` if filling is in progress, `false` otherwise. */
  def turtleFilling(id: Int): Boolean

  /** Begin a filled shape. */
  def turtleBeginFill(id: Int): Unit

  /** Fill the shape drawn after the last call to `begin_fill()`. */
  def turtleEndFill(id: Int): Unit

  // More drawing control --------------------------------------------------------------------------

  /** Delete the turtle's drawings and restore its defaults. */
  def turtleReset(id: Int): Unit

  /** Delete the turtle's drawings from the screen. Do not move the turtle. */
  def turtleClear(id: Int): Unit

  /** Write text at the current turtle position. */
  def turtleWrite(id: Int, text: String, move: Boolean, align: String, fontCss: String): Unit

  // Turtle state ----------------------------------------------------------------------------------

  /** Make the turtle visible. */
  def turtleShowTurtle(id: Int): Unit

  /** Make the turtle invisible. */
  def turtleHideTurtle(id: Int): Unit

  /** Return `true` if the turtle is shown, `false` if it is hidden. */
  def turtleIsVisible(id: Int): Boolean

  /** Return the current shape name. */
  def turtleShapeGet(id: Int): String

  /** Set the turtle's shape by name. */
  def turtleShapeSet(id: Int, name: String): Unit

  /** Return the current resizemode. */
  def turtleResizeModeGet(id: Int): String

  /** Set the turtle's resizemode. */
  def turtleResizeModeSet(id: Int, mode: String): Unit

  /** Set stretch_wid, stretch_len and outline if supplied; otherwise return the current values. */
  def turtleShapeSizeSet(id: Int, stretchWid: Double, stretchLen: Double, outline: Double): Unit

  /** Return `(stretch_wid, stretch_len, outline)`. */
  def turtleShapeSizeGet(id: Int): js.Array[Double]

  /** Set or return the current shear factor. */
  def turtleShearFactorSet(id: Int, shear: Double): Unit

  /** Return the current shear factor. */
  def turtleShearFactorGet(id: Int): Double

  /** Set or return the current tilt angle. */
  def turtleTiltAngleSet(id: Int, angle: Double): Unit

  /** Return the current tilt angle. */
  def turtleTiltAngleGet(id: Int): Double

  /** Rotate the turtle shape by angle. */
  def turtleTilt(id: Int, angle: Double): Unit

  /** Set the current shape transform matrix. */
  def turtleShapeTransformSet(id: Int, t11: Double, t12: Double, t21: Double, t22: Double): Unit

  /** Return the current shape transform as `(t11, t12, t21, t22)`. */
  def turtleShapeTransformGet(id: Int): js.Array[Double]

  /** Return the current shape polygon. */
  def turtleGetShapePoly(id: Int): js.Array[js.Array[Double]]

  // Using events ----------------------------------------------------------------------------------

  /** Bind fun to mouse-click events on this turtle. */
  def turtleOnClick(id: Int, callback: js.Any, button: js.UndefOr[Int], add: js.UndefOr[Boolean]): Unit

  /** Bind fun to mouse-button-release events on this turtle. */
  def turtleOnRelease(id: Int, callback: js.Any, button: js.UndefOr[Int], add: js.UndefOr[Boolean]): Unit

  /** Bind fun to mouse-move events on this turtle. */
  def turtleOnDrag(id: Int, callback: js.Any, button: js.UndefOr[Int], add: js.UndefOr[Boolean]): Unit

  // Special Turtle methods ------------------------------------------------------------------------

  /** Return the current polygon recorded by `begin_poly()` / `end_poly()`. */
  def turtlePoly(id: Int): js.Array[js.Array[Double]]

  /** Start recording the vertices of a polygon. */
  def turtleBeginPoly(id: Int): Unit

  /** Stop recording the vertices of a polygon. */
  def turtleEndPoly(id: Int): Unit

  /** Return the last recorded polygon. */
  def turtleGetPoly(id: Int): js.Array[js.Array[Double]]

  /** Return the turtle itself. */
  def turtleGetTurtle(id: Int): Int

  /** Return the associated screen object. */
  def turtleGetScreen(id: Int): Int

  /** Set or disable the undo buffer. */
  def turtleSetUndoBuffer(id: Int, size: js.UndefOr[Int]): Unit

  /** Return the number of entries in the undo buffer. */
  def turtleUndoBufferEntries(id: Int): Int

  // Screen methods --------------------------------------------------------------------------------

  /** Return the current background color. */
  def screenBgColorGet(): String

  /** Set the background color. */
  def screenBgColorSet(color: String): Unit

  /** Return the current background image name. */
  def screenBgPicGet(): String

  /** Set the background image name or clear it with `nopic`. */
  def screenBgPicSet(name: String): Unit

  /** Delete all drawings and all turtles from the screen and reset it to its initial state. */
  def screenClearScreen(): Unit

  /** Reset all turtles on the screen to their initial state. */
  def screenResetScreen(): Unit

  /** Return the current canvas size as `(width, height)`. */
  def screenSizeGet(): js.Array[Int]

  /** Resize the canvas and optionally change the background color. */
  def screenSizeSet(width: js.UndefOr[Int], height: js.UndefOr[Int], bg: js.UndefOr[String]): js.Array[Int]

  /** Set up a user-defined coordinate system. */
  def screenSetWorldCoordinates(llx: Double, lly: Double, urx: Double, ury: Double): Unit

  /** Return the current drawing delay in milliseconds. */
  def screenDelayGet(): Int

  /** Set the drawing delay in milliseconds. */
  def screenDelaySet(delay: Int): Unit

  /** Return the current tracer setting. */
  def screenTracerGet(): Int

  /** Set tracing on or off and optionally the update delay. Return the current tracer value. */
  def screenTracerSet(n: js.UndefOr[Int], delay: js.UndefOr[Int]): Int

  /** Perform a screen update. */
  def screenUpdate(): Unit

  /** Set focus to the canvas so keyboard events can be received. */
  def screenListen(): Unit

  /** Bind a key event callback. */
  def screenOnKey(callback: js.Any, key: String, press: Boolean): Unit

  /** Bind a mouse click callback for the screen. */
  def screenOnClick(callback: js.Any, button: js.UndefOr[Int], add: js.UndefOr[Boolean]): Unit

  /** Install a callback that is called after `millis` milliseconds. */
  def screenOnTimer(callback: js.Any, millis: js.UndefOr[Int]): Unit

  /** Enter the main loop. In the browser backend this is a no-op. */
  def screenMainLoop(): Unit

  /** Return the current screen mode. */
  def screenModeGet(): String

  /** Set the screen mode and return the effective mode. */
  def screenModeSet(mode: String): String

  /** Return the current color mode. */
  def screenColorModeGet(): Double

  /** Set the color mode. */
  def screenColorModeSet(mode: Double): Unit

  /** Return a backend-specific representation of the drawing canvas. */
  def screenGetCanvas(): js.Object

  /** Return the list of registered shape names. */
  def screenGetShapes(): js.Array[String]

  /** Register a shape. The kind is implementation-defined, the payload is forwarded from Python. */
  def screenRegisterShape(name: String, kind: String, payload: js.Any): Unit

  /** Return the ids of all turtles on this screen. */
  def screenTurtles(): js.Array[Int]

  /** Return the current window height. */
  def screenWindowHeight(): Int

  /** Return the current window width. */
  def screenWindowWidth(): Int

  /** Show a text input dialog and return the resulting string, if any. */
  def screenTextInput(title: String, prompt: String): js.UndefOr[String]

  /** Show a numeric input dialog and return the resulting number, if any. */
  def screenNumInput(
                      title: String,
                      prompt: String,
                      defaultValue: js.UndefOr[Double],
                      minval: js.UndefOr[Double],
                      maxval: js.UndefOr[Double]
                    ): js.UndefOr[Double]

  /** Close the screen. In the browser backend this clears the canvas and removes listeners where practical. */
  def screenBye(): Unit

  /** Bind `bye()` to a mouse click on the screen. */
  def screenExitOnClick(): Unit

  /** Save the current drawing to a browser download and return the requested filename. */
  def screenSave(filename: String, overwrite: Boolean): String

  /** Set the size and nominal position of the browser drawing surface. */
  def screenSetup(
                   width: js.UndefOr[Double],
                   height: js.UndefOr[Double],
                   startx: js.UndefOr[Double],
                   starty: js.UndefOr[Double]
                 ): Unit

  /** Return the current title string. */
  def screenTitleGet(): String

  /** Set the title of the drawing surface. */
  def screenTitleSet(title: String): Unit
}
