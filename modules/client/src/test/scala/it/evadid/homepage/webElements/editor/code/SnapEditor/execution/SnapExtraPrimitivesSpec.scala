package it.evadid.homepage.webElements.editor.code.SnapEditor.execution

import it.evadid.workbook.interactions.programming.state.snap.SnapTurtleCatalog
import munit.FunSuite
import scala.scalajs.js

/** The extra primitives survive Snap's primitive-table rebuild, which every project load performs. */
class SnapExtraPrimitivesSpec extends FunSuite {
  private val globals = js.Dynamic.global.globalThis
  private val names = List("SpriteMorph", "Color")
  private val saved = names.map(name => name -> globals.selectDynamic(name))

  /** Snap's shape: `initBlocks` replaces `blocks` with a fresh table of the built-in primitives. */
  private def installSnap(): js.Dynamic = {
    val sprite = js.eval("(function SpriteMorph() {})").asInstanceOf[js.Dynamic]
    val prototype = sprite.selectDynamic("prototype")
    prototype.updateDynamic("initBlocks")(js.eval("(function () { this.blocks = {forward: {spec: 'move %n steps'}}; return 'rebuilt'; })").asInstanceOf[js.Any])
    prototype.updateDynamic("blockColor")(js.Dynamic.literal())
    globals.updateDynamic("SpriteMorph")(sprite)
    globals.updateDynamic("Color")(js.eval("(function Color(r, g, b) { this.r = r; this.g = g; this.b = b; })").asInstanceOf[js.Any])
    prototype
  }
  override def afterEach(context: AfterEach): Unit = saved.foreach((name, value) => globals.updateDynamic(name)(value))

  private def selectors(prototype: js.Dynamic): Set[String] = js.Object.keys(prototype.selectDynamic("blocks").asInstanceOf[js.Object]).toSet

  test("installing adds every extra primitive with its spec, category and defaults") {
    val prototype = installSnap()
    SnapExtraPrimitives.install()
    assertEquals(selectors(prototype), Set("forward") ++ SnapTurtleCatalog.ExtraPrimitives.map(_.snapSelector))
    val backward = prototype.blocks.backward
    assertEquals((backward.`type`.asInstanceOf[String], backward.category.asInstanceOf[String], backward.spec.asInstanceOf[String]),
      ("command", "motion", "move back %n steps"))
    assertEquals(backward.defaults.asInstanceOf[js.Array[Double]].toList, List(10.0))
    assert(backward.only eq globals.SpriteMorph)
    assert(js.isUndefined(prototype.blocks.home.defaults), "a primitive without inputs has no defaults")
    assertEquals(prototype.blockColor.embroidery.g.asInstanceOf[Int], 120)
  }

  test("a later rebuild of the primitive table, as during a project load, keeps the extra primitives") {
    val prototype = installSnap()
    SnapExtraPrimitives.install()
    assertEquals(prototype.applyDynamic("initBlocks")().asInstanceOf[String], "rebuilt", "the original rebuild still runs and returns")
    assertEquals(selectors(prototype), Set("forward") ++ SnapTurtleCatalog.ExtraPrimitives.map(_.snapSelector))
  }

  test("installing again neither wraps the rebuild twice nor replaces an existing embroidery colour") {
    val prototype = installSnap()
    prototype.blockColor.updateDynamic("embroidery")("kept")
    SnapExtraPrimitives.install()
    val wrapped = prototype.selectDynamic("initBlocks")
    SnapExtraPrimitives.install()
    assert(prototype.selectDynamic("initBlocks") eq wrapped)
    assertEquals(prototype.blockColor.embroidery.asInstanceOf[String], "kept")
  }

  test("installing before Snap has built its table builds it first; without Snap nothing happens") {
    val prototype = installSnap()
    assert(js.isUndefined(prototype.selectDynamic("blocks")))
    SnapExtraPrimitives.install()
    assert(selectors(prototype).contains("circle"))
    globals.updateDynamic("SpriteMorph")(js.undefined)
    SnapExtraPrimitives.install()
  }
}
