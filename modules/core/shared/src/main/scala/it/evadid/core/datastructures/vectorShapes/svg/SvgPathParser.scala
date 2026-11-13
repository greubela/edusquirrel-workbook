package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import scala.annotation.tailrec

object SvgPathParser {
  private val commandLetters = "AaCcHhLlMmQqVvZz"
  private val numberPattern = "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?".r

  private case class Command(letter: Char, arguments: List[Double]) derives upickle.default.ReadWriter
  private case class BuildState(path: SvgPathBuilder[Double], current: Point[Double], subpathStart: Point[Double])
    derives upickle.default.ReadWriter

  def parseString(pathDString: String): Option[SvgPathBuilder[Double]] =
    tokenize(pathDString.trim).flatMap(readCommands).flatMap(build)

  private def isCommand(part: String): Boolean = part.length == 1 && commandLetters.contains(part.head)

  private def tokenize(source: String): Option[Vector[String]] = {
    @tailrec def scan(offset: Int, reversed: List[String]): Option[Vector[String]] =
      if offset == source.length then Some(reversed.reverse.toVector)
      else {
        val char = source.charAt(offset)
        if char == ',' || char.isWhitespace then scan(offset + 1, reversed)
        else if commandLetters.contains(char) then scan(offset + 1, char.toString :: reversed)
        else numberPattern.findPrefixOf(source.substring(offset)) match {
          case Some(number) => scan(offset + number.length, number :: reversed)
          case None => None
        }
      }
    scan(0, Nil)
  }

  private def arity(letter: Char): Int = letter.toLower match {
    case 'm' | 'l' => 2
    case 'h' | 'v' => 1
    case 'c' => 6
    case 'q' => 4
    case 'a' => 7
    case 'z' => 0
    case other => throw new IllegalArgumentException(s"Unsupported internal SVG command: $other")
  }

  private def readArguments(parts: Vector[String], start: Int, letter: Char): Option[List[Double]] = {
    val count = arity(letter)
    if start + count > parts.size then None
    else {
      val numbers = parts.slice(start, start + count).map(_.toDoubleOption.filter(_.isFinite))
      if numbers.exists(_.isEmpty) then None
      else {
        val arguments = numbers.flatten.toList
        Option.when(validArguments(letter, arguments))(arguments)
      }
    }
  }

  private def validArguments(letter: Char, arguments: List[Double]): Boolean =
    letter.toLower != 'a' || (arguments(0) >= 0 && arguments(1) >= 0 &&
      Set(0.0, 1.0).contains(arguments(3)) && Set(0.0, 1.0).contains(arguments(4)))

  private def implicitCommand(letter: Char): Char = letter match {
    case 'M' => 'L'
    case 'm' => 'l'
    case other => other
  }

  private def readCommands(parts: Vector[String]): Option[List[Command]] = {
    @tailrec def read(index: Int, active: Option[Char], reversed: List[Command]): Option[List[Command]] =
      if index == parts.size then Option.when(reversed.nonEmpty)(reversed.reverse)
      else {
        val next = if isCommand(parts(index)) then Some(parts(index).head -> (index + 1)) else active.map(_ -> index)
        next match {
          case Some((letter, start)) if letter.toLower == 'z' =>
            read(start, None, Command(letter, Nil) :: reversed)
          case Some((letter, start)) => readArguments(parts, start, letter) match {
            case Some(arguments) =>
              read(start + arguments.size, Some(implicitCommand(letter)), Command(letter, arguments) :: reversed)
            case None => None
          }
          case None => None
        }
      }
    read(0, None, Nil)
  }

  private def build(commands: List[Command]): Option[SvgPathBuilder[Double]] = commands match {
    case Command('M' | 'm', List(x, y)) :: remaining =>
      val start = Point(x, y)
      // Retain the explicit initial move, including the existing path-string representation.
      val initial = BuildState(SvgPathBuilder(start).moveToAbs(start), start, start)
      remaining.foldLeft(Option(initial))((state, command) => state.flatMap(appendCommand(_, command))).map(_.path)
    case _ => None
  }

  private def appendCommand(state: BuildState, command: Command): Option[BuildState] = {
    val current = state.current
    val path = state.path
    def at(next: SvgPathBuilder[Double], end: Point[Double]): Option[BuildState] =
      Some(state.copy(path = next, current = end))
    def moveTo(point: Point[Double]): Option[BuildState] =
      Some(BuildState(path.moveToAbs(point), point, point))

    command match {
      case Command('M', List(x, y)) => moveTo(Point(x, y))
      case Command('m', List(dx, dy)) => moveTo(Point(current.x + dx, current.y + dy))
      case Command('L', List(x, y)) => at(path.lineToAbs(Point(x, y)), Point(x, y))
      case Command('l', List(dx, dy)) => at(path.lineToRel(Dimension(dx, dy)), Point(current.x + dx, current.y + dy))
      case Command('H', List(x)) => at(path.horizontalLineWithWidth(x - current.x), Point(x, current.y))
      case Command('h', List(dx)) => at(path.horizontalLineWithWidth(dx), Point(current.x + dx, current.y))
      case Command('V', List(y)) => at(path.verticalLineWithHeight(y - current.y), Point(current.x, y))
      case Command('v', List(dy)) => at(path.verticalLineWithHeight(dy), Point(current.x, current.y + dy))
      case Command('C', List(x1, y1, x2, y2, x, y)) =>
        at(path.cubicBezierToAbs(Point(x1, y1), Point(x2, y2), Point(x, y)), Point(x, y))
      case Command('c', List(dx1, dy1, dx2, dy2, dx, dy)) =>
        at(path.cubicBezierToRel(Dimension(dx1, dy1), Dimension(dx2, dy2), Dimension(dx, dy)), Point(current.x + dx, current.y + dy))
      case Command('Q', List(cx, cy, x, y)) => at(path.quadraticBezierToAbs(Point(cx, cy), Point(x, y)), Point(x, y))
      case Command('q', List(dcx, dcy, dx, dy)) =>
        at(path.quadraticBezierWithRel(Dimension(dcx, dcy), Dimension(dx, dy)), Point(current.x + dx, current.y + dy))
      case Command('A', List(rx, ry, rotation, large, sweep, x, y)) =>
        at(path.arcToAbs(rx, ry, rotation, large != 0, sweep != 0, Point(x, y)), Point(x, y))
      case Command('a', List(rx, ry, rotation, large, sweep, dx, dy)) =>
        at(path.arcToRel(rx, ry, rotation, large != 0, sweep != 0, Dimension(dx, dy)), Point(current.x + dx, current.y + dy))
      case Command('Z' | 'z', Nil) => at(path.closePath(), state.subpathStart)
      case _ => None
    }
  }
}
