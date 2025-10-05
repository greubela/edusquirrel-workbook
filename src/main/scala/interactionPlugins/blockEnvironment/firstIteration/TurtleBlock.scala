package interactionPlugins.blockEnvironment.firstIteration

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.color.RGBColor
import util.IdHelper

enum TurtleDataType(val color: RGBColor) {
  case Numeric extends TurtleDataType(RGBColor.darkGreen)
  case Boolean extends TurtleDataType(RGBColor.green)
  case String extends TurtleDataType(RGBColor.yellow)
  case Unit extends TurtleDataType(RGBColor.white)
  case Date extends TurtleDataType(RGBColor.red)
}

sealed trait TurtleConnectionKind
object TurtleConnectionKind {
  case object Stack extends TurtleConnectionKind
  case object Enclosed extends TurtleConnectionKind
  case object Parameter extends TurtleConnectionKind
}

sealed trait TurtleConnectionLayout
object TurtleConnectionLayout {
  case class Vertical(gap: Double = 8.0, inset: Double = 0.0) extends TurtleConnectionLayout
  case class Inline(gap: Double = 6.0) extends TurtleConnectionLayout
}

trait TurtleBlockArea {
  def distanceToArea(x: Double, y: Double): Double
  def highlightArea(): L.SvgElement
  def asSvg(): L.SvgElement
  def translate(dx: Double, dy: Double): TurtleBlockArea
}

case class TurtleRectangleArea(x: Double, y: Double, width: Double, height: Double) extends TurtleBlockArea {

  override def distanceToArea(px: Double, py: Double): Double = {
    val inside = px >= x && px <= x + width && py >= y && py <= y + height
    if (inside) 0.0
    else {
      val dx =
        if (px < x) x - px
        else if (px > x + width) px - (x + width)
        else 0.0
      val dy =
        if (py < y) y - py
        else if (py > y + height) py - (y + height)
        else 0.0
      math.sqrt(dx * dx + dy * dy)
    }
  }

  override def highlightArea(): L.SvgElement =
    svg.rect(
      svg.x := x.toString,
      svg.y := y.toString,
      svg.width := width.toString,
      svg.height := height.toString,
      svg.rx := "6",
      svg.ry := "6",
      svg.fill := "rgba(255, 128, 0, 0.25)",
      svg.stroke := "#ff8000",
      svg.strokeDashArray := "6 4"
    )

  override def asSvg(): L.SvgElement =
    svg.rect(
      svg.x := x.toString,
      svg.y := y.toString,
      svg.width := width.toString,
      svg.height := height.toString,
      svg.fill := "rgba(0,0,0,0)",
      svg.stroke := "rgba(0,0,0,0)"
    )

  override def translate(dx: Double, dy: Double): TurtleRectangleArea =
    copy(x = x + dx, y = y + dy)

  def withHeight(newHeight: Double): TurtleRectangleArea = copy(height = newHeight)
  def withWidth(newWidth: Double): TurtleRectangleArea = copy(width = newWidth)
}

case class TurtleBlockConnection(
  id: String,
  acceptTypes: Set[TurtleDataType],
  area: TurtleRectangleArea,
  layout: TurtleConnectionLayout,
  kind: TurtleConnectionKind,
  maxChildren: Int = 1,
  placeholderLabel: Option[String] = None,
  placeholderColor: Option[String] = None,
  defaultChildren: () => List[TurtleStructuredBlock] = () => Nil
)

object TurtleBlockShape {

  enum Outline {
    case Rounded, Capsule, Hexagon
  }
}

