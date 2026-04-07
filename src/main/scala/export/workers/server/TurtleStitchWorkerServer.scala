package `export`.workers.server

import `export`.traits.SynchronizedWorkerServer
import `export`.traits.WorkerTraits.WorkerCommand
import org.scalajs.dom
import org.scalajs.dom.OffscreenCanvas
import util.IdHelper

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

/**
 * Worker server for TurtleStitch script snapshot/Lisp export commands.
 *
 * This server runs in synchronized (single-command) mode and avoids timeout/sleep waiting patterns.
 */
case class TurtleStitchWorkerServer(id: String = IdHelper.getNextId())
    extends SynchronizedWorkerServer("TurtleStitchWorker" + id, true) {

  private val domHelper = new DomHelper
  private var editorInstance: Option[TurtleStitchEditorInstance] = None

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
    val scriptUrl = domHelper.normalizeScriptUrl(basePath + scriptName)
    val p = Promise[Unit]()

    try {
      js.Dynamic.global.importScripts(scriptUrl)
      val loadingDuration = ChronoUnit.MILLIS.between(start, LocalDateTime.now())
      logInfo(s"Successfully loaded $scriptName in $loadingDuration ms")
      p.success(())
    } catch {
      case t: Throwable =>
        val loadingDuration = ChronoUnit.MILLIS.between(start, LocalDateTime.now())
        logInfo(s"Error at loading script $scriptName after $loadingDuration ms: ${t.getMessage}")
        p.failure(new RuntimeException(s"Failed to load $scriptName", t))
    }

    p.future
  }

  override def init(params: Map[String, String], canvas: Option[OffscreenCanvas]): Future[Boolean] = {
    logInfo("params: " + params + ", and canvas: " + canvas)

    val offscreenCanvas = canvas match {
      case Some(value) => value
      case None => return Future.failed(new IllegalArgumentException("TurtleStitchWorkerServer requires init canvas"))
    }

    try {
      logInfo("Installing worker DOM shim")
      TurtleStitchWorkerServer.installWorkerDomShim(TurtleStitchWorkerServer.basePath, domHelper)
      logInfo("Worker DOM shim installed")
      loadScripts(TurtleStitchWorkerServer.basePath, TurtleStitchWorkerServer.turtleScripts)
        .flatMap { _ =>
          val instance = new TurtleStitchEditorInstance(offscreenCanvas)
          instance.boot().map { _ =>
            editorInstance = Some(instance)
            true
          }
        }
    } catch {
      case t: Throwable =>
        val dyn = t.asInstanceOf[js.Dynamic]
        val stack = dyn.selectDynamic("stack").asInstanceOf[js.UndefOr[String]].getOrElse("<no-stack>")
        logInfo(s"Init failed: ${t.toString} | stack: $stack")
        Future.failed(t)
    }
  }

  override protected def handleTask(workerCommand: WorkerCommand): Future[Map[String, String]] = {
    val engine = editorInstance.getOrElse(throw new IllegalStateException("TurtleStitch editor not initialized"))

    workerCommand.name match {
      case "snapshotGreenFlagProgramsPngDataUrl" => handleSnapshotGreenFlagProgramsPngDataUrl(workerCommand, engine)
      case "getGreenFlagAsLispCode" => handleGreenFlagAsLispCode(workerCommand, engine)
      case other => Future.failed(new IllegalArgumentException(s"Unknown turtle command '$other'"))
    }
  }

  private def handleSnapshotGreenFlagProgramsPngDataUrl(
      workerCommand: WorkerCommand,
      engine: TurtleStitchEditorInstance
  ): Future[Map[String, String]] = {
    val xml = workerCommand.params.getOrElse("xml_content", throw new IllegalArgumentException("missing xml_content"))
    val language = workerCommand.params.getOrElse("language", "en")
    engine.snapshotGreenFlagProgramsPngDataUrl(xml, language).map(value => Map("value" -> value))
  }

  private def handleGreenFlagAsLispCode(
      workerCommand: WorkerCommand,
      engine: TurtleStitchEditorInstance
  ): Future[Map[String, String]] = {
    val xml = workerCommand.params.getOrElse("xml_content", throw new IllegalArgumentException("missing xml_content"))
    val language = workerCommand.params.getOrElse("language", "en")
    engine.getGreenFlagAsLispCode(xml, language).map(value => Map("value" -> value))
  }

  private final class TurtleStitchEditorInstance(canvas: OffscreenCanvas) {
    private var world: js.Dynamic = null
    private var ide: js.Dynamic = null

    def boot(): Future[Unit] = Future {
      if (world == null || ide == null) {
        val worldCtor = js.Dynamic.global.selectDynamic("WorldMorph")
        val ideCtor = js.Dynamic.global.selectDynamic("IDE_Morph")
        if (js.isUndefined(worldCtor) || js.isUndefined(ideCtor)) {
          throw new IllegalStateException("WorldMorph/IDE_Morph missing after script load")
        }

        world = js.Dynamic.newInstance(worldCtor)(canvas)
        world.updateDynamic("worldCanvas")(canvas)
        ide = js.Dynamic.newInstance(ideCtor)(js.Dynamic.literal(noAutoFill = true, noCloud = true))
        ide.openIn(world)
        forceLayout()
        stepWorld(3)
      }
    }

    private def forceLayout(): Unit =
      if (ide != null && domHelper.hasFunction(ide, "fixLayout")) ide.fixLayout()

    private def stepWorld(cycles: Int): Unit = {
      if (world == null) return
      var i = 0
      while (i < cycles) {
        if (domHelper.hasFunction(world, "doOneCycle")) world.doOneCycle()
        i += 1
      }
    }

    private def setLanguageWithoutProjectReloadAsync(lang: String): Future[Unit] = Future {
      val safe = if (lang == null || lang.trim.isEmpty) "en" else lang
      val snapTranslator = js.Dynamic.global.selectDynamic("SnapTranslator")
      if (!js.isUndefined(snapTranslator)) snapTranslator.updateDynamic("language")(safe)
    }

    private def loadProjectXmlCanonical(xml: String): Future[Unit] = {
      if (xml == null || xml.trim.isEmpty) Future.failed(new IllegalArgumentException("xml_content must be a non-empty string"))
      else {
        for {
          _ <- boot()
          _ <- setLanguageWithoutProjectReloadAsync("en")
          _ <- Future {
            ide.loadProjectXML(xml)
            forceLayout()
            stepWorld(4)
          }
        } yield ()
      }
    }

    private def greenFlagTopBlocks(): List[js.Dynamic] = {
      if (ide == null) return Nil
      val out = scala.collection.mutable.ListBuffer.empty[js.Dynamic]

      def addFromScripts(scripts: js.Dynamic): Unit = {
        if (scripts == null || js.isUndefined(scripts.selectDynamic("children"))) return
        scripts.selectDynamic("children").asInstanceOf[js.Array[js.Dynamic]].foreach { block =>
          val selector = domHelper.dynamicString(block, "selector")
          if (selector == "receiveGo") {
            val top = if (domHelper.hasFunction(block, "topBlock")) block.topBlock().asInstanceOf[js.Dynamic] else block
            if (top == block) out += block
          }
        }
      }

      addFromScripts(ide.selectDynamic("stage").selectDynamic("scripts"))
      val sprites = ide.selectDynamic("sprites")
      if (!js.isUndefined(sprites) && domHelper.hasFunction(sprites, "asArray")) {
        sprites.asArray().asInstanceOf[js.Array[js.Dynamic]].foreach(sprite => addFromScripts(sprite.selectDynamic("scripts")))
      }

      out.toList
    }

    private def greenFlagProgramPictures(): List[js.Dynamic] = {
      val topBlocks = greenFlagTopBlocks()
      if (topBlocks.isEmpty) throw new IllegalStateException("No green-flag blocks found for snapshot")

      topBlocks.map { block =>
        val top = if (domHelper.hasFunction(block, "topBlock")) block.topBlock().asInstanceOf[js.Dynamic] else block
        val pic =
          if (domHelper.hasFunction(top, "fullImage")) top.fullImage()
          else if (domHelper.hasFunction(top, "scriptPic")) top.scriptPic()
          else null

        if (pic == null || js.isUndefined(pic)) throw new IllegalStateException("Could not render a green-flag script picture")
        pic.asInstanceOf[js.Dynamic]
      }
    }

    private def snapshotProgramsPngDataUrl(pictures: List[js.Dynamic]): Future[String] = {
      forceLayout()
      stepWorld(2)

      val padding = 20
      val width = pictures.map(domHelper.dynamicInt(_, "width", 1)).foldLeft(1)(Math.max)
      val height = pictures.zipWithIndex.map { case (p, idx) =>
        val h = domHelper.dynamicInt(p, "height", 0)
        if (idx == pictures.size - 1) h else h + padding
      }.sum.max(1)

      val composite = new OffscreenCanvas(width, height)
      val ctx = composite.getContext("2d").asInstanceOf[js.Dynamic]
      if (ctx == null || js.isUndefined(ctx)) {
        Future.failed(new IllegalStateException("Could not get 2d context for snapshot composition"))
      } else {
        var y = 0
        pictures.foreach { picture =>
          ctx.drawImage(picture, 0, y)
          y += domHelper.dynamicInt(picture, "height", 0) + padding
        }
        domHelper.canvasToPngDataUrl(composite.asInstanceOf[js.Dynamic])
      }
    }

    private def greenFlagLispCode(): String = {
      val snippets = greenFlagTopBlocks().flatMap { block =>
        try {
          if (!domHelper.hasFunction(block, "toLisp")) None
          else {
            val text = block.toLisp(4).asInstanceOf[String]
            if (text == null || text.trim.isEmpty) None else Some(text)
          }
        } catch {
          case _: Throwable => None
        }
      }

      if (snippets.isEmpty) throw new IllegalStateException("No green-flag script found for Lisp export")
      snippets.mkString("\n\n")
    }

    def getGreenFlagAsLispCode(xml: String, language: String): Future[String] =
      for {
        _ <- loadProjectXmlCanonical(xml)
        _ <- setLanguageWithoutProjectReloadAsync(language)
      } yield greenFlagLispCode()

    def snapshotGreenFlagProgramsPngDataUrl(xml: String, language: String): Future[String] =
      for {
        _ <- loadProjectXmlCanonical(xml)
        _ <- setLanguageWithoutProjectReloadAsync(language)
        result <- snapshotProgramsPngDataUrl(greenFlagProgramPictures())
      } yield result
  }
}

