package it.evadid.homepage.webElements.facades

import it.evadid.homepage.control.startup.BrowserLoading
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.js

object JSXGraph {
  @js.native trait Api extends js.Object { val JSXGraph: BoardFactory = js.native }
  @js.native trait BoardFactory extends js.Object {
    def initBoard(container: dom.Element, config: js.Object): Board = js.native
    def freeBoard(board: Board): Unit = js.native
  }
  @js.native trait Board extends js.Object {
    def create(kind: String, parents: js.Array[js.Any], config: js.Object): js.Dynamic = js.native
    def suspendUpdate(): Unit = js.native
    def unsuspendUpdate(): Unit = js.native
    def update(): Unit = js.native
  }
  def available: Boolean = {
    val library = js.Dynamic.global.globalThis.selectDynamic("JXG")
    !js.isUndefined(library) && library != null
  }
  def preload(): Future[Unit] = if available then Future.unit
    else BrowserLoading.load(BrowserLoading.resource("programs/20261004JSXGraph/jsxgraphcore.js"))
  private def api: Api = js.Dynamic.global.globalThis.selectDynamic("JXG").asInstanceOf[Api]
  def board(container: dom.Element, config: js.Object): ExternalDependency[Board] =
    new ExternalDependency(() => preload(), () => available, () => api.JSXGraph.initBoard(container, config))
  def free(board: Board): Unit = api.JSXGraph.freeBoard(board)
}
