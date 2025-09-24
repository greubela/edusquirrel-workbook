package interactionPlugins.turtleEnvironment

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import util.IdHelper

enum TurtleBlockArea {
  case Below, Inside, Parameter
}

enum TurtleValueType {
  case Numeric, Boolean
}

case class TurtleBlockSvgShape(
                                width: Double,
                                height: Double,
                                pathDefinition: String,
                                backgroundColor: String,
                                borderColor: String = "#0c3359",
                                textColor: String = "white",
                                areas: Set[TurtleBlockArea] = Set(TurtleBlockArea.Below)
                              ) {

  def render(label: String): L.SvgElement = {
    val viewBox = s"0 0 $width $height"
    svg.svg(
      svg.viewBox := viewBox,
      svg.width := width.toString,
      svg.height := height.toString,
      svg.path(
        svg.d := pathDefinition,
        svg.fill := backgroundColor,
        svg.stroke := borderColor,
        svg.strokeWidth := "2"
      ),
      svg.text(
        svg.fill := textColor,
        svg.fontSize := (math.max(10, height / 4)).toInt.toString,
        svg.textAnchor := "middle",
        svg.alignmentBaseline := "middle",
        svg.x := (width / 2).toString,
        svg.y := (height / 2).toString,
        label
      )
    )
  }
}

object TurtleBlockSvgShape {

  val HatBlock: TurtleBlockSvgShape = TurtleBlockSvgShape(
    width = 160,
    height = 48,
    pathDefinition = "M4 44 L4 16 Q4 4 16 4 L144 4 Q156 4 156 16 L156 44 Z",
    backgroundColor = "#4C97FF",
    areas = Set(TurtleBlockArea.Below)
  )

  val StackBlock: TurtleBlockSvgShape = TurtleBlockSvgShape(
    width = 160,
    height = 44,
    pathDefinition = "M4 40 L4 12 Q4 4 12 4 L60 4 L70 14 L150 14 Q156 14 156 20 L156 40 Z",
    backgroundColor = "#9966FF",
    areas = Set(TurtleBlockArea.Below)
  )

  val CBlock: TurtleBlockSvgShape = TurtleBlockSvgShape(
    width = 176,
    height = 86,
    pathDefinition =
      "M4 82 L4 18 Q4 6 16 6 L120 6 L130 16 L160 16 Q172 16 172 28 L172 38 Q172 50 160 50 L84 50 L74 60 L74 64 L160 64 Q172 64 172 76 L172 82 Q172 94 160 94 L16 94 Q4 94 4 82 Z",
    backgroundColor = "#00A99D",
    areas = Set(TurtleBlockArea.Below, TurtleBlockArea.Inside)
  )

  val CapBlock: TurtleBlockSvgShape = TurtleBlockSvgShape(
    width = 160,
    height = 32,
    pathDefinition = "M4 28 L4 8 Q4 0 12 0 L148 0 Q156 0 156 8 L156 28 Z",
    backgroundColor = "#008F7A",
    areas = Set.empty
  )

  val ReporterBlock: TurtleBlockSvgShape = TurtleBlockSvgShape(
    width = 140,
    height = 36,
    pathDefinition = "M12 32 Q4 32 4 24 L4 12 Q4 4 12 4 L128 4 Q136 4 136 12 L136 24 Q136 32 128 32 Z",
    backgroundColor = "#FFAB19",
    areas = Set(TurtleBlockArea.Parameter)
  )
}

enum TurtleBlockCategory {
  case Control, Motion, Pen, Logic, Reporter, Operators, Math
}

case class TurtleBlockSocketDefinition(
                                        id: String,
                                        label: String,
                                        valueType: TurtleValueType,
                                        color: String,
                                        maxChildren: Int = 1,
                                        defaultNumeric: Double = 0.0,
                                        defaultBoolean: Boolean = false
                                      )

case class TurtleBlockContext(
                               block: TurtleBlock,
                               socketExpressions: Map[String, TurtleExpression]
                             )

sealed trait TurtleBlockBehaviour

