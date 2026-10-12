package it.evadid.workbook.model.plot

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.elements.interactionElements.plot.CoordinatePlotInteraction
import munit.FunSuite

class CoordinatePlotSpec extends FunSuite {
  private val plot = CoordinatePlotInteraction("test", LanguageMapContentId("plot/title"), LanguageMapContentId("plot/x"),
    LanguageMapContentId("plot/y"), PlotAxis(0, 3, 0.5), PlotAxis(0, 30, 10))
  test("axis ticks and fractions preserve physical units and endpoint positions") {
    assertEquals(plot.xAxis.ticks, List(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0))
    assertEquals(plot.yAxis.ticks, List(0.0, 10.0, 20.0, 30.0))
    val axis = PlotAxis(-10, 10, 5)
    assertEquals(axis.fraction(-10), 0.0)
    assertEquals(axis.fraction(0), 0.5)
    assertEquals(axis.fraction(10), 1.0)
    intercept[IllegalArgumentException](axis.fraction(11))
  }
  test("invalid and excessive axis ranges are rejected before generating ticks") {
    for ((min, max, tick) <- List((0.0, 0.0, 1.0), (2.0, 1.0, 1.0), (0.0, 3.0, 0.0),
        (0.0, 3.0, -1.0), (0.0, 3.0, 0.001), (Double.NaN, 3.0, 1.0),
        (0.0, Double.PositiveInfinity, 1.0), (-Double.MaxValue, Double.MaxValue, 1.0)))
      intercept[IllegalArgumentException](PlotAxis(min, max, tick))
  }
  test("point entry sorts by x, replaces an existing coordinate and removes only the chosen point") {
    val a = plot.put(plot.put(PlotAnswer(), PlotPoint(2, 20)), PlotPoint(1, 10))
    val b = plot.put(a, PlotPoint(1, 8))
    assertEquals(b.points, List(PlotPoint(1, 8), PlotPoint(2, 20)))
    assertEquals(b.remove(1).points, List(PlotPoint(2, 20)))
    assertEquals(a.points, List(PlotPoint(1, 10), PlotPoint(2, 20)))
    intercept[IllegalArgumentException](plot.put(a, PlotPoint(4, 10)))
    intercept[IllegalArgumentException](plot.put(a, PlotPoint(1, -1)))
  }
  test("stored points cannot contain duplicate coordinates, nonfinite values or more than 100 entries") {
    intercept[IllegalArgumentException](PlotPoint(Double.NaN, 0))
    intercept[IllegalArgumentException](PlotPoint(0, Double.PositiveInfinity))
    intercept[IllegalArgumentException](PlotAnswer(List(PlotPoint(1, 2), PlotPoint(1, 3))))
    val full = PlotAnswer((0 until 100).map(i => PlotPoint(i, i)).toList)
    assertEquals(full.put(PlotPoint(50, 2)).points.size, 100)
    intercept[IllegalArgumentException](full.put(PlotPoint(100, 100)))
  }
  test("numeric drafts accept decimal separators and exclude incomplete or nonfinite values") {
    assertEquals(PlotAnswer.parseNumber(" 1,5 "), Some(1.5))
    assertEquals(PlotAnswer.parseNumber("1.5"), Some(1.5))
    for (draft <- List("", "-", "1,2,3", "NaN", "Infinity", "1e999"))
      assertEquals(PlotAnswer.parseNumber(draft), None)
  }
  test("answer serialization preserves connection choice and checks bounds in both directions") {
    val serializer = plot.serializerInteractionContent
    val answer = PlotAnswer(List(PlotPoint(2, 20), PlotPoint(0.5, 30)), connected = false)
    assertEquals(serializer.deserialize(serializer.serialize(answer)), answer.copy(points = answer.points.sortBy(_.x)))
    intercept[it.evadid.distribution.command.SerializedException](serializer.serialize(PlotAnswer(List(PlotPoint(3.5, 20)))))
    intercept[it.evadid.distribution.command.SerializedException](serializer.deserialize("""{"points":[{"x":1,"y":31}],"connected":true}"""))
    intercept[it.evadid.distribution.command.SerializedException](serializer.deserialize("""{"points":[{"x":1,"y":2},{"x":1,"y":3}],"connected":true}"""))
  }
}
