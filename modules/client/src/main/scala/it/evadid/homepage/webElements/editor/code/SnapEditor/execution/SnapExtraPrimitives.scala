package it.evadid.homepage.webElements.editor.code.SnapEditor.execution

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** TurtleStitch's embroidery, circle, home and backward primitives, which the bundled Snap lacks.
  *
  * Snap rebuilds its primitive table with `SpriteMorph.prototype.initBlocks()` whenever a loaded project
  * has a `<primitives>` element, as every saved project does. The primitives therefore follow every
  * rebuild; otherwise loaded scripts using them turn into `errorObsolete` blocks.
  */
object SnapExtraPrimitives {
  private val OriginalInitBlocks = "_eduExtraPrimitivesInitBlocks"

  /** Adds the primitives now and after every later primitive-table rebuild. Idempotent. */
  def install(): Unit = {
    val sprite = js.Dynamic.global.globalThis.selectDynamic("SpriteMorph")
    if js.isUndefined(sprite) || sprite == null then return
    val prototype = sprite.selectDynamic("prototype")
    val original = prototype.selectDynamic("initBlocks")
    if js.isUndefined(prototype.selectDynamic(OriginalInitBlocks)) && js.typeOf(original) == "function" then {
      prototype.updateDynamic(OriginalInitBlocks)(original)
      val wrapped: js.ThisFunction0[js.Dynamic, js.Any] = (self: js.Dynamic) => {
        val value = original.applyDynamic("call")(self)
        inject(prototype)
        value.asInstanceOf[js.Any]
      }
      prototype.updateDynamic("initBlocks")(wrapped)
    }
    if js.isUndefined(prototype.selectDynamic("blocks")) || prototype.selectDynamic("blocks") == null then
      prototype.applyDynamic("initBlocks")()
    else inject(prototype)
  }

  private def inject(prototype: js.Dynamic): Unit = {
    val blocks = prototype.selectDynamic("blocks")
    val blockColor = prototype.selectDynamic("blockColor")
    if !js.isUndefined(blockColor) && blockColor != null &&
        (js.isUndefined(blockColor.selectDynamic("embroidery")) || blockColor.selectDynamic("embroidery") == null) then
      blockColor.updateDynamic("embroidery")(js.Dynamic.newInstance(js.Dynamic.global.Color)(0, 120, 0))
    SnapTurtleCatalog.ExtraPrimitives.foreach { primitive =>
      primitive.extraSpec.foreach { spec =>
        val info = js.Dynamic.literal(`type` = "command", category = spec.category, spec = spec.spec,
          only = js.Dynamic.global.SpriteMorph)
        if spec.defaults.nonEmpty then
          info.updateDynamic("defaults")(spec.defaults.map(value => js.JSON.parse(ujson.write(value))).toJSArray)
        blocks.updateDynamic(primitive.snapSelector)(info)
      }
    }
  }
}
