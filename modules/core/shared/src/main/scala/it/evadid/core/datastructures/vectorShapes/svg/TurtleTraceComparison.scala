package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand

object TurtleTraceComparison {
  val MaxCommands = 10000

  enum Failure {
    case InvalidScale, InvalidTolerance, TooManyCommands, UnsupportedCommand, InvalidArguments, NonFiniteValue, NumericRange
  }

  case class Result(matches: Boolean, expectedLength: Double, actualLength: Double, lengthDeviation: Double, maxDeviation: Double)

  private case class Vertex(distance: Double, point: Point[Double])
  private case class Trace(vertices: Vector[Vertex], commandCount: Int) {
    def length: Double = vertices.last.distance
  }

  def compare(expected: Seq[TurtleCommand[Double]], actual: Seq[TurtleCommand[Double]], requestedScale: Double,
      tolerance: Double): Either[Failure, Result] = {
    if !requestedScale.isFinite || requestedScale <= 0.0 then return Left(Failure.InvalidScale)
    if !tolerance.isFinite || tolerance <= 0.0 then return Left(Failure.InvalidTolerance)
    for {
      target <- trace(expected, requestedScale)
      drawing <- trace(actual, requestedScale)
      result <- compare(target, drawing, tolerance)
    } yield result
  }

  private def trace(commands: Seq[TurtleCommand[Double]], scale: Double): Either[Failure, Trace] = {
    val vertices = Vector.newBuilder[Vertex]
    var point = Point(0.0, 0.0)
    var distance = 0.0
    var heading = 0.0
    var count = 0
    vertices += Vertex(distance, point)
    val pending = commands.iterator
    while pending.hasNext do {
      val command = pending.next()
      count += 1
      if count > MaxCommands then return Left(Failure.TooManyCommands)
      val arguments = command.args.iterator.take(2).toVector
      if arguments.size != 1 || command.stringArgs.iterator.hasNext then return Left(Failure.InvalidArguments)
      val value = arguments.head
      if !value.isFinite then return Left(Failure.NonFiniteValue)
      command.name.trim.toLowerCase.replace('-', '_') match {
        case "forward" | "fd" =>
          val length = value / scale
          if !length.isFinite || value != 0.0 && length == 0.0 then return Left(Failure.NumericRange)
          if length != 0.0 then {
            val nextDistance = distance + math.abs(length)
            val radians = math.toRadians(heading)
            val next = Point(point.x + math.cos(radians) * length, point.y - math.sin(radians) * length)
            if !nextDistance.isFinite || nextDistance <= distance || !next.x.isFinite || !next.y.isFinite || next == point then
              return Left(Failure.NumericRange)
            distance = nextDistance
            point = next
            vertices += Vertex(distance, point)
          }
        case "right" | "rt" | "turn" | "turnright" | "turn_right" =>
          val rotated = (heading - value % 360.0) % 360.0
          heading = if rotated > 180.0 then rotated - 360.0 else if rotated < -180.0 then rotated + 360.0 else rotated
        case _ => return Left(Failure.UnsupportedCommand)
      }
    }
    Right(Trace(vertices.result(), count))
  }

  private class Cursor(vertices: Vector[Vertex]) {
    private var index = 0

    def nextDistance: Double =
      if index + 1 < vertices.size then vertices(index + 1).distance else Double.PositiveInfinity

    def at(distance: Double): Point[Double] = {
      while index + 1 < vertices.size && vertices(index + 1).distance <= distance do index += 1
      val from = vertices(index)
      if index + 1 == vertices.size || distance == from.distance then from.point
      else {
        val to = vertices(index + 1)
        val fraction = (distance - from.distance) / (to.distance - from.distance)
        Point(from.point.x + (to.point.x - from.point.x) * fraction, from.point.y + (to.point.y - from.point.y) * fraction)
      }
    }
  }

  private def compare(expected: Trace, actual: Trace, tolerance: Double): Either[Failure, Result] = {
    val magnitude = expected.length.max(actual.length)
    val count = expected.commandCount.max(actual.commandCount).max(1).toDouble
    val resolution = math.ulp(magnitude) * (64.0 * count)
    if magnitude > 0.0 && tolerance < resolution then return Left(Failure.NumericRange)
    val target = new Cursor(expected.vertices)
    val drawing = new Cursor(actual.vertices)
    var maxDeviation = 0.0
    var next = target.nextDistance.min(drawing.nextDistance)
    while next.isFinite do {
      val a = target.at(next)
      val b = drawing.at(next)
      val deviation = math.hypot(a.x - b.x, a.y - b.y)
      if !deviation.isFinite then return Left(Failure.NumericRange)
      maxDeviation = maxDeviation.max(deviation)
      next = target.nextDistance.min(drawing.nextDistance)
    }
    val lengthDeviation = math.abs(expected.length - actual.length)
    val matches = (expected.length == 0.0) == (actual.length == 0.0) &&
      lengthDeviation <= tolerance && maxDeviation <= tolerance
    Right(Result(matches, expected.length, actual.length, lengthDeviation, maxDeviation))
  }
}
