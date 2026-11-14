package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.vm.naming.BeEntityName
import upickle.default.*

/** Typed drawing operations. Text aliases and argument lists are decoded at language boundaries. */
sealed trait TurtleCommand[+T] derives ReadWriter {
  import TurtleCommand.*

  def name: BeEntityName = this match {
    case Unsupported(entityName, _, _) => entityName
    case Stitch(mode, _, _) => mode.name
    case Forward(_) => BeEntityName.fromUniversalNameInParts("forward")
    case Backward(_) => BeEntityName.fromUniversalNameInParts("backward")
    case Left(_) => BeEntityName.fromUniversalNameInParts("left")
    case Right(_) => BeEntityName.fromUniversalNameInParts("right")
    case SetHeading(_) => BeEntityName.fromUniversalNameInParts("setheading")
    case SetX(_) => BeEntityName.fromUniversalNameInParts("setx")
    case SetY(_) => BeEntityName.fromUniversalNameInParts("sety")
    case Dot(_) => BeEntityName.fromUniversalNameInParts("dot")
    case PenSize(_) => BeEntityName.fromUniversalNameInParts("pensize")
    case Speed(_) => BeEntityName.fromUniversalNameInParts("speed")
    case Goto(_, _) => BeEntityName.fromUniversalNameInParts("goto")
    case Towards(_, _) => BeEntityName.fromUniversalNameInParts("towards")
    case ArcLeft(_, _) => BeEntityName.fromUniversalNameInParts("arc_left")
    case ArcRight(_, _) => BeEntityName.fromUniversalNameInParts("arc_right")
    case PenUp() => BeEntityName.fromUniversalNameInParts("pen_up")
    case PenDown() => BeEntityName.fromUniversalNameInParts("pen_down")
    case ShowTurtle() => BeEntityName.fromUniversalNameInParts("showturtle")
    case HideTurtle() => BeEntityName.fromUniversalNameInParts("hideturtle")
    case Home() => BeEntityName.fromUniversalNameInParts("home")
    case Reset() => BeEntityName.fromUniversalNameInParts("reset")
    case Clear() => BeEntityName.fromUniversalNameInParts("clear")
    case Circle(_, _, _) => BeEntityName.fromUniversalNameInParts("circle")
    case Color(_) => BeEntityName.fromUniversalNameInParts("color")
  }

  /** Derived argument views for VM observation/formatting, never separately stored state. */
  def args: Seq[T] = this match {
    case Forward(distance) => Seq(distance)
    case Backward(distance) => Seq(distance)
    case Left(degrees) => Seq(degrees)
    case Right(degrees) => Seq(degrees)
    case SetHeading(degrees) => Seq(degrees)
    case SetX(x) => Seq(x)
    case SetY(y) => Seq(y)
    case Dot(size) => Seq(size)
    case PenSize(size) => Seq(size)
    case Speed(speed) => Seq(speed)
    case Goto(x, y) => Seq(x, y)
    case Towards(x, y) => Seq(x, y)
    case ArcLeft(radius, extent) => Seq(radius, extent)
    case ArcRight(radius, extent) => Seq(radius, extent)
    case Circle(radius, extent, steps) => Seq(radius) ++ extent ++ steps
    case Stitch(_, numeric, _) => numeric
    case Unsupported(_, numeric, _) => numeric
    case _ => Nil
  }

  def stringArgs: Seq[String] = this match {
    case Color(color) => Seq(color)
    case Stitch(_, _, text) => text
    case Unsupported(_, _, text) => text
    case _ => Nil
  }

  def asDouble[N >: T](using numeric: Fractional[N]): TurtleCommand[Double] = mapNumbers(numeric.toDouble)

  def mapNumbers[S](convert: T => S): TurtleCommand[S] = this match {
    case Forward(distance) => Forward(convert(distance))
    case Backward(distance) => Backward(convert(distance))
    case Left(degrees) => Left(convert(degrees))
    case Right(degrees) => Right(convert(degrees))
    case SetHeading(degrees) => SetHeading(convert(degrees))
    case SetX(x) => SetX(convert(x))
    case SetY(y) => SetY(convert(y))
    case Dot(size) => Dot(convert(size))
    case PenSize(size) => PenSize(convert(size))
    case Speed(speed) => Speed(convert(speed))
    case Goto(x, y) => Goto(convert(x), convert(y))
    case Towards(x, y) => Towards(convert(x), convert(y))
    case ArcLeft(radius, extent) => ArcLeft(convert(radius), convert(extent))
    case ArcRight(radius, extent) => ArcRight(convert(radius), convert(extent))
    case PenUp() => PenUp()
    case PenDown() => PenDown()
    case ShowTurtle() => ShowTurtle()
    case HideTurtle() => HideTurtle()
    case Home() => Home()
    case Reset() => Reset()
    case Clear() => Clear()
    case Circle(radius, extent, steps) => Circle(convert(radius), extent.map(convert), steps.map(convert))
    case Color(color) => Color(color)
    case Stitch(mode, numeric, text) => Stitch(mode, numeric.map(convert), text)
    case Unsupported(entityName, numeric, text) => Unsupported(entityName, numeric.map(convert), text)
  }
}