object TurtleBlockBehaviour {
  case class Command(build: TurtleBlockContext => TurtleCommand) extends TurtleBlockBehaviour

  case class Reporter(valueType: TurtleValueType, build: TurtleBlockContext => TurtleExpression) extends TurtleBlockBehaviour
}

sealed trait TurtleCommand {
  def programText: String
}

object TurtleCommand {
  case object WhenProgramStarted extends TurtleCommand {
    override val programText: String = "whenProgramStarted"
  }

  case class Forward(distance: TurtleExpression) extends TurtleCommand {
    override def programText: String = s"forward(${distance.render})"
  }

  case class TurnRight(angle: TurtleExpression) extends TurtleCommand {
    override def programText: String = s"turnRight(${angle.render})"
  }

  case class TurnLeft(angle: TurtleExpression) extends TurtleCommand {
    override def programText: String = s"turnLeft(${angle.render})"
  }

  case object PenUp extends TurtleCommand {
    override val programText: String = "penUp"
  }

  case object PenDown extends TurtleCommand {
    override val programText: String = "penDown"
  }

  case object TurnAround extends TurtleCommand {
    override val programText: String = "turnAround"
  }

  case class Repeat(times: TurtleExpression) extends TurtleCommand {
    override def programText: String = s"repeat(${times.render})"
  }

  case object EndRepeat extends TurtleCommand {
    override val programText: String = "endRepeat"
  }

  case class IfPenState(expectPenDown: Boolean) extends TurtleCommand {
    override def programText: String = s"ifPen${if (expectPenDown) "Down" else "Up"}"
  }

  case object EndIf extends TurtleCommand {
    override val programText: String = "endIf"
  }

  case class WhilePenState(expectPenDown: Boolean) extends TurtleCommand {
    override def programText: String = s"whilePen${if (expectPenDown) "Down" else "Up"}"
  }

  case object EndWhile extends TurtleCommand {
    override val programText: String = "endWhile"
  }
}

case class TurtleBlockDefinition(
                                  key: String,
                                  category: TurtleBlockCategory,
                                  shape: TurtleBlockSvgShape,
                                  labelForValue: Option[Double] => String,
                                  defaultValue: Option[Double],
                                  behaviour: TurtleBlockBehaviour,
                                  sockets: List[TurtleBlockSocketDefinition] = Nil,
                                  closingCommand: Option[TurtleCommand] = None,
                                  sanitizeValueFn: Option[Double] => Option[Double] = identity
                                ) {

  lazy val areas: Set[TurtleBlockArea] =
    if (sockets.nonEmpty) shape.areas + TurtleBlockArea.Parameter else shape.areas

  def supportsArea(area: TurtleBlockArea): Boolean = areas.contains(area)

  def valueType: Option[TurtleValueType] = behaviour match {
    case TurtleBlockBehaviour.Reporter(valueType, _) => Some(valueType)
    case _ => None
  }

  def sanitizeValue(value: Option[Double]): Option[Double] = sanitizeValueFn(value)

  def createInstance(initialValue: Option[Double] = None): TurtleBlock = {
    val sanitized = sanitizeValue(initialValue.orElse(defaultValue))
    TurtleBlock(TurtleBlock.newId(), this, sanitized)
  }
}

case class TurtleBlock(
                        id: String,
                        definition: TurtleBlockDefinition,
                        value: Option[Double]
                      ) {
  def label: String = definition.labelForValue(value)

  def updateValue(newValue: Double): TurtleBlock = {
    val sanitized = definition.sanitizeValue(Some(newValue)).orElse(value)
    copy(value = sanitized)
  }

  def numericValue(default: Double = 0.0): Double = value.getOrElse(default)

  def booleanValue(default: Boolean = false): Boolean = value match {
    case Some(num) => num >= 0.5
    case None => default
  }
}

object TurtleBlock {
  def newId(): String = IdHelper.getNextId()
}

object TurtleBlockLibrary {

