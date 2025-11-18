package util.JSXGraph

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("JXG.JSXGraph")
private[JSXGraph] object JSXGraphNative extends js.Object:
  def initBoard(containerId: String, options: js.Dictionary[Any]): JSXBoardNative = js.native
  def freeBoard(board: JSXBoardNative): Unit = js.native

@js.native
@JSGlobal("JXG")
private[JSXGraph] object JXGNative extends js.Object:
  val COORDS_BY_USER: Int = js.native

@js.native
private[JSXGraph] trait JSXBoardNative extends js.Object:
  val id: String = js.native
  def create(elementType: String, parents: js.Array[Any], attributes: js.UndefOr[js.Dictionary[Any]] = js.undefined): js.Any = js.native
  def update(): Unit = js.native
  def fullUpdate(): Unit = js.native

@js.native
private[JSXGraph] trait JSXGeometryElementNative extends js.Object:
  val id: String = js.native
  val name: js.UndefOr[String] = js.native

@js.native
private[JSXGraph] trait JSXPointNative extends JSXGeometryElementNative:
  def X(): Double = js.native
  def Y(): Double = js.native
  def setPosition(method: Int, coords: js.Array[Double], doRound: js.UndefOr[Boolean] = js.undefined): Unit = js.native

@js.native
private[JSXGraph] trait JSXLineNative extends JSXGeometryElementNative

@js.native
private[JSXGraph] trait JSXCircleNative extends JSXGeometryElementNative

@js.native
private[JSXGraph] trait JSXTextNative extends JSXGeometryElementNative:
  var text: String = js.native

@js.native
private[JSXGraph] trait JSXCurveNative extends JSXGeometryElementNative
