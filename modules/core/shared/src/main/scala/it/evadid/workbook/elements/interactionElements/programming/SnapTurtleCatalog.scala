package it.evadid.workbook.elements.interactionElements.programming

/**
 * Canonical Snap selector ↔ Python name ↔ TurtleCommand mapping.
 *
 * Aliases (`right`/`turn`, `penup`/`up`, `goto`/`gotoXY`) are resolved in both
 * directions so Apply in the Python overlay and Snap XML stay aligned.
 */
object SnapTurtleCatalog {

  enum PaletteTab:
    case Motion, Pen, Embroidery, Control, Operators, Variables, Other

  enum SnapInputKind:
    case Numeric, String, Bool, Color

  final case class ExtraBlockSpec(
      spec: String,
      category: String,
      defaults: List[Any] = Nil
  )

  /**
   * @param extraPrimitive true when vanilla Snap has no `SpriteMorph.blocks` entry
   *                       (embroidery, circle, home, backward) and the editor must inject one.
   */
  final case class Primitive(
      snapSelector: String,
      pythonName: String,
      aliases: List[String] = Nil,
      turtleCommand: String,
      example: String,
      tab: PaletteTab,
      extraPrimitive: Boolean = false,
      extraSpec: Option[ExtraBlockSpec] = None,
      inputKinds: List[SnapInputKind] = Nil
  ) {
    def allPythonNames: List[String] = pythonName :: aliases
    def arity: Int = inputKinds.size
  }

  private def extra(spec: String, category: String, defaults: List[Any] = Nil): ExtraBlockSpec =
    ExtraBlockSpec(spec, category, defaults)

  import SnapInputKind.{Bool, Color, Numeric}

  private val none: List[SnapInputKind] = Nil
  private val n: List[SnapInputKind] = List(Numeric)
  private val nn: List[SnapInputKind] = List(Numeric, Numeric)
  private val nnb: List[SnapInputKind] = List(Numeric, Numeric, Bool)
  private val nb: List[SnapInputKind] = List(Numeric, Bool)
  private val b: List[SnapInputKind] = List(Bool)
  private val c: List[SnapInputKind] = List(Color)

