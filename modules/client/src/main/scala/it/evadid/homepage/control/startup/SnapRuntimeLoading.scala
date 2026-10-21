package it.evadid.homepage.control.startup

import com.raquo.airstream.state.Var
import scala.scalajs.js
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
    prepareResources()
    val result = BrowserLoading.ordered(scripts.map(path => BrowserLoading.resource("programs/20260212TurtleStitch/" + path))).map(_ => installCompatibility())
    pending.set(Some(result))
    result.failed.foreach { _ =>
      if pending.now().contains(result) then pending.set(None)
    }
    result
  }
  def preheat(): Unit = { load().failed.foreach(_ => ()); () }

  private val originalField = "eduSquirrelOriginalLoadProjectModel"

  private def prepareResources(): Unit =
    // The bundled IDE's language loader resolves its own assets using this global.
    js.Dynamic.global.globalThis.updateDynamic("base_prog_dir")(
      BrowserLoading.resource("programs/20260212TurtleStitch/"))

  private def installCompatibility(): Unit = {
    val serializer = js.Dynamic.global.globalThis.selectDynamic("SnapSerializer")
    require(js.typeOf(serializer) == "function", "SnapSerializer is unavailable after runtime loading")
    patchSerializer(serializer.selectDynamic("prototype"))
  }

  private[startup] def patchSerializer(prototype: js.Dynamic): Unit = {
    val existing = prototype.selectDynamic(originalField)
    if js.isUndefined(existing) then {
      val original = prototype.selectDynamic("loadProjectModel")
      require(js.typeOf(original) == "function", "SnapSerializer.loadProjectModel is unavailable")
      val patched: js.ThisFunction3[js.Dynamic, js.Dynamic, js.Any, js.Any, js.Any] =
        (serializer: js.Dynamic, xml: js.Dynamic, ide: js.Any, remixId: js.Any) => {
          val attributes: js.Dynamic = if xml == null || js.isUndefined(xml) then null
            else xml.selectDynamic("attributes")
          val sourceApp = if attributes == null || js.isUndefined(attributes) then js.undefined
            else attributes.selectDynamic("app")
          val targetApp = serializer.selectDynamic("app")
          if js.typeOf(sourceApp) == "string" && js.typeOf(targetApp) == "string" then {
            val source = sourceApp.asInstanceOf[String]
            val target = targetApp.asInstanceOf[String]
            val sourceName = source.takeWhile(_ != ' ')
            val targetName = target.takeWhile(_ != ' ')
            if sourceName.nonEmpty && targetName.nonEmpty && sourceName != targetName then
              attributes.updateDynamic("app")(target)
          }
          original.applyDynamic("call")(serializer, xml, ide, remixId)
        }
      prototype.updateDynamic(originalField)(original)
      prototype.updateDynamic("loadProjectModel")(patched)
    }
  }
}
