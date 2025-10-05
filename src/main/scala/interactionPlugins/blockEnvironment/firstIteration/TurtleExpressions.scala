package interactionPlugins.blockEnvironment.firstIteration

sealed trait TurtleExpression {
  def evaluate: Double
  def render: String
}

object TurtleExpression {

  case class Literal(value: Double) extends TurtleExpression {
    override def evaluate: Double = sanitize(value)
    override def render: String = sanitize(value).formatted("%.2f").replaceAll("\\.00$", "")
  }

  case class BooleanLiteral(value: Boolean) extends TurtleExpression {
    override def evaluate: Double = if (value) 1.0 else 0.0
    override def render: String = if (value) "true" else "false"
  }

  case class Unary(function: TurtleUnaryFunction, child: TurtleExpression) extends TurtleExpression {
    override def evaluate: Double = sanitize(function.apply(child.evaluate))
    override def render: String = s"${function.renderName}(${child.render})"
  }

  case class Binary(function: TurtleBinaryOperator, left: TurtleExpression, right: TurtleExpression) extends TurtleExpression {
    override def evaluate: Double = sanitize(function.apply(left.evaluate, right.evaluate))
    override def render: String = s"(${left.render} ${function.symbol} ${right.render})"
  }

  private def sanitize(value: Double): Double =
    if (value.isNaN || value.isInfinity) 0.0 else value
}

enum TurtleBinaryOperator(val symbol: String, val displayName: String) {
  case Add extends TurtleBinaryOperator("+", "add")
  case Subtract extends TurtleBinaryOperator("-", "subtract")
  case Multiply extends TurtleBinaryOperator("×", "multiply")
  case Divide extends TurtleBinaryOperator("÷", "divide")
  case Modulo extends TurtleBinaryOperator("mod", "mod")
  case Power extends TurtleBinaryOperator("^", "power")

  def apply(left: Double, right: Double): Double = this match {
    case Add      => left + right
    case Subtract => left - right
    case Multiply => left * right
    case Divide   => if (math.abs(right) < 1e-9) 0.0 else left / right
    case Modulo   => if (math.abs(right) < 1e-9) 0.0 else left % right
    case Power    => math.pow(left, right)
  }
}

enum TurtleUnaryFunction(val renderName: String, val displayName: String) {
  case Sin extends TurtleUnaryFunction("sin", "sin")
  case Cos extends TurtleUnaryFunction("cos", "cos")
  case Tan extends TurtleUnaryFunction("tan", "tan")
  case Sqrt extends TurtleUnaryFunction("sqrt", "sqrt")
  case Abs extends TurtleUnaryFunction("abs", "abs")
  case Round extends TurtleUnaryFunction("round", "round")
  case Floor extends TurtleUnaryFunction("floor", "floor")
  case Ceil extends TurtleUnaryFunction("ceil", "ceil")

  def apply(value: Double): Double = this match {
    case Sin   => math.sin(math.toRadians(value))
    case Cos   => math.cos(math.toRadians(value))
    case Tan   => math.tan(math.toRadians(value))
    case Sqrt  => if (value < 0) 0.0 else math.sqrt(value)
    case Abs   => math.abs(value)
    case Round => math.round(value).toDouble
    case Floor => math.floor(value)
    case Ceil  => math.ceil(value)
  }
}
