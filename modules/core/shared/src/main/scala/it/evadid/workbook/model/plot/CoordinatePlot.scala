package it.evadid.workbook.model.plot

import upickle.default.*

case class PlotAxis(min: Double, max: Double, tick: Double) derives ReadWriter {
  require(min.isFinite && max.isFinite && tick.isFinite && min < max && (max - min).isFinite,
    "Axis bounds must be finite and increasing")
  require(tick > 0 && (max - min) / tick <= 50, "Use a positive interval and at most 50 tick intervals")
  def contains(value: Double): Boolean = value.isFinite && value >= min && value <= max
  def fraction(value: Double): Double = {
    require(contains(value), "Coordinate is outside the axis")
    (value - min) / (max - min)
  }
  def ticks: List[Double] = (0 to math.floor((max - min) / tick).toInt).map(i => min + i * tick).filter(_ <= max).toList
}
case class PlotPoint(x: Double, y: Double) derives ReadWriter {
  require(x.isFinite && y.isFinite, "Point coordinates must be finite")
}
case class PlotAnswer(points: List[PlotPoint] = Nil, connected: Boolean = true) derives ReadWriter {
  require(points.size <= PlotAnswer.maxPoints && points.map(_.x).distinct.size == points.size,
    "Store at most 100 points, with one point per x coordinate")
  def put(point: PlotPoint): PlotAnswer = copy(points = (points.filterNot(_.x == point.x) :+ point).sortBy(_.x))
  def remove(x: Double): PlotAnswer = copy(points = points.filterNot(_.x == x))
}
object PlotAnswer {
  val maxPoints = 100
  def parseNumber(value: String): Option[Double] = value.trim.replace(',', '.').toDoubleOption.filter(_.isFinite)
}