/** Companion object containing worker bootstrap and DOM shim utilities. */
object TurtleStitchWorkerServer {

  @JSExportTopLevel("startTurtleWorkerServer")
  def startMathWorkerServer(): Unit =
    new TurtleStitchWorkerServer().start()

  /** Install minimal worker-safe DOM/window shims required by TurtleStitch runtime scripts. */
  private[server] def installWorkerDomShim(basePath: String, domHelper: DomHelper): Unit = {
    val listenersByScript = scala.collection.mutable.Map.empty[js.Dynamic, scala.collection.mutable.Map[String, js.Function1[js.Any, Unit]]]

    def tryLoadScriptNode(node: js.Dynamic): Unit = {
      val tag = domHelper.dynamicString(node, "tagName").toUpperCase
      val src = domHelper.dynamicString(node, "src")
      if (tag != "SCRIPT" || src.isEmpty) return

      try {
        val normalizedSrc =
          if (src.startsWith("http://") || src.startsWith("https://") || src.startsWith("/")) src
          else basePath + src
        js.Dynamic.global.importScripts(domHelper.normalizeScriptUrl(normalizedSrc))
        node.updateDynamic("__scriptLoaded")(true)
        node.selectDynamic("onload").asInstanceOf[js.UndefOr[js.Function0[Unit]]].foreach(_())
        listenersByScript.get(node).flatMap(_.get("load")).foreach(_(js.Dynamic.literal()))
      } catch {
        case t: Throwable =>
          node.selectDynamic("onerror").asInstanceOf[js.UndefOr[js.Function1[js.Any, Unit]]].foreach(_(t.asInstanceOf[js.Any]))
          listenersByScript.get(node).flatMap(_.get("error")).foreach(_(t.asInstanceOf[js.Any]))
      }
    }

    def createElement(tagNameRaw: String): js.Dynamic = {
      val tagName = Option(tagNameRaw).getOrElse("div").toLowerCase
      tagName match {
        case "canvas" =>
          val canvas = new OffscreenCanvas(300, 150).asInstanceOf[js.Dynamic]
          canvas.updateDynamic("style")(js.Dynamic.literal())
          canvas.updateDynamic("dataset")(js.Dynamic.literal())
          canvas.updateDynamic("tabIndex")(1)
          canvas.updateDynamic("addEventListener")((_: String, _: js.Function) => ())
          canvas.updateDynamic("removeEventListener")((_: String, _: js.Function) => ())
          canvas
        case "script" =>
          val node: js.Dynamic = js.Dynamic.literal()
          node.updateDynamic("tagName")("SCRIPT")
          node.updateDynamic("src")("")
          node.updateDynamic("async")(false)
          node.updateDynamic("onload")(null)
          node.updateDynamic("onerror")(null)
          node.updateDynamic("setAttribute")((name: String, value: String) => {
            if (name == "src") node.updateDynamic("src")(value)
          })
          listenersByScript.put(node, scala.collection.mutable.Map.empty)
          node.updateDynamic("addEventListener")((evt: String, fn: js.Function1[js.Any, Unit]) => listenersByScript.get(node).foreach(_.put(evt, fn)))
          node.updateDynamic("removeEventListener")((evt: String, _: js.Function1[js.Any, Unit]) => listenersByScript.get(node).foreach(_.remove(evt)))
          node
        case _ =>
          js.Dynamic.literal(
            tagName = tagName.toUpperCase,
            style = js.Dynamic.literal(),
            dataset = js.Dynamic.literal(),
            children = js.Array[js.Any](),
            appendChild = ((_: js.Any) => ()),
            removeChild = ((_: js.Any) => ())
          )
      }
    }

    val head = js.Dynamic.literal(
      appendChild = (node: js.Dynamic) => {
        tryLoadScriptNode(node)
        node
      }
    )

    val document = js.Dynamic.literal(
      head = head,
      body = js.Dynamic.literal(appendChild = ((_: js.Any) => ())),
      documentElement = js.Dynamic.literal(style = js.Dynamic.literal()),
      title = "",
      createElement = ((tagName: String) => createElement(tagName)),
      addEventListener = ((_: String, _: js.Function) => ()),
      removeEventListener = ((_: String, _: js.Function) => ())
    )

    js.Dynamic.global.self.updateDynamic("document")(document)
    js.Dynamic.global.self.updateDynamic("window")(js.Dynamic.global.self)

    try {
      js.Dynamic.global.importScripts("data:application/javascript,var%20document%20%3D%20self.document%3Bvar%20window%20%3D%20self.window%20%7C%7C%20self%3B")
    } catch {
      case _: Throwable =>
    }

    if (js.isUndefined(js.Dynamic.global.self.selectDynamic("requestAnimationFrame"))) {
      js.Dynamic.global.self.updateDynamic("requestAnimationFrame")((fn: js.Function1[Double, Unit]) => {
        fn(js.Date.now())
        0
      })
    }
    if (js.isUndefined(js.Dynamic.global.self.selectDynamic("cancelAnimationFrame"))) {
      js.Dynamic.global.self.updateDynamic("cancelAnimationFrame")((_: Int) => ())
    }
  }

  private val basePath = "../resources/programs/20260212TurtleStitch/"

  private val turtleScripts: List[String] = List(
    "adjusted/adjustedMorphic.js",
    "turtlestitchsrc/symbols.js",
    "turtlestitchsrc/widgets.js",
    "turtlestitchsrc/blocks.js",
    "turtlestitchsrc/threads.js",
    "adjusted/adjustedObjects.js",
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