enum TurtleStitchMode derives ReadWriter {
  case Running, Cross, Bean, Zigzag, Z, Satin, Tatami, Jump, Tie, Trim, StopRunning

  def name: BeEntityName = BeEntityName.fromUniversalNameInParts(this match {
    case Running => "running_stitch"
    case Cross => "cross_stitch"
    case Bean => "bean_stitch"
    case Zigzag => "zigzag_stitch"
    case Z => "z_stitch"
    case Satin => "satin_stitch"
    case Tatami => "tatami_stitch"
    case Jump => "jump_stitch"
    case Tie => "tie_stitch"
    case Trim => "trim_stitch"
    case StopRunning => "stop_running"
  })
}

object TurtleCommand {
  case class Forward[+T](distance: T) extends TurtleCommand[T] derives ReadWriter
  case class Backward[+T](distance: T) extends TurtleCommand[T] derives ReadWriter
  case class Left[+T](degrees: T) extends TurtleCommand[T] derives ReadWriter
  case class Right[+T](degrees: T) extends TurtleCommand[T] derives ReadWriter
  case class SetHeading[+T](degrees: T) extends TurtleCommand[T] derives ReadWriter
  case class SetX[+T](x: T) extends TurtleCommand[T] derives ReadWriter
  case class SetY[+T](y: T) extends TurtleCommand[T] derives ReadWriter
  case class Dot[+T](size: T) extends TurtleCommand[T] derives ReadWriter
  case class PenSize[+T](size: T) extends TurtleCommand[T] derives ReadWriter
  case class Speed[+T](speed: T) extends TurtleCommand[T] derives ReadWriter
  case class Goto[+T](x: T, y: T) extends TurtleCommand[T] derives ReadWriter
  case class Towards[+T](x: T, y: T) extends TurtleCommand[T] derives ReadWriter
  case class ArcLeft[+T](radius: T, extent: T) extends TurtleCommand[T] derives ReadWriter
  case class ArcRight[+T](radius: T, extent: T) extends TurtleCommand[T] derives ReadWriter
  case class PenUp[+T]() extends TurtleCommand[T] derives ReadWriter
  case class PenDown[+T]() extends TurtleCommand[T] derives ReadWriter
  case class ShowTurtle[+T]() extends TurtleCommand[T] derives ReadWriter
  case class HideTurtle[+T]() extends TurtleCommand[T] derives ReadWriter
  case class Home[+T]() extends TurtleCommand[T] derives ReadWriter
  case class Reset[+T]() extends TurtleCommand[T] derives ReadWriter
  case class Clear[+T]() extends TurtleCommand[T] derives ReadWriter
  case class Circle[+T](radius: T, extent: Option[T] = None, steps: Option[T] = None) extends TurtleCommand[T] derives ReadWriter
  case class Color[+T](color: String) extends TurtleCommand[T] derives ReadWriter
  /** Parameters are retained verbatim; the preview currently applies only the stitch mode. */
  case class Stitch[+T](mode: TurtleStitchMode, numeric: Seq[T] = Nil, text: Seq[String] = Nil) extends TurtleCommand[T] derives ReadWriter
  /** Unsupported or malformed VM calls remain observable without drawing geometry. */
  case class Unsupported[+T](entityName: BeEntityName, numeric: Seq[T], text: Seq[String]) extends TurtleCommand[T] derives ReadWriter


  private val canonicalNames: Map[String, BeEntityName] = List(
    List("forward", "fd") -> "forward",
    List("backward", "back", "bk") -> "backward",
    List("left", "lt", "turn_left", "turnleft") -> "left",
    List("right", "rt", "turn", "turn_right", "turnright") -> "right",
    List("setheading", "seth", "set_heading") -> "setheading",
    List("setx", "set_x", "setxposition") -> "setx",
    List("sety", "set_y", "setyposition") -> "sety",
    List("dot") -> "dot",
    List("pensize", "setsize", "width") -> "pensize",
    List("speed") -> "speed",
    List("goto", "setpos", "setposition", "goto_x_y", "gotoxy") -> "goto",
    List("towards") -> "towards",
    List("arc", "arcleft", "arc_left") -> "arc_left",
    List("arcright", "arc_right") -> "arc_right",
    List("penup", "pu", "up", "pen_up") -> "pen_up",
    List("pendown", "pd", "down", "pen_down") -> "pen_down",
    List("showturtle", "st", "show_turtle") -> "showturtle",
    List("hideturtle", "ht", "hide_turtle") -> "hideturtle",
    List("home") -> "home",
    List("reset") -> "reset",
    List("clear", "clearscreen", "clear_screen") -> "clear",
    List("circle") -> "circle",
    List("color", "pencolor", "pen_color", "setcolor", "set_color") -> "color",
    List("running_stitch", "runningstitch") -> "running_stitch",
    List("cross_stitch", "crossstitch") -> "cross_stitch",
    List("bean_stitch", "beanstitch") -> "bean_stitch",
    List("zigzag_stitch", "zigzagstitch") -> "zigzag_stitch",
    List("z_stitch", "zstitch") -> "z_stitch",
    List("satin_stitch", "satinstitch") -> "satin_stitch",
    List("tatami_stitch", "tatamistitch") -> "tatami_stitch",
    List("jump_stitch", "jumpstitch") -> "jump_stitch",
    List("tie_stitch", "tiestitch") -> "tie_stitch",
    List("trim_stitch", "trimstitch", "trim") -> "trim_stitch",
    List("stop_running", "stoprunning") -> "stop_running"
  ).flatMap { (aliases, canonical) =>
    val name = BeEntityName.fromUniversalNameInParts(canonical)
    aliases.map(_ -> name)
  }.toMap

