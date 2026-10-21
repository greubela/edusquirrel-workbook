package it.evadid.homepage.control.startup

import scala.scalajs.js

/** Compatibility at the native Snap boundary; editor ownership stays in SnapCodeEditor. */
private[startup] object SnapRuntimeCompatibility {
  private val originalField = "eduSquirrelOriginalLoadProjectModel"

  def prepareResources(): Unit =
    // The bundled IDE's language loader resolves its own assets using this global.
    js.Dynamic.global.globalThis.updateDynamic("base_prog_dir")(
      BrowserAssets.resource("programs/20260212TurtleStitch/"))

  def install(): Unit = {
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
