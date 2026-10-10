package it.evadid.core.datastructures.geometry

import upickle.default.*

/** Represents a line segment from a start point to an end point.
 */
case class Line[T: Fractional](start: Point[T], end: Point[T])

object Line {
  given [T: Fractional](using rw: ReadWriter[T]): ReadWriter[Line[T]] = summon[ReadWriter[(Point[T], Point[T])]].bimap[Line[T]](
    line => (line.start, line.end),
    (p1, p2) => Line[T](p1, p2)
  )
}