case class TurtleBlockShape(
  width: Double,
  minHeight: Double,
  headerHeight: Double,
  footerHeight: Double,
  insidePadding: Double,
  backgroundColor: String,
  borderColor: String = "#0c3359",
  textColor: String = "white",
  cornerRadius: Double = 10.0,
  outline: TurtleBlockShape.Outline = TurtleBlockShape.Outline.Rounded
) {
  val bodyWidth: Double = math.max(0.0, width - insidePadding * 2)

  def insideTop: Double = headerHeight

  def computeHeight(contentHeight: Double): Double =
    math.max(minHeight, headerHeight + contentHeight + footerHeight)

  def render(label: String, height: Double, labelYOffset: Double = 0.0): L.SvgElement = {
    val labelY = (headerHeight / 2.0) + labelYOffset
    val outlineElement: L.SvgElement = outline match {
      case TurtleBlockShape.Outline.Hexagon =>
        val cut = math.min(cornerRadius, math.min(width / 4.0, height / 2.0))
        val points = Seq(
          (cut, 0.0),
          (width - cut, 0.0),
          (width, height / 2.0),
          (width - cut, height),
          (cut, height),
          (0.0, height / 2.0)
        ).map { case (x, y) => f"$x%.2f,$y%.2f" }.mkString(" ")
        svg.polygon(
          svg.points := points,
          svg.fill := backgroundColor,
          svg.stroke := borderColor,
          svg.strokeWidth := "2"
        )
      case other =>
        val radius = other match {
          case TurtleBlockShape.Outline.Capsule =>
            val maxRadius = math.min(width / 2.0, height / 2.0)
            math.min(maxRadius, math.max(cornerRadius, 0.0))
          case _ => cornerRadius
        }
        svg.rect(
          svg.x := "0",
          svg.y := "0",
          svg.width := width.toString,
          svg.height := height.toString,
          svg.rx := radius.toString,
          svg.ry := radius.toString,
          svg.fill := backgroundColor,
          svg.stroke := borderColor,
          svg.strokeWidth := "2"
        )
    }
    svg.g(
      outlineElement,
      svg.text(
        svg.x := insidePadding.toString,
        svg.y := labelY.toString,
        svg.fill := textColor,
        svg.fontSize := "14",
        svg.fontWeight := "600",
        svg.textAnchor := "start",
        svg.alignmentBaseline := "middle",
        label
      )
    )
  }
}

enum TurtleBlockCategory {
  case Control, Motion, Pen, Logic, Reporter, Operators, Math
}

case class TurtleBlockContext(
  block: TurtleBlock,
  connectionExpressions: Map[String, TurtleExpression]
)

sealed trait TurtleBlockBehaviour

object TurtleBlockBehaviour {
  case class Command(build: TurtleBlockContext => TurtleCommand) extends TurtleBlockBehaviour

  case class Reporter(valueType: TurtleDataType, build: TurtleBlockContext => TurtleExpression) extends TurtleBlockBehaviour
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
  shape: TurtleBlockShape,
  labelForValue: Option[Double] => String,
  defaultValue: Option[Double],
  behaviour: TurtleBlockBehaviour,
  connections: List[TurtleBlockConnection] = Nil,
  closingCommand: Option[TurtleCommand] = None,
  sanitizeValueFn: Option[Double] => Option[Double] = identity
) {

  private val connectionIndex: Map[String, TurtleBlockConnection] = connections.map(c => c.id -> c).toMap

  def evaluatesTo: TurtleDataType = behaviour match {
    case TurtleBlockBehaviour.Command(_)          => TurtleDataType.Unit
    case TurtleBlockBehaviour.Reporter(valueType, _) => valueType
  }

  def connection(id: String): Option[TurtleBlockConnection] = connectionIndex.get(id)

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
    case None      => default
  }
}

object TurtleBlock {
  def newId(): String = IdHelper.getNextId()
}

object TurtleBlockLibrary {

  private val baseCommandWidth = 220.0
  private val reporterSlotWidth = 96.0
  private val reporterBaseMinHeight = 40.0
  private val reporterHeaderHeight = 32.0
  private val reporterFooterHeight = 8.0

  private val numericReporterColor = "#FFAB19"
  private val numericReporterBorder = "#C57F00"
  private val booleanReporterColor = "#0E9D57"
  private val booleanReporterBorder = "#0A6B3F"

  private val hatShape = TurtleBlockShape(
    width = baseCommandWidth,
    minHeight = 60,
    headerHeight = 60,
    footerHeight = 16,
    insidePadding = 18,
    backgroundColor = "#4C97FF"
  )

  private val stackShape = TurtleBlockShape(
    width = baseCommandWidth,
    minHeight = 48,
    headerHeight = 48,
    footerHeight = 16,
    insidePadding = 18,
    backgroundColor = "#9966FF"
  )

  private val penShape = stackShape.copy(backgroundColor = "#FF8C1A")

