package it.evadid.homepage.webElements.editor.compression

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.model.compression.*
import org.scalajs.dom
import scala.util.Try

/** A same-origin source image is sampled once per mount; only block settings are saved. */
case class CompressionImageView(imageResource: String, settings: Signal[ImageBlocks]) extends HtmlAppElement {
  override def getDomElement(): Element = {
    val pixels = Var(Option.empty[Vector[(Int, Int, Int)]])
    val failed = Var(false)
    var image: dom.HTMLImageElement = null
    var mounted = false
    div(cls := "compression-images",
      canvasTag(width := "64", height := "64", aria.label <-- CompressionExperimentView.localized("imagePreview"),
        onMountCallback(ctx => {
          mounted = true
          val canvas = ctx.thisNode.ref
          val context = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
          settings.combineWith(pixels.signal).foreach { (value, source) => source.foreach { rgb =>
            val transformed = CompressionAlgorithms.imageBlocks(rgb, 64, 64, value.luminanceBlock, value.chromaBlock)
            val output = context.createImageData(64, 64)
            transformed.zipWithIndex.foreach { case ((r, g, b), i) =>
              output.data(i * 4) = r; output.data(i * 4 + 1) = g; output.data(i * 4 + 2) = b; output.data(i * 4 + 3) = 255
            }
            context.putImageData(output, 0, 0)
          }}(using ctx.owner)
          image = dom.document.createElement("img").asInstanceOf[dom.HTMLImageElement]
          image.onload = (_: dom.Event) => if mounted then {
            Try {
              context.drawImage(image, 0, 0, 64, 64)
              val data = context.getImageData(0, 0, 64, 64).data
              pixels.set(Some((0 until 4096).map(i => (data(i * 4).toInt, data(i * 4 + 1).toInt, data(i * 4 + 2).toInt)).toVector))
            }.failed.foreach(_ => failed.set(true))
          }
          image.addEventListener("error", (_: dom.Event) => if mounted then failed.set(true))
          image.src = fullInfo.contentControl.fileFactory.relativeToTechnicalResources(imageResource).asUrlString
        }),
        onUnmountCallback(_ => { mounted = false; if image != null then image.onload = null })),
      p(role := "status", text <-- failed.signal.combineWith(pixels.signal).flatMapSwitch((error, source) =>
        if error then CompressionExperimentView.localized("imageLoadFailed")
        else if source.isEmpty then CompressionExperimentView.localized("imageLoading") else Val(""))))
  }
}
