package it.evadid.homepage.webElements.editor.compression

import com.raquo.laminar.api.L.Var
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8ClampedArray

/** jsdom has neither a 2D canvas nor image decoding. This stands in for both: images "load" a fixed RGB raster
  * (or fail when their URL contains `missing`), and every canvas records the last image data written to it.
  */
final class FakeCanvas(width: Int, height: Int, pixels: Seq[(Int, Int, Int)]) {
  require(pixels.size == width * height)
  val written = Var(Vector.empty[(Int, Int, Seq[Int])])
  private val window = EditorDomSuite.window
  private val canvasPrototype = window.HTMLCanvasElement.prototype
  private val imagePrototype = window.HTMLImageElement.prototype
  private val objects = js.Dynamic.global.Object
  private val saved = List(canvasPrototype -> "getContext", imagePrototype -> "src", imagePrototype -> "naturalWidth",
    imagePrototype -> "naturalHeight").map((target, name) => (target, name, objects.getOwnPropertyDescriptor(target, name)))

  private def imageData(w: Int, h: Int, values: Seq[Int]): js.Dynamic = {
    val data = new Uint8ClampedArray(w * h * 4)
    values.zipWithIndex.foreach((value, index) => data(index) = value.toShort)
    js.Dynamic.literal(width = w, height = h, data = data)
  }
  private val rasterReader: js.Function4[Int, Int, Int, Int, js.Dynamic] =
    (_, _, w, h) => imageData(w, h, pixels.flatMap((r, g, b) => List(r, g, b, 255)))
  private val context: js.Function0[js.Dynamic] = () => js.Dynamic.literal(
    createImageData = ((w: Int, h: Int) => imageData(w, h, Nil)): js.Function2[Int, Int, js.Dynamic],
    drawImage = ((_: js.Any, _: Int, _: Int) => ()): js.Function3[js.Any, Int, Int, Unit],
    getImageData = rasterReader,
    putImageData = ((value: js.Dynamic, _: Int, _: Int) => {
      val data = value.data.asInstanceOf[Uint8ClampedArray]
      written.update(_ :+ (value.width.asInstanceOf[Int], value.height.asInstanceOf[Int], (0 until data.length).map(i => data(i).toInt)))
    }): js.Function3[js.Dynamic, Int, Int, Unit])

  def install(): Unit = {
    canvasPrototype.updateDynamic("getContext")(((_: js.Any) => context()): js.Function1[js.Any, js.Dynamic])
    objects.defineProperty(imagePrototype, "naturalWidth", js.Dynamic.literal(configurable = true, get = (() => width): js.Function0[Int]))
    objects.defineProperty(imagePrototype, "naturalHeight", js.Dynamic.literal(configurable = true, get = (() => height): js.Function0[Int]))
    objects.defineProperty(imagePrototype, "src", js.Dynamic.literal(configurable = true,
      get = ((self: js.Dynamic) => self.getAttribute("src")): js.ThisFunction0[js.Dynamic, js.Any],
      set = ((self: js.Dynamic, url: String) => {
        self.setAttribute("src", url)
        js.timers.setTimeout(0) {
          if url.contains("missing") then self.dispatchEvent(js.Dynamic.newInstance(window.Event)("error"))
          else if js.typeOf(self.onload) == "function" then self.onload(js.Dynamic.newInstance(window.Event)("load"))
        }
        ()
      }): js.ThisFunction1[js.Dynamic, String, Unit]))
  }

  def uninstall(): Unit = saved.foreach { (target, name, descriptor) =>
    if js.isUndefined(descriptor) then js.special.delete(target, name) else objects.defineProperty(target, name, descriptor)
  }

  /** RGB triples of the last image written to a canvas of this raster's size. */
  def lastRaster: Seq[(Int, Int, Int)] = written.now().reverseIterator.find((w, h, _) => w == width && h == height)
    .map(_._3.grouped(4).map(p => (p(0), p(1), p(2))).toList).getOrElse(Nil)
}
