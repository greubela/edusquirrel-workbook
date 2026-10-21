package it.evadid.homepage.control.startup

import com.raquo.airstream.state.Var
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Application-owned readiness: preheat code at startup without constructing editors. */
object SnapRuntimeLoading {
  private val pending = Var(Option.empty[Future[Unit]])
  private val scripts = List(
    "adjusted/adjustedMorphic.js", "turtlestitchsrc/symbols.js", "turtlestitchsrc/widgets.js",
    "turtlestitchsrc/blocks.js", "turtlestitchsrc/threads.js", "adjusted/adjustedObjects.js",
    "turtlestitchsrc/scenes.js", "turtlestitchsrc/gui.js", "turtlestitchsrc/paint.js",
    "turtlestitchsrc/lists.js", "turtlestitchsrc/byob.js", "turtlestitchsrc/tables.js",
    "turtlestitchsrc/sketch.js", "turtlestitchsrc/video.js", "turtlestitchsrc/maps.js",
    "turtlestitchsrc/extensions.js", "turtlestitchsrc/xml.js", "turtlestitchsrc/store.js",
    "turtlestitchsrc/locale.js", "turtlestitchsrc/cloud.js", "turtlestitchsrc/api.js",
    "turtlestitchsrc/embroider.js")

  def load(): Future[Unit] = pending.now().getOrElse {
    SnapRuntimeCompatibility.prepareResources()
    val result = BrowserScriptLoading.ordered(scripts.map(path => BrowserAssets.resource("programs/20260212TurtleStitch/" + path))).map(_ => SnapRuntimeCompatibility.install())
    pending.set(Some(result))
    result.failed.foreach { _ =>
      if pending.now().contains(result) then pending.set(None)
    }
    result
  }
  def preheat(): Unit = { load().failed.foreach(_ => ()); () }
}
