package it.evadid.workbook.elements.interactionElements.plot

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.model.plot.*
import upickle.default.*

/** A learner-authored graph. Bounds validate the answer; they do not grade a hypothesis. */
case class CoordinatePlotInteraction(elementId: String, title: LanguageMapContentId,
    xLabel: LanguageMapContentId, yLabel: LanguageMapContentId, xAxis: PlotAxis, yAxis: PlotAxis)
    extends WorkbookInteractionElement[PlotAnswer] derives upickle.default.ReadWriter {
  override val defaultValue = PlotAnswer()
  override lazy val childrenOfThisElement = Nil
  override val associatedFactory = CoordinatePlotInteraction.factory
  def checked(answer: PlotAnswer): PlotAnswer = {
    require(answer.points.forall(p => xAxis.contains(p.x) && yAxis.contains(p.y)), "Points must be within the plot axes")
    answer.copy(points = answer.points.sortBy(_.x))
  }
  def put(answer: PlotAnswer, point: PlotPoint): PlotAnswer = checked(checked(answer).put(point))
  override val serializerInteractionContent: Serializer[PlotAnswer] =
    Serializer.fromUpickleJson(summon[ReadWriter[PlotAnswer]]).map(checked, checked)
}
object CoordinatePlotInteraction {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[CoordinatePlotInteraction] {
    override protected val constructorFieldOrder = List("elementId", "title", "xLabel", "yLabel", "xAxis", "yAxis")
    override def finishSerialization(base: WorkbookElementSerializable, e: CoordinatePlotInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("title", e.title).withElementAddedAs("xLabel", e.xLabel).withElementAddedAs("yLabel", e.yLabel)
        .withElementAddedAs("xAxis", e.xAxis).withElementAddedAs("yAxis", e.yAxis)
    override def finishDeserialization(e: WorkbookElementSerializable): CoordinatePlotInteraction =
      CoordinatePlotInteraction(e.elementId, e.getElementAs[LanguageMapContentId]("title"),
        e.getElementAs[LanguageMapContentId]("xLabel"), e.getElementAs[LanguageMapContentId]("yLabel"),
        e.getElementAs[PlotAxis]("xAxis"), e.getElementAs[PlotAxis]("yAxis"))
  }
}