  private val cShape = TurtleBlockShape(
    width = baseCommandWidth + 20,
    minHeight = 80,
    headerHeight = 52,
    footerHeight = 28,
    insidePadding = 22,
    backgroundColor = "#00A99D"
  )

  private val reporterShape = TurtleBlockShape(
    width = reporterSlotWidth,
    minHeight = reporterBaseMinHeight,
    headerHeight = reporterHeaderHeight,
    footerHeight = reporterFooterHeight,
    insidePadding = 16,
    backgroundColor = numericReporterColor,
    borderColor = numericReporterBorder,
    cornerRadius = 18,
    outline = TurtleBlockShape.Outline.Capsule
  )

  private val reporterSlotHeight: Double = reporterShape.computeHeight(0.0)

  private val defaultStackConnection: TurtleBlockConnection =
    TurtleBlockConnection(
      id = "stack",
      acceptTypes = Set(TurtleDataType.Unit),
      area = TurtleRectangleArea(0, stackShape.minHeight + 8, stackShape.width, 0),
      layout = TurtleConnectionLayout.Vertical(),
      kind = TurtleConnectionKind.Stack,
      maxChildren = Int.MaxValue
    )

  private def stackConnection(shape: TurtleBlockShape): TurtleBlockConnection =
    defaultStackConnection.copy(area = TurtleRectangleArea(0, shape.minHeight + 8, shape.width, 0))

  private def enclosedConnection(shape: TurtleBlockShape, id: String = "inside"): TurtleBlockConnection =
    TurtleBlockConnection(
      id = id,
      acceptTypes = Set(TurtleDataType.Unit),
      area = TurtleRectangleArea(shape.insidePadding, shape.headerHeight, shape.bodyWidth, 0),
      layout = TurtleConnectionLayout.Vertical(),
      kind = TurtleConnectionKind.Enclosed,
      maxChildren = Int.MaxValue
    )

  private def numericParameter(
    id: String,
    label: String,
    shape: TurtleBlockShape,
    xOffset: Double,
    yOffset: Double,
    width: Double,
    height: Double,
    placeholderColor: String,
    defaultValue: Double
  ): TurtleBlockConnection =
    TurtleBlockConnection(
      id = id,
      acceptTypes = Set(TurtleDataType.Numeric),
      area = TurtleRectangleArea(shape.width - xOffset - width, yOffset, width, height),
      layout = TurtleConnectionLayout.Inline(),
      kind = TurtleConnectionKind.Parameter,
      maxChildren = 1,
      placeholderLabel = Some(label),
      placeholderColor = Some(placeholderColor),
      defaultChildren = () => List(instantiateLiteral(defaultValue))
    )

  private def booleanParameter(
    id: String,
    label: String,
    shape: TurtleBlockShape,
    xOffset: Double,
    yOffset: Double,
    width: Double,
    height: Double,
    placeholderColor: String,
    defaultValue: Boolean
  ): TurtleBlockConnection =
    TurtleBlockConnection(
      id = id,
      acceptTypes = Set(TurtleDataType.Boolean),
      area = TurtleRectangleArea(shape.width - xOffset - width, yOffset, width, height),
      layout = TurtleConnectionLayout.Inline(),
      kind = TurtleConnectionKind.Parameter,
      maxChildren = 1,
      placeholderLabel = Some(label),
      placeholderColor = Some(placeholderColor),
      defaultChildren = () => List(instantiateBooleanLiteral(defaultValue))
    )