  private val controlShape = TurtleBlockSvgShape.HatBlock
  private val motionShape = TurtleBlockSvgShape.StackBlock
  private val motionParameterShape = TurtleBlockSvgShape.StackBlock.copy(areas = Set(TurtleBlockArea.Below, TurtleBlockArea.Parameter))
  private val penShape = TurtleBlockSvgShape.StackBlock.copy(backgroundColor = "#FF8C1A")
  private val logicShapeWithParameter = TurtleBlockSvgShape.CBlock.copy(areas = Set(TurtleBlockArea.Below, TurtleBlockArea.Inside, TurtleBlockArea.Parameter))
  private val reporterShape = TurtleBlockSvgShape.ReporterBlock

  private val distanceSocket = TurtleBlockSocketDefinition("distance", "steps", TurtleValueType.Numeric, "#4C97FF", defaultNumeric = 100)
  private val angleSocket = TurtleBlockSocketDefinition("angle", "degrees", TurtleValueType.Numeric, "#FF6680", defaultNumeric = 90)
  private val repeatSocket = TurtleBlockSocketDefinition("times", "times", TurtleValueType.Numeric, "#00A99D", defaultNumeric = 4)

  private val unarySocket = TurtleBlockSocketDefinition("value", "value", TurtleValueType.Numeric, "#FFAB19")
  private val leftSocket = TurtleBlockSocketDefinition("left", "left", TurtleValueType.Numeric, "#FFAB19")
  private val rightSocket = TurtleBlockSocketDefinition("right", "right", TurtleValueType.Numeric, "#FFAB19")

