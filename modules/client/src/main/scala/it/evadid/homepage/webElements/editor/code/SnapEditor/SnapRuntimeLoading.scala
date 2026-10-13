package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.homepage.control.startup.{BrowserDependencyLoading, StartupTasks}
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Snap needs ordered classic scripts, but neither its code nor its UI blocks startup. */
object SnapRuntimeLoading {
  private var pending: Option[Future[Unit]] = None
  private[SnapEditor] val scripts = List(
    "adjusted/adjustedMorphic.js", "turtlestitchsrc/symbols.js", "turtlestitchsrc/widgets.js",
    "turtlestitchsrc/blocks.js", "turtlestitchsrc/threads.js", "adjusted/adjustedObjects.js",
    "turtlestitchsrc/scenes.js", "turtlestitchsrc/gui.js", "turtlestitchsrc/paint.js",
    "turtlestitchsrc/lists.js", "turtlestitchsrc/byob.js", "turtlestitchsrc/tables.js",
    "turtlestitchsrc/sketch.js", "turtlestitchsrc/video.js", "turtlestitchsrc/maps.js",
    "turtlestitchsrc/extensions.js", "turtlestitchsrc/xml.js", "turtlestitchsrc/store.js",
    "turtlestitchsrc/locale.js", "turtlestitchsrc/cloud.js", "turtlestitchsrc/api.js",
    "turtlestitchsrc/embroider.js", "fullturtle.js")

  def ready(): Future[Unit] = pending.getOrElse {
    val base = BrowserDependencyLoading.siteUrl("resources/programs/20260212TurtleStitch/")
    val result = scripts.foldLeft(Future.successful(())) { (previous, path) =>
      previous.flatMap(_ => StartupTasks.idle(()).flatMap(_ => BrowserDependencyLoading.script(base + path)))
    }
    pending = Some(result)
    result.failed.foreach(_ => if (pending.contains(result)) pending = None)
    result
  }
}