  val whenProgramStarted: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "whenProgramStarted",
    category = TurtleBlockCategory.Control,
    shape = hatShape,
    labelForValue = _ => "When program started",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command(_ => TurtleCommand.WhenProgramStarted),
    connections = List(stackConnection(hatShape))
  )

  private val distanceParam = numericParameter(
    id = "distance",
    label = "steps",
    shape = stackShape,
    xOffset = stackShape.insidePadding,
    yOffset = 8,
    width = reporterSlotWidth,
    height = reporterSlotHeight,
    placeholderColor = numericReporterColor,
    defaultValue = 100
  )

  val forward: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "forward",
    category = TurtleBlockCategory.Motion,
    shape = stackShape,
    labelForValue = _ => "forward",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      val distance = ctx.connectionExpressions.getOrElse(distanceParam.id, TurtleExpression.Literal(100.0))
      TurtleCommand.Forward(distance)
    },
    connections = List(stackConnection(stackShape), distanceParam)
  )

  private val angleParam = numericParameter(
    id = "angle",
    label = "degrees",
    shape = stackShape,
    xOffset = stackShape.insidePadding,
    yOffset = 8,
    width = reporterSlotWidth,
    height = reporterSlotHeight,
    placeholderColor = numericReporterColor,
    defaultValue = 90
  )

  val turnRight: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "turnRight",
    category = TurtleBlockCategory.Motion,
    shape = stackShape.copy(backgroundColor = "#FF6680"),
    labelForValue = _ => "turn right",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      val angle = ctx.connectionExpressions.getOrElse(angleParam.id, TurtleExpression.Literal(90.0))
      TurtleCommand.TurnRight(angle)
    },
    connections = List(stackConnection(stackShape), angleParam)
  )

  val turnLeft: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "turnLeft",
    category = TurtleBlockCategory.Motion,
    shape = stackShape.copy(backgroundColor = "#FF6680"),
    labelForValue = _ => "turn left",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      val angle = ctx.connectionExpressions.getOrElse(angleParam.id, TurtleExpression.Literal(90.0))
      TurtleCommand.TurnLeft(angle)
    },
    connections = List(stackConnection(stackShape), angleParam)
  )

  val penUp: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "penUp",
    category = TurtleBlockCategory.Pen,
    shape = penShape,
    labelForValue = _ => "pen up",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command(_ => TurtleCommand.PenUp),
    connections = List(stackConnection(penShape))
  )

  val penDown: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "penDown",
    category = TurtleBlockCategory.Pen,
    shape = penShape.copy(backgroundColor = "#FFBF00"),
    labelForValue = _ => "pen down",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command(_ => TurtleCommand.PenDown),
    connections = List(stackConnection(penShape.copy(backgroundColor = "#FFBF00")))
  )

  val turnAround: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "turnAround",
    category = TurtleBlockCategory.Motion,
    shape = stackShape.copy(backgroundColor = "#00B295"),
    labelForValue = _ => "turn around",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command(_ => TurtleCommand.TurnAround),
    connections = List(stackConnection(stackShape.copy(backgroundColor = "#00B295")))
  )

  private val repeatParam = numericParameter(
    id = "times",
    label = "times",
    shape = cShape,
    xOffset = cShape.insidePadding,
    yOffset = 12,
    width = reporterSlotWidth,
    height = reporterSlotHeight,
    placeholderColor = numericReporterColor,
    defaultValue = 4
  )

  val repeat: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "repeat",
    category = TurtleBlockCategory.Logic,
    shape = cShape,
    labelForValue = _ => "repeat",
    defaultValue = None,
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      val times = ctx.connectionExpressions.getOrElse(repeatParam.id, TurtleExpression.Literal(4.0))
      TurtleCommand.Repeat(times)
    },
    connections = List(stackConnection(cShape), enclosedConnection(cShape), repeatParam),
    closingCommand = Some(TurtleCommand.EndRepeat)
  )

  val ifPenState: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "ifPenState",
    category = TurtleBlockCategory.Logic,
    shape = cShape.copy(backgroundColor = "#3FBBAD"),
    labelForValue = value => if (value.exists(_ >= 0.5)) "if pen is down" else "if pen is up",
    defaultValue = Some(1.0),
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      TurtleCommand.IfPenState(ctx.block.booleanValue(default = true))
    },
    connections = List(stackConnection(cShape.copy(backgroundColor = "#3FBBAD")), enclosedConnection(cShape.copy(backgroundColor = "#3FBBAD"))),
    closingCommand = Some(TurtleCommand.EndIf),
    sanitizeValueFn = value => Some(if (value.exists(_ >= 0.5)) 1.0 else 0.0)
  )

  val whilePenState: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "whilePenState",
    category = TurtleBlockCategory.Logic,
    shape = cShape.copy(backgroundColor = "#0D9E8A"),
    labelForValue = value => if (value.exists(_ >= 0.5)) "while pen is down" else "while pen is up",
    defaultValue = Some(1.0),
    behaviour = TurtleBlockBehaviour.Command { ctx =>
      TurtleCommand.WhilePenState(ctx.block.booleanValue(default = true))
    },
    connections = List(stackConnection(cShape.copy(backgroundColor = "#0D9E8A")), enclosedConnection(cShape.copy(backgroundColor = "#0D9E8A"))),
    closingCommand = Some(TurtleCommand.EndWhile),
    sanitizeValueFn = value => Some(if (value.exists(_ >= 0.5)) 1.0 else 0.0)
  )

  val numericLiteral: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "numericLiteral",
    category = TurtleBlockCategory.Reporter,
    shape = reporterShape,
    labelForValue = value => value.map(v => s"${v.formatted("%.1f").replaceAll("\\.0$", "")}").getOrElse("number"),
    defaultValue = Some(0.0),
    behaviour = TurtleBlockBehaviour.Reporter(TurtleDataType.Numeric, ctx => TurtleExpression.Literal(ctx.block.numericValue())),
    connections = Nil,
    sanitizeValueFn = value => value.map(v => if (v.isNaN || v.isInfinity) 0.0 else v)
  )

  val booleanLiteral: TurtleBlockDefinition = TurtleBlockDefinition(
    key = "booleanLiteral",
    category = TurtleBlockCategory.Reporter,
    shape = reporterShape.copy(
      backgroundColor = booleanReporterColor,
      borderColor = booleanReporterBorder,
      outline = TurtleBlockShape.Outline.Hexagon
    ),
    labelForValue = value => if (value.exists(_ >= 0.5)) "true" else "false",
    defaultValue = Some(1.0),
    behaviour = TurtleBlockBehaviour.Reporter(TurtleDataType.Boolean, ctx => TurtleExpression.BooleanLiteral(ctx.block.booleanValue(default = true))),
    connections = Nil,
    sanitizeValueFn = value => Some(if (value.exists(_ >= 0.5)) 1.0 else 0.0)
  )

  private def binaryReporter(
    key: String,
    operator: TurtleBinaryOperator,
    label: String
  ): TurtleBlockDefinition = {
    val shape = reporterShape.copy(
      width = reporterSlotWidth * 2 + reporterShape.insidePadding * 2 + 24,
      backgroundColor = numericReporterColor,
      borderColor = numericReporterBorder
    )
    val slotY = (shape.minHeight - reporterSlotHeight) / 2
    val leftArea = TurtleRectangleArea(shape.insidePadding, slotY, reporterSlotWidth, reporterSlotHeight)
    val rightArea = TurtleRectangleArea(shape.width - shape.insidePadding - reporterSlotWidth, slotY, reporterSlotWidth, reporterSlotHeight)
    val leftConnection = TurtleBlockConnection(
      id = "left",
      acceptTypes = Set(TurtleDataType.Numeric),
      area = leftArea,
      layout = TurtleConnectionLayout.Inline(),
      kind = TurtleConnectionKind.Parameter,
      maxChildren = 1,
      placeholderLabel = Some("left"),
      placeholderColor = Some(numericReporterColor),
      defaultChildren = () => List(instantiateLiteral(0.0))
    )
    val rightConnection = TurtleBlockConnection(
      id = "right",
      acceptTypes = Set(TurtleDataType.Numeric),
      area = rightArea,
      layout = TurtleConnectionLayout.Inline(),
      kind = TurtleConnectionKind.Parameter,
      maxChildren = 1,
      placeholderLabel = Some("right"),
      placeholderColor = Some(numericReporterColor),
      defaultChildren = () => List(instantiateLiteral(0.0))
    )
    TurtleBlockDefinition(
      key = key,
      category = TurtleBlockCategory.Operators,
      shape = shape,
      labelForValue = _ => label,
      defaultValue = None,
      behaviour = TurtleBlockBehaviour.Reporter(
        TurtleDataType.Numeric,
        ctx => {
          val left = ctx.connectionExpressions.getOrElse(leftConnection.id, TurtleExpression.Literal(0.0))
          val right = ctx.connectionExpressions.getOrElse(rightConnection.id, TurtleExpression.Literal(0.0))
          TurtleExpression.Binary(operator, left, right)
        }
      ),
      connections = List(leftConnection, rightConnection)
    )
  }

  private def unaryReporter(
    key: String,
    function: TurtleUnaryFunction,
    label: String
  ): TurtleBlockDefinition = {
    val shape = reporterShape.copy(
      width = reporterSlotWidth + reporterShape.insidePadding * 2 + 24,
      backgroundColor = numericReporterColor,
      borderColor = numericReporterBorder
    )
    val slotX = shape.width - shape.insidePadding - reporterSlotWidth
    val slotY = (shape.minHeight - reporterSlotHeight) / 2
    val valueConnection = TurtleBlockConnection(
      id = "value",
      acceptTypes = Set(TurtleDataType.Numeric),
      area = TurtleRectangleArea(slotX, slotY, reporterSlotWidth, reporterSlotHeight),
      layout = TurtleConnectionLayout.Inline(),
      kind = TurtleConnectionKind.Parameter,
      maxChildren = 1,
      placeholderLabel = Some("value"),
      placeholderColor = Some(numericReporterColor),
      defaultChildren = () => List(instantiateLiteral(0.0))
    )
    TurtleBlockDefinition(
      key = key,
      category = TurtleBlockCategory.Math,
      shape = shape,
      labelForValue = _ => label,
      defaultValue = None,
      behaviour = TurtleBlockBehaviour.Reporter(
        TurtleDataType.Numeric,
        ctx => {
          val value = ctx.connectionExpressions.getOrElse(valueConnection.id, TurtleExpression.Literal(0.0))
          TurtleExpression.Unary(function, value)
        }
      ),
      connections = List(valueConnection)
    )
  }

  val add: TurtleBlockDefinition = binaryReporter("add", TurtleBinaryOperator.Add, "add")
  val subtract: TurtleBlockDefinition = binaryReporter("subtract", TurtleBinaryOperator.Subtract, "subtract")
  val multiply: TurtleBlockDefinition = binaryReporter("multiply", TurtleBinaryOperator.Multiply, "multiply")
  val divide: TurtleBlockDefinition = binaryReporter("divide", TurtleBinaryOperator.Divide, "divide")
  val modulo: TurtleBlockDefinition = binaryReporter("modulo", TurtleBinaryOperator.Modulo, "modulo")
  val power: TurtleBlockDefinition = binaryReporter("power", TurtleBinaryOperator.Power, "power")

  val sin: TurtleBlockDefinition = unaryReporter("sin", TurtleUnaryFunction.Sin, "sin")
  val cos: TurtleBlockDefinition = unaryReporter("cos", TurtleUnaryFunction.Cos, "cos")
  val tan: TurtleBlockDefinition = unaryReporter("tan", TurtleUnaryFunction.Tan, "tan")
  val sqrt: TurtleBlockDefinition = unaryReporter("sqrt", TurtleUnaryFunction.Sqrt, "sqrt")
  val abs: TurtleBlockDefinition = unaryReporter("abs", TurtleUnaryFunction.Abs, "abs")
  val round: TurtleBlockDefinition = unaryReporter("round", TurtleUnaryFunction.Round, "round")
  val floor: TurtleBlockDefinition = unaryReporter("floor", TurtleUnaryFunction.Floor, "floor")
  val ceil: TurtleBlockDefinition = unaryReporter("ceil", TurtleUnaryFunction.Ceil, "ceil")

  def instantiateWithCompanion(definition: TurtleBlockDefinition): List[TurtleStructuredBlock] = {
    val block = definition.createInstance()
    val connectionChildren = definition.connections.collect {
      case connection if connection.kind != TurtleConnectionKind.Stack =>
        val children = connection.defaultChildren().take(connection.maxChildren)
        connection.id -> children
    }.toMap
    List(TurtleStructuredBlock(block, connectionChildren))
  }

  def instantiateLiteral(value: Double): TurtleStructuredBlock =
    TurtleStructuredBlock(numericLiteral.createInstance(Some(value)), Map.empty)

  def instantiateBooleanLiteral(value: Boolean): TurtleStructuredBlock =
    TurtleStructuredBlock(booleanLiteral.createInstance(Some(if (value) 1.0 else 0.0)), Map.empty)

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
}
