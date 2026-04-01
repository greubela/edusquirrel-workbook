package contentmanagement.webElements.svg

import contentmanagement.webElements.svg.TurtlePathBuilder.{TurtleCommand, TurtleState}
import contentmanagement.webElements.svg.builder.{SvgPathBuilder, SvgPathBuilderCommand, SvgPathBuilderImmutable}
import datastructures.core.geometry.Point

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


case class TurtlePathBuilder[T: Fractional](
                                             startPoint: Point[T],
                                             turtleState: TurtleState[T],
                                             turtleCommands: List[TurtleCommand[T]],
                                             svgPathBuilder: SvgPathBuilderImmutable[T]
                                           ) {

  def pathBuilderCommands: List[SvgPathBuilderCommand[T]] = svgPathBuilder.furtherCommands

  val N = summon[Fractional[T]];

  import N.*

  lazy val zero = fromInt(0)

  def forward(distance: T): TurtlePathBuilder[T] = {
    val endPoint: Point[T] = ???
    ???
  }

  def left(rotationDeg: T): TurtlePathBuilder[T] = {
    this.copy(turtleState = turtleState.copy(headingDeg = turtleState.headingDeg + rotationDeg))
  }

  def getX(): T = turtleState.headingDeg

  def handleStringCommand(turtleCommand: TurtleCommand[T]): TurtlePathBuilder[T] = {
    // take into accounts synonyms and MAYBE other languages like German
    ???
  }

  /* functions todo:

*/


}

object TurtlePathBuilder {

  case class TurtleCommand[T: Fractional](name: String, args: List[T])

  case class TurtleState[T: Fractional](x: T, y: T, headingDeg: T, penDown: Boolean)

  def apply[T: Fractional](startPoint: Point[T], turtleCommands: List[TurtleCommand[T]]): TurtlePathBuilder[T] = {
    // todo
    ???
  }

  def apply[T: Fractional](): TurtlePathBuilder[T] = {
    lazy val zero: T = summon[Fractional[T]].fromInt(0)
    val start = Point[T](zero, zero)
    TurtlePathBuilder[T](start, TurtleState[T](zero, zero, zero, true), List(), SvgPathBuilderImmutable[T](start))
  }



}
