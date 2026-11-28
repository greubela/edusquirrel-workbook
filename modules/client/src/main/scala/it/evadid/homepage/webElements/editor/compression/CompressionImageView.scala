package it.evadid.homepage.webElements.editor.compression

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.basic.LabelSelection
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.interactions.compression.*
import org.scalajs.dom
import scala.scalajs.js.typedarray.Float64Array
import scala.util.Try

/** Full-resolution source pixels and partial edge blocks; canvas data is never saved as an answer. */
case class CompressionImageView(fullInfo: FullInfo, imageResource: String, settings: Signal[ImageBlocks]) extends HtmlAppElement {
  override def getDomElement(): Element = {
    val failed = Var(false)
    /** The loaded image's pixels; set once it has been drawn. */
    val source = Var(Option.empty[dom.ImageData])
    /** The loading image while the canvas is mounted; unmounting detaches its handler. */
    val image = Var(Option.empty[dom.HTMLImageElement])
    /** Replaces each `block`×`block` cell of `channel` (partial cells at the edges too) by its mean. */
    def average(channel: Float64Array, width: Int, height: Int, block: Int): Unit =
      for { top <- 0 until height by block; left <- 0 until width by block } {
        val bottom = math.min(height, top + block); val right = math.min(width, left + block)
        val cells = for { y <- top until bottom; x <- left until right } yield y * width + x
        val mean = cells.map(channel(_)).sum / cells.size
        cells.foreach(channel(_) = mean)
      }
    div(cls := "compression-image-comparison",
      img(src := fullInfo.contentControl.fileFactory.relativeToTechnicalResources(imageResource).asUrlString,
        alt <-- CompressionExperimentView.localized(fullInfo, "src_widget_blockSize_original"),cls := "compression-original-image"),
      div(cls := "compression-image-viewport", canvasTag(aria.label <-- CompressionExperimentView.localized(fullInfo, "imagePreview"),
        onMountCallback(ctx => {
          val canvas=ctx.thisNode.ref;val context=canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
          settings.combineWith(source.signal).foreach { (value, loaded) => loaded.foreach { source =>
            val width=source.width;val height=source.height;val count=width*height
            val first=new Float64Array(count);val second=new Float64Array(count);val third=new Float64Array(count)
            (0 until count).foreach { i => val r=source.data(i*4).toDouble;val g=source.data(i*4+1).toDouble;val b=source.data(i*4+2).toDouble
              if value.separateChannels then {first(i)=0.299*r+0.587*g+0.114*b;second(i)=128-0.168736*r-0.331264*g+0.5*b;third(i)=128+0.5*r-0.418688*g-0.081312*b}
              else {first(i)=r;second(i)=g;third(i)=b}
            }
            average(first,width,height,value.luminanceBlock);average(second,width,height,value.chromaBlock);average(third,width,height,value.chromaBlock)
            val output=context.createImageData(width,height)
            def clip(v: Double): Int = math.max(0,math.min(255,math.round(v).toInt))
            (0 until count).foreach { i =>
              val a=first(i);val b=second(i);val c=third(i)
              output.data(i*4)=clip(if value.separateChannels then a+1.402*(c-128) else a)
              output.data(i*4+1)=clip(if value.separateChannels then a-0.344136*(b-128)-0.714136*(c-128) else b)
              output.data(i*4+2)=clip(if value.separateChannels then a+1.772*(b-128) else c)
              output.data(i*4+3)=255
            }
            context.putImageData(output,0,0)
            canvas.style.width=s"${value.zoom}%";canvas.style.maxWidth="none"
          }}(using ctx.owner)
          val loading=dom.document.createElement("img").asInstanceOf[dom.HTMLImageElement]
          image.set(Some(loading))
          loading.onload=(_:dom.Event) => if image.now().contains(loading) then Try {
            canvas.width=loading.naturalWidth;canvas.height=loading.naturalHeight
            context.drawImage(loading,0,0)
            source.set(Some(context.getImageData(0,0,canvas.width,canvas.height)))
          }.failed.foreach(_ => failed.set(true))
          loading.addEventListener("error",(_:dom.Event) => if image.now().contains(loading) then failed.set(true))
          loading.src=fullInfo.contentControl.fileFactory.relativeToTechnicalResources(imageResource).asUrlString
        }),onUnmountCallback(_ => {image.now().foreach(_.onload=null);image.set(None)}))),
      p(role := "status", text <-- LabelSelection(failed.signal.combineWith(source.signal.map(_.nonEmpty)), Seq("imageLoadFailed", "imageLoading"),
        CompressionExperimentView.localized(fullInfo, _))((error,loaded) => if error then Some("imageLoadFailed") else Option.when(!loaded)("imageLoading"))))
  }
}