  /** The same alias normalization is used for source occurrences and execution. */
  def canonicalName(name: BeEntityName): BeEntityName =
    canonicalNames.getOrElse(name.universalInterpretation().trim.toLowerCase.replace('-', '_'), name)

  def fromText[T: Fractional](name: String, args: Seq[T] = Nil, stringArgs: Seq[String] = Nil): TurtleCommand[T] =
    fromCall(BeEntityName.fromCodeString(name.trim.toLowerCase.replace('-', '_')), args, stringArgs)

  def fromCall[T: Fractional](name: BeEntityName, args: Seq[T] = Nil, stringArgs: Seq[String] = Nil): TurtleCommand[T] = {
    val normalized = canonicalName(name).universalInterpretation().trim.toLowerCase.replace('-', '_')
    def one(make: T => TurtleCommand[T]): Option[TurtleCommand[T]] = if args.size == 1 && stringArgs.isEmpty then args.headOption.map(make) else None
    def two(make: (T, T) => TurtleCommand[T]): Option[TurtleCommand[T]] = args match {
      case Seq(first, second) if stringArgs.isEmpty => Some(make(first, second))
      case _ => None
    }
    val decoded: Option[TurtleCommand[T]] = normalized match {
      case "forward" => one(Forward(_))
      case "backward" => one(Backward(_))
      case "left" => one(Left(_))
      case "right" => one(Right(_))
      case "setheading" => one(SetHeading(_))
      case "setx" => one(SetX(_))
      case "sety" => one(SetY(_))
      case "dot" => one(Dot(_))
      case "pensize" => one(PenSize(_))
      case "speed" => one(Speed(_))
      case "goto" => two(Goto(_, _))
      case "towards" => two(Towards(_, _))
      case "arc_left" => two(ArcLeft(_, _))
      case "arc_right" => two(ArcRight(_, _))
      case "pen_up" => Option.when(args.isEmpty && stringArgs.isEmpty)(PenUp())
      case "pen_down" => Option.when(args.isEmpty && stringArgs.isEmpty)(PenDown())
      case "showturtle" => Option.when(args.isEmpty && stringArgs.isEmpty)(ShowTurtle())
      case "hideturtle" => Option.when(args.isEmpty && stringArgs.isEmpty)(HideTurtle())
      case "home" => Option.when(args.isEmpty && stringArgs.isEmpty)(Home())
      case "reset" => Option.when(args.isEmpty && stringArgs.isEmpty)(Reset())
      case "clear" => Option.when(args.isEmpty && stringArgs.isEmpty)(Clear())
      case "circle" => if args.nonEmpty && args.size <= 3 && stringArgs.isEmpty then args.headOption.map(radius => Circle(radius, args.lift(1), args.lift(2))) else None
      case "color" =>
        stringArgs.headOption.orElse {
          if args.size >= 3 then {
            val n = summon[Fractional[T]]
            Some(s"rgb(${n.toDouble(args(0)).toInt},${n.toDouble(args(1)).toInt},${n.toDouble(args(2)).toInt})")
          } else None
        }.map(Color(_))
      case "running_stitch" => Some(Stitch(TurtleStitchMode.Running, args, stringArgs))
      case "cross_stitch" => Some(Stitch(TurtleStitchMode.Cross, args, stringArgs))
      case "bean_stitch" => Some(Stitch(TurtleStitchMode.Bean, args, stringArgs))
      case "zigzag_stitch" => Some(Stitch(TurtleStitchMode.Zigzag, args, stringArgs))
      case "z_stitch" => Some(Stitch(TurtleStitchMode.Z, args, stringArgs))
      case "satin_stitch" => Some(Stitch(TurtleStitchMode.Satin, args, stringArgs))
      case "tatami_stitch" => Some(Stitch(TurtleStitchMode.Tatami, args, stringArgs))
      case "jump_stitch" => Some(Stitch(TurtleStitchMode.Jump, args, stringArgs))
      case "tie_stitch" => Some(Stitch(TurtleStitchMode.Tie, args, stringArgs))
      case "trim_stitch" => Some(Stitch(TurtleStitchMode.Trim, args, stringArgs))
      case "stop_running" => Some(Stitch(TurtleStitchMode.StopRunning, args, stringArgs))
      case _ => None
    }
    decoded.getOrElse(Unsupported(name, args, stringArgs))
  }
}
