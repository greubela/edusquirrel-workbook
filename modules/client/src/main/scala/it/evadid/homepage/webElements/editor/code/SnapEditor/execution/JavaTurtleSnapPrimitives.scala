package it.evadid.homepage.webElements.editor.code.SnapEditor.execution

import it.evadid.vm.parsing.java.turtle.JavaTurtleResolution as R
import it.evadid.vm.simulation.java.{JavaInt32, JavaTurtleEvaluation as E}
import scala.scalajs.js

object JavaTurtleSnapPrimitives {
  private val OriginalInitBlocks = "_eduJavaNumericInitBlocks"
  private val CellMarker = "_eduJavaNumericCell"
  private val binaryOperators = Map(
    "add" -> R.BinaryOperator.Add, "sub" -> R.BinaryOperator.Subtract,
    "mul" -> R.BinaryOperator.Multiply, "div" -> R.BinaryOperator.Divide,
    "rem" -> R.BinaryOperator.Remainder
  )
  private val comparisonOperators = Map(
    "lt" -> R.BinaryOperator.Less, "le" -> R.BinaryOperator.LessEqual,
    "gt" -> R.BinaryOperator.Greater, "ge" -> R.BinaryOperator.GreaterEqual,
    "eq" -> R.BinaryOperator.Equal, "ne" -> R.BinaryOperator.NotEqual
  )

  private def fail(message: String): Nothing =
    throw js.JavaScriptException(js.Dynamic.newInstance(js.Dynamic.global.Error)(message))

  private def number(value: js.Any): Double =
    if js.typeOf(value) != "number" then fail("Java arithmetic needs numeric operands.")
    else value.asInstanceOf[Double]

  private def finite(value: js.Any): Double = {
    val actual = number(value)
    if !actual.isFinite then fail("A Java turtle command needs a finite numeric value.")
    actual
  }

  private def receiver(process: js.Dynamic): js.Dynamic = {
    val target = process.applyDynamic("blockReceiver")()
    if js.isUndefined(target) || target == null then fail("A Java turtle command needs a sprite receiver.")
    target
  }

  private[execution] def forward(process: js.Dynamic, value: js.Any): Unit = {
    val distance = finite(value)
    receiver(process).applyDynamic("forward")(distance)
  }

  private[execution] def turnRight(process: js.Dynamic, value: js.Any): Unit = {
    val angle = finite(value)
    receiver(process).applyDynamic("turn")(angle)
  }

  private[execution] def reset(process: js.Dynamic): Unit = {
    val target = receiver(process)
    target.applyDynamic("up")()
    target.applyDynamic("gotoXY")(0, 0)
    target.applyDynamic("setHeading")(90)
    target.applyDynamic("setColorRGBA")(0)
    target.applyDynamic("setColorDimension")(3, 0)
    target.applyDynamic("setSize")(1)
    target.applyDynamic("clear")()
    target.applyDynamic("down")()
  }

  private def integer(value: js.Any): Int = {
    val actual = number(value)
    if !actual.isFinite || actual < Int.MinValue.toDouble || actual > Int.MaxValue.toDouble || actual != math.floor(actual) then
      fail("Java int arithmetic needs values in the int range.")
    actual.toInt
  }

  private def flag(value: js.Any): Boolean =
    if js.typeOf(value) != "boolean" then fail("Use a boolean Java operation flag.")
    else value.asInstanceOf[Boolean]

  private def operation(value: js.Any): String =
    if js.typeOf(value) != "string" then fail("Choose a supported Java arithmetic operation.")
    else value.asInstanceOf[String]

  private[execution] def cell(value: js.Any): js.Any =
    js.Dynamic.literal(_eduJavaNumericCell = true, value = number(value))

  private def cellValue(value: js.Any): Double = {
    if js.typeOf(value) != "object" || value == null then fail("Use a Java numeric variable cell.")
    val boxed = value.asInstanceOf[js.Dynamic]
    val marker = boxed.selectDynamic(CellMarker)
    if js.typeOf(marker) != "boolean" || !marker.asInstanceOf[Boolean] then fail("Use a Java numeric variable cell.")
    number(boxed.selectDynamic("value").asInstanceOf[js.Any])
  }

