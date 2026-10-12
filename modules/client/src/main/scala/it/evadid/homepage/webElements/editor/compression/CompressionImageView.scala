package it.evadid.homepage.webElements.editor.compression

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.model.compression.*
import org.scalajs.dom
import scala.scalajs.js.typedarray.Float64Array
import scala.util.Try

/** Full-resolution source pixels and partial edge blocks; canvas data is never saved as an answer. */
case class CompressionImageView(imageResource: String, settings: Signal[ImageBlocks]) extends HtmlAppElement {
  override def getDomElement(): Element = {
    val ready = Var(false); val failed = Var(false)
    var image: dom.HTMLImageElement = null; var mounted = false
    var source: dom.ImageData = null
    def average(channel: Float64Array, width: Int, height: Int, block: Int): Unit = {
      var top=0
      while top < height do { var left=0
        while left < width do {
          val bottom=math.min(height,top+block); val right=math.min(width,left+block)
          var sum=0.0;var y=top
          while y < bottom do {var x=left;while x < right do {sum+=channel(y*width+x);x+=1};y+=1}
          val mean=sum/((bottom-top)*(right-left));y=top
          while y < bottom do {var x=left;while x < right do {channel(y*width+x)=mean;x+=1};y+=1}
          left+=block
        };top+=block
      }
    }
    div(cls := "compression-image-comparison",
      img(src := fullInfo.contentControl.fileFactory.relativeToTechnicalResources(imageResource).asUrlString,
        alt <-- CompressionExperimentView.localized("src_widget_blockSize_original"),cls := "compression-original-image"),
      div(cls := "compression-image-viewport", canvasTag(aria.label <-- CompressionExperimentView.localized("imagePreview"),
        onMountCallback(ctx => {
          mounted=true
          val canvas=ctx.thisNode.ref;val context=canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
          settings.combineWith(ready.signal).foreach { (value, loaded) => if loaded then {
            val width=source.width;val height=source.height;val count=width*height
            val first=new Float64Array(count);val second=new Float64Array(count);val third=new Float64Array(count)
            var i=0
            while i < count do { val r=source.data(i*4).toDouble;val g=source.data(i*4+1).toDouble;val b=source.data(i*4+2).toDouble
              if value.separateChannels then {first(i)=0.299*r+0.587*g+0.114*b;second(i)=128-0.168736*r-0.331264*g+0.5*b;third(i)=128+0.5*r-0.418688*g-0.081312*b}
              else {first(i)=r;second(i)=g;third(i)=b};i+=1
            }
            average(first,width,height,value.luminanceBlock);average(second,width,height,value.chromaBlock);average(third,width,height,value.chromaBlock)
            val output=context.createImageData(width,height)
            def clip(v: Double): Int = math.max(0,math.min(255,math.round(v).toInt))
            i=0
            while i < count do {
              val a=first(i);val b=second(i);val c=third(i)
              output.data(i*4)=clip(if value.separateChannels then a+1.402*(c-128) else a)
              output.data(i*4+1)=clip(if value.separateChannels then a-0.344136*(b-128)-0.714136*(c-128) else b)
              output.data(i*4+2)=clip(if value.separateChannels then a+1.772*(b-128) else c)
              output.data(i*4+3)=255;i+=1
            }
            context.putImageData(output,0,0)
            canvas.style.width=s"${value.zoom}%";canvas.style.maxWidth="none"
          }}(using ctx.owner)
          image=dom.document.createElement("img").asInstanceOf[dom.HTMLImageElement]
          image.onload=(_:dom.Event) => if mounted then Try {
            canvas.width=image.naturalWidth;canvas.height=image.naturalHeight
            context.drawImage(image,0,0)
            source=context.getImageData(0,0,canvas.width,canvas.height);ready.set(true)
          }.failed.foreach(_ => failed.set(true))
          image.addEventListener("error",(_:dom.Event) => if mounted then failed.set(true))
          image.src=fullInfo.contentControl.fileFactory.relativeToTechnicalResources(imageResource).asUrlString
        }),onUnmountCallback(_ => {mounted=false;if image != null then image.onload=null}))),
      p(role := "status", text <-- failed.signal.combineWith(ready.signal).flatMapSwitch((error,loaded) =>
        if error then CompressionExperimentView.localized("imageLoadFailed") else if !loaded then CompressionExperimentView.localized("imageLoading") else Val(""))))
  }
}