  val Primitives: List[Primitive] = List(
    Primitive("receiveGo", "receive_go", Nil, "receive_go", "receive_go()", PaletteTab.Control, inputKinds = none),
    Primitive("doWait", "do_wait", Nil, "do_wait", "do_wait(seconds)", PaletteTab.Control, inputKinds = n),
    Primitive("forward", "forward", List("fd"), "forward", "forward(steps)", PaletteTab.Motion, inputKinds = n),
    Primitive("turn", "turn", List("right", "rt"), "right", "turn(degrees)", PaletteTab.Motion, inputKinds = n),
    Primitive("turnLeft", "turn_left", List("left", "lt"), "left", "turn_left(degrees)", PaletteTab.Motion, inputKinds = n),
    Primitive("gotoXY", "goto_x_y", List("goto", "setpos", "setposition"), "goto", "goto_x_y(x, y)", PaletteTab.Motion, inputKinds = nn),
    Primitive("setHeading", "set_heading", List("setheading", "seth"), "setheading", "set_heading(degrees)", PaletteTab.Motion, inputKinds = n),
    Primitive("setXPosition", "set_x", List("setx"), "setx", "set_x(x)", PaletteTab.Motion, inputKinds = n),
    Primitive("setYPosition", "set_y", List("sety"), "sety", "set_y(y)", PaletteTab.Motion, inputKinds = n),
    Primitive(
      "backward",
      "backward",
      List("back", "bk"),
      "backward",
      "backward(steps)",
      PaletteTab.Motion,
      extraPrimitive = true,
      extraSpec = Some(extra("move back %n steps", "motion", List(10))),
      inputKinds = n
    ),
    Primitive(
      "home",
      "home",
      Nil,
      "home",
      "home()",
      PaletteTab.Motion,
      extraPrimitive = true,
      extraSpec = Some(extra("go to origin", "motion")),
      inputKinds = none
    ),
    Primitive(
      "circle",
      "circle",
      Nil,
      "circle",
      "circle(radius)",
      PaletteTab.Motion,
      extraPrimitive = true,
      extraSpec = Some(extra("circle radius %n", "motion", List(50))),
      inputKinds = n
    ),
    Primitive("clear", "clear", List("clearscreen"), "clear", "clear()", PaletteTab.Pen, inputKinds = none),
    Primitive("down", "down", List("pendown", "pd", "pen_down"), "pendown", "down()", PaletteTab.Pen, inputKinds = none),
    Primitive("up", "up", List("penup", "pu", "pen_up"), "penup", "up()", PaletteTab.Pen, inputKinds = none),
    Primitive("setColor", "color", List("pencolor"), "color", "color(value)", PaletteTab.Pen, inputKinds = c),
    Primitive("setSize", "pensize", Nil, "pensize", "pensize(width)", PaletteTab.Pen, inputKinds = n),
    Primitive(
      "runningStitch",
      "running_stitch",
      Nil,
      "running_stitch",
      "running_stitch(steps)",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("running stitch by %n steps", "embroidery", List(10))),
      inputKinds = n
    ),
    Primitive(
      "crossStitch",
      "cross_stitch",
      Nil,
      "cross_stitch",
      "cross_stitch(size, size, center)",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("cross stitch in %n by %n center %b", "embroidery", List(10, 10, true))),
      inputKinds = nnb
    ),
    Primitive(
      "beanStitch",
      "bean_stitch",
      Nil,
      "bean_stitch",
      "bean_stitch(length)",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("triple run by %n", "embroidery", List(10))),
      inputKinds = n
    ),
    Primitive(
      "zigzagStitch",
      "zigzag_stitch",
      Nil,
      "zigzag_stitch",
      "zigzag_stitch(density, width, center)",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("zigzag with density %n width %n center %b", "embroidery", List(20, 20, true))),
      inputKinds = nnb
    ),
    Primitive(
      "ZStitch",
      "z_stitch",
      Nil,
      "z_stitch",
      "z_stitch(density, width, center)",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("Z-stitch with density %n width %n center %b", "embroidery", List(20, 10, true))),
      inputKinds = nnb
    ),
    Primitive(
      "satinStitch",
      "satin_stitch",
      Nil,
      "satin_stitch",
      "satin_stitch(width, center)",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("satin stitch with width %n center %b", "embroidery", List(20, true))),
      inputKinds = nb
    ),
    Primitive(
      "tatamiStitch",
      "tatami_stitch",
      Nil,
      "tatami_stitch",
      "tatami_stitch(width, interval, center)",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("tatami stitch width %n interval %n center %b", "embroidery", List(100, 40, true))),
      inputKinds = nnb
    ),
    Primitive(
      "jumpStitch",
      "jump_stitch",
      Nil,
      "jump_stitch",
      "jump_stitch(enabled)",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("jump stitch %b", "embroidery", List(true))),
      inputKinds = b
    ),
    Primitive(
      "tieStitch",
      "tie_stitch",
      Nil,
      "tie_stitch",
      "tie_stitch()",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("tie stitch", "embroidery")),
      inputKinds = none
    ),
    Primitive(
      "trimStitch",
      "trim_stitch",
      List("trim"),
      "trim",
      "trim_stitch()",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("trim", "embroidery")),
      inputKinds = none
    ),
    Primitive(
      "stopRunning",
      "stop_running",
      Nil,
      "stop_running",
      "stop_running()",
      PaletteTab.Embroidery,
      extraPrimitive = true,
      extraSpec = Some(extra("stop running", "embroidery")),
      inputKinds = none
    )
  )

  val ExtraPrimitives: List[Primitive] = Primitives.filter(_.extraPrimitive)

  val AllowedPythonNames: Set[String] =
    Primitives.flatMap(_.allPythonNames).toSet

  val AllowedSnapSelectors: Set[String] =
    Primitives.map(_.snapSelector).toSet

  val snapSelectorByPythonName: Map[String, String] =
    Primitives.flatMap { primitive =>
      primitive.allPythonNames.map(_ -> primitive.snapSelector)
    }.toMap

  val turtleCommandBySnapSelector: Map[String, String] =
    Primitives.map(p => p.snapSelector -> p.turtleCommand).toMap

  val turtleCommandByPythonName: Map[String, String] =
    Primitives.flatMap { primitive =>
      primitive.allPythonNames.map(_ -> primitive.turtleCommand)
    }.toMap

  val pythonNameBySnapSelector: Map[String, String] =
    Primitives.map(p => p.snapSelector -> p.pythonName).toMap

  val primitiveBySnapSelector: Map[String, Primitive] =
    Primitives.map(p => p.snapSelector -> p).toMap

  val primitiveByPythonName: Map[String, Primitive] =
    Primitives.flatMap { primitive =>
      primitive.allPythonNames.map(_ -> primitive)
    }.toMap

  val expectedArityByPythonName: Map[String, Int] =
    primitiveByPythonName.map((name, primitive) => name -> primitive.arity)

  val expectedArityBySnapSelector: Map[String, Int] =
    primitiveBySnapSelector.map((selector, primitive) => selector -> primitive.arity)

  def primitivesForTab(tab: PaletteTab): List[Primitive] =
    Primitives.filter(_.tab == tab)

  def inputKindsForSelector(selector: String): List[SnapInputKind] =
    primitiveBySnapSelector.get(selector).map(_.inputKinds).getOrElse(Nil)

  def canonicalPythonName(snapSelectorOrSpec: String): String =
    pythonNameBySnapSelector.getOrElse(
      snapSelectorOrSpec,
      if snapSelectorOrSpec.contains("%") || snapSelectorOrSpec.contains(" ") then
        pythonNameFromCustomSpec(snapSelectorOrSpec)
      else snapSelectorOrSpec
    )

  def pythonNameFromCustomSpec(spec: String): String = {
    val name = spec.trim.takeWhile(ch => ch.isLetterOrDigit || ch == '_')
    if name.isEmpty then "custom" else name
  }

  /**
   * Snap stores definition `s` as a semantic spec (`square %size`) and call `s`
   * as `CustomBlockDefinition.blockSpec()` (`square %n`). Python apply must
   * emit that pair or Snap loads the call as `Undefined!`.
   */
  def customBlockSemanticSpec(pythonName: String, inputNames: List[String]): String =
    pythonName + uniqueSlotNames(inputNames).map(name => s" %$name").mkString

  /** Type spec used on `<custom-block s>` and by Snap's `blockSpec()`. */
  def customBlockTypeSpec(pythonName: String, arity: Int): String =
    pythonName + (" %n" * arity)

  def customBlockSpec(pythonName: String, arity: Int): String =
    customBlockTypeSpec(pythonName, arity)

  def inputNamesFromSpec(spec: String): List[String] =
    spec.split(' ').toList.collect {
      case part if part.startsWith("%") && part.length > 1 => part.substring(1)
    }

  /** Snap `blockSpec()`: each `%name` slot becomes its declared type (`%n` for Python). */
  def typeSpecFromSemantic(semanticSpec: String): String =
    customBlockTypeSpec(pythonNameFromCustomSpec(semanticSpec), arityFromSpec(semanticSpec))

  def slotNameFromPython(name: String): String = {
    val trimmed = name.trim
    if trimmed.nonEmpty && !trimmed.exists(ch => ch.isWhitespace || ch == '%') then trimmed
    else "n"
  }

  def uniqueSlotNames(names: List[String]): List[String] = {
    val used = scala.collection.mutable.LinkedHashSet.empty[String]
    names.map { raw =>
      val base = slotNameFromPython(raw)
      if used.add(base) then base
      else Iterator.from(2).map(i => s"$base$i").find(used.add).get
    }
  }

  def arityFromSpec(spec: String): Int =
    raw"%[A-Za-z]+".r.findAllIn(spec).length

  def validateCallArity(pythonName: String, actual: Int): Option[String] =
    primitiveByPythonName.get(pythonName).flatMap { primitive =>
      if primitive.arity == actual then None
      else Some(s"$pythonName expects ${primitive.arity} argument(s), got $actual")
    }
}