  private[execution] def read(process: js.Dynamic, variable: js.Any): Double = {
    val name = operation(variable)
    val boxed = process.selectDynamic("context").selectDynamic("variables").applyDynamic("getVar")(name, process)
    cellValue(boxed.asInstanceOf[js.Any])
  }

  private def result(value: Either[E.Failure, E.Value]): Double = value match {
    case Right(E.Value.IntValue(number)) => number.toDouble
    case Right(E.Value.DoubleValue(number)) => number
    case Left(E.Failure.DivisionByZero) => fail("Java integer division by zero.")
    case _ => fail("Invalid Java arithmetic operands.")
  }

  private[execution] def intOperation(operator: js.Any, left: js.Any, right: js.Any): Double = {
    val op = operation(operator)
    val a = integer(left)
    op match {
      case "pos" => a.toDouble
      case "neg" => JavaInt32.negate(a).toDouble
      case _ =>
        val binary = binaryOperators.getOrElse(op, fail("Choose a supported Java arithmetic operation."))
        result(E.arithmetic(binary, E.Value.IntValue(a), E.Value.IntValue(integer(right))))
    }
  }

  private[execution] def doubleOperation(operator: js.Any, left: js.Any, right: js.Any): Double = {
    val op = operation(operator)
    val a = number(left)
    op match {
      case "pos" => a
      case "neg" => -a
      case _ =>
        val binary = binaryOperators.getOrElse(op, fail("Choose a supported Java arithmetic operation."))
        result(E.arithmetic(binary, E.Value.DoubleValue(a), E.Value.DoubleValue(number(right))))
    }
  }

  private[execution] def compare(operator: js.Any, left: js.Any, right: js.Any, floating: js.Any): Boolean = {
    val comparison = comparisonOperators.getOrElse(operation(operator), fail("Choose a supported Java comparison operation."))
    val decimal = flag(floating)
    val a = if decimal then E.Value.DoubleValue(number(left)) else E.Value.IntValue(integer(left))
    val b = if decimal then E.Value.DoubleValue(number(right)) else E.Value.IntValue(integer(right))
    E.arithmetic(comparison, a, b) match {
      case Right(E.Value.BooleanValue(value)) => value
      case _ => fail("Invalid Java comparison operands.")
    }
  }

  private[execution] def update(process: js.Dynamic, variable: js.Any, delta: js.Any,
      prefix: js.Any, floating: js.Any): Double = {
    val name = operation(variable)
    val change = integer(delta)
    if name.isEmpty || (change != 1 && change != -1) then fail("Increment or decrement a named Java variable.")
    val before = flag(prefix)
    val decimal = flag(floating)
    val old = read(process, name)
    val next = if decimal then doubleOperation("add", old, change) else intOperation("add", old, change)
    process.applyDynamic("doSetVar")(name, cell(next))
    if before then next else old
  }

  private def installSpecs(prototype: js.Dynamic): Unit = {
    val blocks = prototype.selectDynamic("blocks")
    if js.isUndefined(blocks) || blocks == null then return
    blocks.updateDynamic("reportJavaInt")(js.Dynamic.literal(
      `type` = "reporter", reports = "number", category = "operators", spec = "Java int %s %n %n",
      defaults = js.Array[js.Any]("add", 0, 0)))
    blocks.updateDynamic("reportJavaDouble")(js.Dynamic.literal(
      `type` = "reporter", reports = "number", category = "operators", spec = "Java double %s %n %n",
      defaults = js.Array[js.Any]("add", 0, 0)))
    blocks.updateDynamic("reportJavaUpdate")(js.Dynamic.literal(
      `type` = "reporter", reports = "number", category = "variables", spec = "Java update %s %n %b %b",
      defaults = js.Array[js.Any]("", 1, false, false)))
    blocks.updateDynamic("reportJavaCompare")(js.Dynamic.literal(
      `type` = "predicate", category = "operators", spec = "Java compare %s %n %n %b",
      defaults = js.Array[js.Any]("eq", 0, 0, false)))
    blocks.updateDynamic("reportJavaCell")(js.Dynamic.literal(
      `type` = "reporter", reports = "number", category = "variables", spec = "Java numeric cell %n",
      defaults = js.Array[js.Any](0)))
    blocks.updateDynamic("reportJavaRead")(js.Dynamic.literal(
      `type` = "reporter", reports = "number", category = "variables", spec = "Java numeric variable %s",
      defaults = js.Array[js.Any]("")))
    blocks.updateDynamic("doJavaForward")(js.Dynamic.literal(
      `type` = "command", category = "motion", spec = "Java forward %n", defaults = js.Array[js.Any](10)))
    blocks.updateDynamic("doJavaTurnRight")(js.Dynamic.literal(
      `type` = "command", category = "motion", spec = "Java turn right %n", defaults = js.Array[js.Any](90)))
    blocks.updateDynamic("doJavaReset")(js.Dynamic.literal(
      `type` = "command", category = "motion", spec = "Java reset turtle"))
  }

