package it.evadid.facades.internal

import com.raquo.airstream.state.Var
import it.evadid.facades.CodeMirror.Flowchart
import it.evadid.facades.CodeMirror.Api.*
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Native gutter integration; semantic graph calculation stays in the worker. */
private[evadid] object CodeMirrorFlowchartRendering {
  private def element(tag: String, attributes: (String, String)*): dom.Element = {
    val node = dom.document.createElementNS("http://www.w3.org/2000/svg", tag)
    attributes.foreach { case (key, value) => node.setAttribute(key, value) }
    node
  }
  private def row(chart: Flowchart, line: Int): dom.Element = {
    val svg = element("svg", "viewBox" -> s"0 0 ${chart.width} 20", "aria-hidden" -> "true",
      "preserveAspectRatio" -> "none")
    chart.rows.get(line).foreach(_.slices.foreach { slice =>
      val viewport = element("svg", "x" -> slice.x.toString, "y" -> "0", "width" -> slice.width.toString,
        "height" -> "20", "viewBox" -> s"0 ${slice.top} ${slice.width} ${slice.span}",
        "preserveAspectRatio" -> "none", "overflow" -> "hidden")
      viewport.appendChild(element("image", "href" -> slice.image, "width" -> slice.width.toString,
        "height" -> slice.height.toString))
      svg.appendChild(viewport)
    })
    svg
  }
  private class Marker(val layout: Flowchart, val line: Int) extends GutterMarker {
    override def eq(other: GutterMarker): Boolean = other match {
      case marker: Marker => (layout eq marker.layout) && line == marker.line
      case _ => false
    }
    override def toDOM(view: View): dom.Element = {
      val container = dom.document.createElement("div").asInstanceOf[dom.html.Div]
      container.className = "cm-flowchart-row"
      container.style.width = s"${layout.width}px"
      layout.rows.get(line).filter(_.title.nonEmpty).foreach(value => container.title = value.title)
      container.appendChild(row(layout, line))
      container
    }
  }
  def extension(chartForDoc: String => Future[Flowchart]): Extension = {
    val ready = Libraries.StateEffect.define[Flowchart]()
    val empty = Flowchart.empty
    val field = Libraries.StateField.define[Flowchart](js.Dynamic.literal(
      create = (() => empty): js.Function0[Flowchart],
      update = ((current: Flowchart, transaction: Transaction) =>
        transaction.effects.find(_.is(ready)).map(_.value.asInstanceOf[Flowchart]).getOrElse(current)
      ): js.Function2[Flowchart, Transaction, Flowchart]))
    val analysis = Libraries.ViewPlugin.define((view: View) => {
      val timer = Var(Option.empty[SetTimeoutHandle])
      val generation = Var(0)
      val destroyed = Var(false)
      def schedule(delay: Int): Unit = {
        timer.now().foreach(clearTimeout)
        generation.update(_ + 1)
        val current = generation.now()
        timer.set(Some(setTimeout(delay) {
          val source = view.state.doc.toString()
          Future(chartForDoc(source)).flatten
            .recover { case _ => empty }
            .foreach { next =>
              if !destroyed.now() && generation.now() == current && view.state.doc.toString() == source &&
                next != view.state.field(field) then
                view.dispatch(js.Dynamic.literal(effects = ready.of(next)))
            }
        }))
      }
      schedule(0)
      new js.Object {
        def update(event: ViewUpdate): Unit = if event.docChanged then schedule(150)
        def destroy(): Unit = {
          destroyed.set(true); generation.update(_ + 1); timer.now().foreach(clearTimeout)
        }
      }
    }, js.Dynamic.literal())
    js.Array[Extension](field, analysis, Libraries.gutter(js.Dynamic.literal(
      `class` = "cm-flowchart-gutter", renderEmptyElements = true,
      lineMarker = ((view: View, line: js.Dynamic) => {
        val current = view.state.field(field)
        if current.rows.isEmpty then null else new Marker(current, view.state.doc.lineAt(line.from.asInstanceOf[Int]).number)
      }): js.Function2[View, js.Dynamic, GutterMarker],
      initialSpacer = ((view: View) => new Marker(view.state.field(field), 0)): js.Function1[View, GutterMarker],
      updateSpacer = ((_: GutterMarker, event: ViewUpdate) => new Marker(event.state.field(field), 0)): js.Function2[GutterMarker, ViewUpdate, GutterMarker],
      lineMarkerChange = ((event: ViewUpdate) => event.transactions.exists(_.effects.exists(_.is(ready)))): js.Function1[ViewUpdate, Boolean]
    )))
  }
}
