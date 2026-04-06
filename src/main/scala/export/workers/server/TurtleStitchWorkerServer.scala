package `export`.workers.server

import `export`.traits.{AbstractWorkerServer, SynchronizedWorkerServer}
import `export`.traits.WorkerTraits.WorkerCommand
import `export`.workers.MathWorkerServer
import org.scalajs.dom
import org.scalajs.dom.{OffscreenCanvas, document, html}
import util.IdHelper

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

case class TurtleStitchWorkerServer(id: String = IdHelper.getNextId()) extends AbstractWorkerServer("TurtleStitchWorker" + id, true) {

  private def loadScripts(basePath: String, scriptNames: List[String]): Future[Unit] = {
    logInfo(s"Loading scripts from $basePath: ${scriptNames.mkString(", ")}")
    val p = Promise[Unit]()
    var i = 0

    def loadNext(): Unit = {
      if (i < scriptNames.length) {
        loadScript(basePath, scriptNames(i)).onComplete { _ =>
          i += 1
          loadNext()
        }
      } else {
        p.success(())
      }
    }
    loadNext()

    p.future
  }

  private def loadScript(basePath: String, scriptName: String): Future[Unit] = {
    val start = LocalDateTime.now()
    val p = Promise[Unit]()
    val script = document.createElement("script").asInstanceOf[html.Script]
    script.src = basePath + scriptName
    script.async = false
    script.addEventListener("load", (_: dom.Event) => {
      val loadingDuration = ChronoUnit.MILLIS.between(start, LocalDateTime.now())
      logInfo(s"Successfully loaded $scriptName in $loadingDuration ms")
      p.trySuccess(())
    })
    script.addEventListener("error", (_: dom.Event) => {
      val loadingDuration = ChronoUnit.MILLIS.between(start, LocalDateTime.now())
      logInfo(s"Error at loading script $scriptName after $loadingDuration ms")
      p.tryFailure(new RuntimeException(s"Failed to load $scriptName"))
    })
    document.head.appendChild(script)
    p.future
  }

  override def init(params: Map[String, String], canvas: Option[OffscreenCanvas]): Future[Boolean] = {

    logInfo("params: " + params + ", and canvas: " + canvas)
    loadScripts(TurtleStitchWorkerServer.basePath, TurtleStitchWorkerServer.turtleScripts).map(_ => true)

  }


  override protected def handleTask(workerCommand: WorkerCommand): Future[Map[String, String]] = {

    Future.successful(Map("value" -> "42"))

  }


}


object TurtleStitchWorkerServer {

  @JSExportTopLevel("startTurtleWorkerServer")
  def startMathWorkerServer(): Unit =
    new TurtleStitchWorkerServer().start()


  private val basePath = "../resources/programs/20260212TurtleStitch/";

  private val turtleScripts: List[String] = List(
    "adjusted/adjustedMorphic.js", // adjusted
    "turtlestitchsrc/symbols.js",
    "turtlestitchsrc/widgets.js",
    "turtlestitchsrc/blocks.js",
    "turtlestitchsrc/threads.js",
    "adjusted/adjustedObjects.js", // adjusted
    "turtlestitchsrc/scenes.js",
    "turtlestitchsrc/gui.js",
    "turtlestitchsrc/paint.js",
    "turtlestitchsrc/lists.js",
    "turtlestitchsrc/byob.js",
    "turtlestitchsrc/tables.js",
    "turtlestitchsrc/sketch.js",
    "turtlestitchsrc/video.js",
    "turtlestitchsrc/maps.js",
    "turtlestitchsrc/extensions.js",
    "turtlestitchsrc/xml.js",
    "turtlestitchsrc/store.js",
    "turtlestitchsrc/locale.js",
    "turtlestitchsrc/cloud.js",
    "turtlestitchsrc/api.js",
    "turtlestitchsrc/embroider.js"
  )

}