  def install(): Unit = {
    val globals = js.Dynamic.global.globalThis
    val process = globals.selectDynamic("Process")
    val sprite = globals.selectDynamic("SpriteMorph")
    if js.isUndefined(process) || process == null || js.isUndefined(sprite) || sprite == null then return
    val processPrototype = process.selectDynamic("prototype")
    val spritePrototype = sprite.selectDynamic("prototype")
    val intReporter: js.ThisFunction3[js.Dynamic, js.Any, js.Any, js.Any, Double] =
      (_: js.Dynamic, operator: js.Any, left: js.Any, right: js.Any) => intOperation(operator, left, right)
    val doubleReporter: js.ThisFunction3[js.Dynamic, js.Any, js.Any, js.Any, Double] =
      (_: js.Dynamic, operator: js.Any, left: js.Any, right: js.Any) => doubleOperation(operator, left, right)
    val updateReporter: js.ThisFunction4[js.Dynamic, js.Any, js.Any, js.Any, js.Any, Double] =
      (self: js.Dynamic, name: js.Any, delta: js.Any, prefix: js.Any, floating: js.Any) => update(self, name, delta, prefix, floating)
    val comparisonReporter: js.ThisFunction4[js.Dynamic, js.Any, js.Any, js.Any, js.Any, Boolean] =
      (_: js.Dynamic, operator: js.Any, left: js.Any, right: js.Any, floating: js.Any) => compare(operator, left, right, floating)
    val cellReporter: js.ThisFunction1[js.Dynamic, js.Any, js.Any] = (_: js.Dynamic, value: js.Any) => cell(value)
    val readReporter: js.ThisFunction1[js.Dynamic, js.Any, Double] = (self: js.Dynamic, name: js.Any) => read(self, name)
    val forwardCommand: js.ThisFunction1[js.Dynamic, js.Any, Unit] = (self: js.Dynamic, value: js.Any) => forward(self, value)
    val turnCommand: js.ThisFunction1[js.Dynamic, js.Any, Unit] = (self: js.Dynamic, value: js.Any) => turnRight(self, value)
    val resetCommand: js.ThisFunction0[js.Dynamic, Unit] = (self: js.Dynamic) => reset(self)
    processPrototype.updateDynamic("reportJavaInt")(intReporter)
    processPrototype.updateDynamic("reportJavaDouble")(doubleReporter)
    processPrototype.updateDynamic("reportJavaUpdate")(updateReporter)
    processPrototype.updateDynamic("reportJavaCompare")(comparisonReporter)
    processPrototype.updateDynamic("reportJavaCell")(cellReporter)
    processPrototype.updateDynamic("reportJavaRead")(readReporter)
    processPrototype.updateDynamic("doJavaForward")(forwardCommand)
    processPrototype.updateDynamic("doJavaTurnRight")(turnCommand)
    processPrototype.updateDynamic("doJavaReset")(resetCommand)
    val stored = spritePrototype.selectDynamic(OriginalInitBlocks)
    val original = spritePrototype.selectDynamic("initBlocks")
    if (js.isUndefined(stored) || stored == null) && js.typeOf(original) == "function" then {
      spritePrototype.updateDynamic(OriginalInitBlocks)(original)
      val wrapped: js.ThisFunction0[js.Dynamic, js.Any] = (self: js.Dynamic) => {
        val value = original.applyDynamic("call")(self)
        installSpecs(spritePrototype)
        value.asInstanceOf[js.Any]
      }
      spritePrototype.updateDynamic("initBlocks")(wrapped)
    }
    installSpecs(spritePrototype)
  }
}