  val whenProgramStarted: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "whenProgramStarted",
    category = TurtleBlockCategory.Control,
    shape = controlShape,
    labelForValue = _ => "When program started",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command(_ => TurtleCommand.WhenProgramStarted)
  )

  val forward: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "forward",
    category = TurtleBlockCategory.Motion,
    shape = motionParameterShape,
    labelForValue = _ => "forward",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      val distance = ctx.socketExpressions.getOrElse(distanceSocket.id, TurtleExpression.defaultForSocket(distanceSocket))
      TurtleCommand.Forward(distance)
    },
    sockets = List(distanceSocket)
  )

  val turnRight: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "turnRight",
    category = TurtleBlockCategory.Motion,
    shape = motionParameterShape.copy(backgroundColor = "#FF6680"),
    labelForValue = _ => "turn right",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      val angle = ctx.socketExpressions.getOrElse(angleSocket.id, TurtleExpression.defaultForSocket(angleSocket))
      TurtleCommand.TurnRight(angle)
    },
    sockets = List(angleSocket)
  )

  val turnLeft: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "turnLeft",
    category = TurtleBlockCategory.Motion,
    shape = motionParameterShape.copy(backgroundColor = "#FF6680"),
    labelForValue = _ => "turn left",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      val angle = ctx.socketExpressions.getOrElse(angleSocket.id, TurtleExpression.defaultForSocket(angleSocket))
      TurtleCommand.TurnLeft(angle)
    },
    sockets = List(angleSocket)
  )

  val penUp: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "penUp",
    category = TurtleBlockCategory.Pen,
    shape = penShape,
    labelForValue = _ => "pen up",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command(_ => TurtleCommand.PenUp)
  )

  val penDown: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "penDown",
    category = TurtleBlockCategory.Pen,
    shape = penShape.copy(backgroundColor = "#FFBF00"),
    labelForValue = _ => "pen down",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command(_ => TurtleCommand.PenDown)
  )

  val turnAround: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "turnAround",
    category = TurtleBlockCategory.Motion,
    shape = motionShape.copy(backgroundColor = "#00B295"),
    labelForValue = _ => "turn around",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command(_ => TurtleCommand.TurnAround)
  )

  val repeat: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "repeat",
    category = TurtleBlockCategory.Logic,
    shape = logicShapeWithParameter,
    labelForValue = _ => "repeat",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      val times = ctx.socketExpressions.getOrElse(repeatSocket.id, TurtleExpression.defaultForSocket(repeatSocket))
      TurtleCommand.Repeat(times)
    },
    sockets = List(repeatSocket),
    closingCommand = Some(TurtleCommand.EndRepeat)
  )

  val ifPenState: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "ifPenState",
    category = TurtleBlockCategory.Logic,
    shape = logicShapeWithParameter.copy(backgroundColor = "#3FBBAD"),
    labelForValue = value => if (value.exists(_ >= 0.5)) "if pen is down" else "if pen is up",
    defaultValue = Some(1.0),
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      TurtleCommand.IfPenState(ctx.block.booleanValue(default = true))
    },
    sanitizeValueFn = value => Some(if (value.exists(_ >= 0.5)) 1.0 else 0.0),
    closingCommand = Some(TurtleCommand.EndIf)
  )

  val whilePenState: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "whilePenState",
    category = TurtleBlockCategory.Logic,
    shape = logicShapeWithParameter.copy(backgroundColor = "#0D9E8A"),
    labelForValue = value => if (value.exists(_ >= 0.5)) "while pen is down" else "while pen is up",
    defaultValue = Some(1.0),
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      TurtleCommand.WhilePenState(ctx.block.booleanValue(default = true))
    },
    sanitizeValueFn = value => Some(if (value.exists(_ >= 0.5)) 1.0 else 0.0),
    closingCommand = Some(TurtleCommand.EndWhile)
  )

  val numericLiteral: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "numericLiteral",
    category = TurtleBlockCategory.Reporter,
    shape = reporterShape.copy(backgroundColor = "#FFAB19"),
    labelForValue = value => value.map(v => s"${v.formatted("%.1f").replaceAll("\\.0$", "")}").getOrElse("number"),
    defaultValue = Some(0.0),
    behaviour = TurtleBlockBehaviour.Reporter(TurtleValueType.Numeric, ctx => TurtleExpression.Literal(ctx.block.numericValue())),
    sanitizeValueFn = value => value.map(v => if (v.isNaN || v.isInfinity) 0.0 else v)
  )

  val booleanLiteral: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "booleanLiteral",
    category = TurtleBlockCategory.Reporter,
    shape = reporterShape.copy(backgroundColor = "#0E9D57"),
    labelForValue = value => if (value.exists(_ >= 0.5)) "true" else "false",
    defaultValue = Some(1.0),
    behaviour = TurtleBlockBehaviour.Reporter(TurtleValueType.Boolean, ctx => TurtleExpression.BooleanLiteral(ctx.block.booleanValue(default = true))),
    sanitizeValueFn = value => Some(if (value.exists(_ >= 0.5)) 1.0 else 0.0)
  )

  private def binaryReporter(
                              key: String,
                              operator: TurtleBinaryOperator,
                              color: String,
                              label: String
                            ): TurtleBlockDefinition =
    TurtleBlockDefinition(
      key = key,
      category = TurtleBlockCategory.Operators,
      shape = reporterShape.copy(backgroundColor = color),
      labelForValue = _ => label,
      defaultValue = None,
      behaviour = TurtleBlockBehaviour.Reporter(
        TurtleValueType.Numeric,
        ctx => {
          val left = ctx.socketExpressions.getOrElse(leftSocket.id, TurtleExpression.defaultForSocket(leftSocket))
          val right = ctx.socketExpressions.getOrElse(rightSocket.id, TurtleExpression.defaultForSocket(rightSocket))
          TurtleExpression.Binary(operator, left, right)
        }
      ),
      sockets = List(leftSocket, rightSocket)
    )

  private def unaryReporter(
                             key: String,
                             function: TurtleUnaryFunction,
                             color: String,
                             label: String
                           ): TurtleBlockDefinition =
    TurtleBlockDefinition(
      key = key,
      category = TurtleBlockCategory.Math,
      shape = reporterShape.copy(backgroundColor = color),
      labelForValue = _ => label,
      defaultValue = None,
      behaviour = TurtleBlockBehaviour.Reporter(
        TurtleValueType.Numeric,
        ctx => {
          val value = ctx.socketExpressions.getOrElse(unarySocket.id, TurtleExpression.defaultForSocket(unarySocket))
          TurtleExpression.Unary(function, value)
        }
      ),
      sockets = List(unarySocket)
    )

  val add: TurtleBlockDefinition = binaryReporter("add", TurtleBinaryOperator.Add, "#FF8C1A", "add")
  val subtract: TurtleBlockDefinition = binaryReporter("subtract", TurtleBinaryOperator.Subtract, "#FF8C1A", "subtract")
  val multiply: TurtleBlockDefinition = binaryReporter("multiply", TurtleBinaryOperator.Multiply, "#FF8C1A", "multiply")
  val divide: TurtleBlockDefinition = binaryReporter("divide", TurtleBinaryOperator.Divide, "#FF8C1A", "divide")
  val modulo: TurtleBlockDefinition = binaryReporter("modulo", TurtleBinaryOperator.Modulo, "#FF8C1A", "modulo")
  val power: TurtleBlockDefinition = binaryReporter("power", TurtleBinaryOperator.Power, "#FF8C1A", "power")

  val sin: TurtleBlockDefinition = unaryReporter("sin", TurtleUnaryFunction.Sin, "#008F7A", "sin")
  val cos: TurtleBlockDefinition = unaryReporter("cos", TurtleUnaryFunction.Cos, "#008F7A", "cos")
  val tan: TurtleBlockDefinition = unaryReporter("tan", TurtleUnaryFunction.Tan, "#008F7A", "tan")
  val sqrt: TurtleBlockDefinition = unaryReporter("sqrt", TurtleUnaryFunction.Sqrt, "#008F7A", "sqrt")
  val abs: TurtleBlockDefinition = unaryReporter("abs", TurtleUnaryFunction.Abs, "#008F7A", "abs")
  val round: TurtleBlockDefinition = unaryReporter("round", TurtleUnaryFunction.Round, "#008F7A", "round")
  val floor: TurtleBlockDefinition = unaryReporter("floor", TurtleUnaryFunction.Floor, "#008F7A", "floor")
  val ceil: TurtleBlockDefinition = unaryReporter("ceil", TurtleUnaryFunction.Ceil, "#008F7A", "ceil")

  def instantiateWithCompanion(definition: TurtleBlockDefinition): List[TurtleStructuredBlock] = {
    val block = definition.createInstance()
    val socketChildren = defaultSocketChildren(definition)
    List(TurtleStructuredBlock(block, Nil, socketChildren))
  }

  def instantiateLiteral(value: Double): TurtleStructuredBlock =
    TurtleStructuredBlock(numericLiteral.createInstance(Some(value)), Nil, Map.empty)

  def instantiateBooleanLiteral(value: Boolean): TurtleStructuredBlock =
    TurtleStructuredBlock(booleanLiteral.createInstance(Some(if (value) 1.0 else 0.0)), Nil, Map.empty)

  val allBlocks: List[TurtleBlockDefinition] = List(
    whenProgramStarted,
    forward,
    turnRight,
    turnLeft,
    penUp,
    penDown,
    turnAround,
    repeat,
    ifPenState,
    whilePenState,
    numericLiteral,
    booleanLiteral,
    add,
    subtract,
    multiply,
    divide,
    modulo,
    power,
    sin,
    cos,
    tan,
    sqrt,
    abs,
    round,
    floor,
    ceil
  )

  val blocksByCategory: Map[TurtleBlockCategory, List[TurtleBlockDefinition]] =
    allBlocks.groupBy(_.category)

  private def defaultSocketChildren(definition: TurtleBlockDefinition): Map[String, List[TurtleStructuredBlock]] = {
    definition.sockets.map { socket =>
      val children: List[TurtleStructuredBlock] = socket.valueType match {
        case TurtleValueType.Numeric => List(instantiateLiteral(socket.defaultNumeric))
        case TurtleValueType.Boolean => List(instantiateBooleanLiteral(socket.defaultBoolean))
      }
      socket.id -> children.take(socket.maxChildren)
    }.toMap
  }
}
