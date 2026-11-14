package it.evadid.core.datastructures.vectorShapes.svg

import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.types.BeDataType
import it.evadid.vm.types.BeDataType.{Boolean as BooleanType, Numeric as NumericType, String as StringType, Unit as UnitType}
import upickle.default.*

/** Typed drawing operations. Text aliases and argument lists are decoded at language boundaries. */
sealed trait TurtleCommand[+T] derives ReadWriter {
  import TurtleCommand.*

  def name: BeEntityName = this match {
    case Unsupported(entityName, _, _) => entityName
    case Stitch(mode, _, _) => mode.name
    case Forward(_) => TurtleCommand.resolveCanonicalNames("forward").get
    case Backward(_) => TurtleCommand.resolveCanonicalNames("backward").get
    case Left(_) => TurtleCommand.resolveCanonicalNames("left").get
    case Right(_) => TurtleCommand.resolveCanonicalNames("right").get
    case SetHeading(_) => TurtleCommand.resolveCanonicalNames("setheading").get
    case SetX(_) => TurtleCommand.resolveCanonicalNames("setx").get
    case SetY(_) => TurtleCommand.resolveCanonicalNames("sety").get
    case Dot(_) => TurtleCommand.resolveCanonicalNames("dot").get
    case PenSize(_) => TurtleCommand.resolveCanonicalNames("pensize").get
    case Speed(_) => TurtleCommand.resolveCanonicalNames("speed").get
    case Goto(_, _) => TurtleCommand.resolveCanonicalNames("goto").get
    case Towards(_, _) => TurtleCommand.resolveCanonicalNames("towards").get
    case ArcLeft(_, _) => TurtleCommand.resolveCanonicalNames("arc_left").get
    case ArcRight(_, _) => TurtleCommand.resolveCanonicalNames("arc_right").get
    case PenUp() => TurtleCommand.resolveCanonicalNames("pen_up").get
    case PenDown() => TurtleCommand.resolveCanonicalNames("pen_down").get
    case ShowTurtle() => TurtleCommand.resolveCanonicalNames("showturtle").get
    case HideTurtle() => TurtleCommand.resolveCanonicalNames("hideturtle").get
    case Home() => TurtleCommand.resolveCanonicalNames("home").get
    case Reset() => TurtleCommand.resolveCanonicalNames("reset").get
    case Clear() => TurtleCommand.resolveCanonicalNames("clear").get
    case Circle(_, _, _) => TurtleCommand.resolveCanonicalNames("circle").get
    case Color(_) => TurtleCommand.resolveCanonicalNames("color").get
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

  def name: BeEntityName = TurtleCommand.resolveCanonicalNames(this match {
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
  }).get
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

  /** Resolve a known turtle alias without constructing a temporary entity name.
    * Only unqualified names and the explicit turtle/Turtle qualifier are recognized.
    */
  def resolveCanonicalNames(sourceName: String): Option[BeEntityName] = {
    val source = sourceName.trim
    val unqualified = source.split("\\.", -1).toList match {
      case name :: Nil => Some(name)
      case owner :: name :: Nil if owner == "turtle" || owner == "Turtle" => Some(name)
      case _ => None
    }
    unqualified.flatMap { name =>
      val normalized = name.toLowerCase.replace('-', '_')
      canonicalNames.get(normalized).orElse(
        canonicalNames.get(NamingStyle.fromAnyNotationToParts(name).mkString("_")))
    }
  }

  def resolveCanonicalNames(name: BeEntityName): Option[BeEntityName] =
    resolveCanonicalNames(name.universalInterpretation())

  /** The same alias normalization is used for source occurrences and execution. */
  def canonicalName(name: BeEntityName): BeEntityName = resolveCanonicalNames(name).getOrElse(name)

  private type FunctionSignature = (String, List[(String, BeDataType)], Option[BeDataType])

  private val turtleMethodSignatures: List[FunctionSignature] = List(
    ("forward", List("distance" -> NumericType), Some(UnitType)),
    ("backward", List("distance" -> NumericType), Some(UnitType)),
    ("left", List("angle" -> NumericType), Some(UnitType)),
    ("right", List("angle" -> NumericType), Some(UnitType)),
    ("penUp", Nil, Some(UnitType)),
    ("penDown", Nil, Some(UnitType)),
    ("circle", List("radius" -> NumericType), Some(UnitType)),
    ("goto", List("x" -> NumericType, "y" -> NumericType), Some(UnitType)),
    ("setheading", List("angle" -> NumericType), Some(UnitType)),
    ("speed", List("value" -> NumericType), Some(UnitType)),
    ("color", List("value" -> StringType), Some(UnitType)),
    ("pensize", List("width" -> NumericType), Some(UnitType)),
    ("home", Nil, Some(UnitType)),
    ("runningStitch", List("steps" -> NumericType), Some(UnitType)),
    ("crossStitch", List("width" -> NumericType, "height" -> NumericType, "center" -> BooleanType), Some(UnitType)),
    ("beanStitch", List("length" -> NumericType), Some(UnitType)),
    ("zigzagStitch", List("density" -> NumericType, "width" -> NumericType, "center" -> BooleanType), Some(UnitType)),
    ("ZStitch", List("density" -> NumericType, "width" -> NumericType, "center" -> BooleanType), Some(UnitType)),
    ("satinStitch", List("width" -> NumericType, "center" -> BooleanType), Some(UnitType)),
    ("tatamiStitch", List("width" -> NumericType, "interval" -> NumericType, "center" -> BooleanType), Some(UnitType)),
    ("jumpStitch", List("enabled" -> BooleanType), Some(UnitType)),
    ("tieStitch", Nil, Some(UnitType)),
    ("trimStitch", Nil, Some(UnitType)),
    ("stopRunning", Nil, Some(UnitType)),
    ("setx", List("x" -> NumericType), Some(UnitType)),
    ("sety", List("y" -> NumericType), Some(UnitType)),
    ("towards", List("x" -> NumericType, "y" -> NumericType), Some(UnitType)),
    ("dot", List("size" -> NumericType), Some(UnitType)),
    ("arc_left", List("radius" -> NumericType, "extent" -> NumericType), Some(UnitType)),
    ("arc_right", List("radius" -> NumericType, "extent" -> NumericType), Some(UnitType)),
    ("showturtle", Nil, Some(UnitType)),
    ("hideturtle", Nil, Some(UnitType)),
    ("reset", Nil, Some(UnitType)),
    ("clear", Nil, Some(UnitType))
  )

  /** One shared, typed turtle definition; methods retain the canonical name objects. */
  lazy val turtleClass: BeDefineClass = BeDefineClass.withMethods(
    BeEntityName.fromUniversalNameInParts("turtle"), Nil,
    turtleMethodSignatures.map { (alias, inputs, output) =>
      def variable(name: String, dataType: BeDataType): BeDefineVariable =
        BeDefineVariable(BeEntityName.fromUniversalNameInParts(name), dataType)
      BeDefineClass.MethodSignature(
        resolveCanonicalNames(alias).getOrElse(throw IllegalArgumentException(s"Unknown turtle method: $alias")),
        inputs.map((name, dataType) => variable(name, dataType)),
        output.map(dataType => variable("result", dataType)))
    })

  def resolveTurtleMethod(sourceName: String): Option[BeDefineFunction] =
    resolveCanonicalNames(sourceName).flatMap { name =>
      turtleClass.methods.find(_.functionTypeInfo.displayName == name)
    }

  def resolveTurtleMethod(name: BeEntityName): Option[BeDefineFunction] =
    resolveTurtleMethod(name.universalInterpretation())

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
