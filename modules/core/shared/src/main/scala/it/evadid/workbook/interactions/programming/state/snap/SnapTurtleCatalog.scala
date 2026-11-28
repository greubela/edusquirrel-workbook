package it.evadid.workbook.interactions.programming.state.snap

import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules

import upickle.default.ReadWriter

/**
 * Canonical Snap selector ↔ Python name ↔ TurtleCommand mapping.
 *
 * Aliases (`right`/`turn`, `penup`/`up`, `goto`/`gotoXY`) are resolved in both
 * directions so Apply in the Python overlay and Snap XML stay aligned.
 */
object SnapTurtleCatalog {

  enum PaletteTab derives ReadWriter:
    case Motion, Pen, Embroidery, Control, Operators, Variables, Other

  enum SnapInputKind derives ReadWriter:
    case Numeric, String, Bool, Color

  final case class ExtraBlockSpec(
      spec: String,
      category: String,
      defaults: Seq[ujson.Value] = Nil
  ) derives upickle.default.ReadWriter {
    require(defaults.forall {
      case ujson.Num(value) => value.isFinite
      case _: ujson.Str | _: ujson.Bool => true
      case _ => false
    }, "Snap defaults must be finite JSON numbers, strings or booleans")
  }

  /**
   * @param extraPrimitive true when vanilla Snap has no `SpriteMorph.blocks` entry
   *                       (embroidery, circle, home, backward) and the editor must inject one.
   */
  final case class Primitive(
      snapSelector: String,
      pythonName: String,
      aliases: Seq[String] = Nil,
      turtleCommand: String,
      example: String,
      tab: PaletteTab,
      extraPrimitive: Boolean = false,
      extraSpec: Option[ExtraBlockSpec] = None,
      inputKinds: Seq[SnapInputKind] = Nil
  ) derives upickle.default.ReadWriter {
    def allPythonNames: Seq[String] = pythonName +: aliases
    def arity: Int = inputKinds.size
  }

  /** A primitive vanilla Snap lacks: the editor injects a block with `spec` into `category`. */
  private def injected(snapSelector: String, pythonName: String, aliases: Seq[String], turtleCommand: String,
      example: String, tab: PaletteTab, spec: String, category: String, defaults: Seq[ujson.Value],
      inputKinds: Seq[SnapInputKind]): Primitive =
    Primitive(snapSelector, pythonName, aliases, turtleCommand, example, tab, extraPrimitive = true,
      extraSpec = Some(ExtraBlockSpec(spec, category, defaults)), inputKinds = inputKinds)

  /** An injected embroidery primitive whose Python name is also its turtle command. */
  private def embroidery(snapSelector: String, pythonName: String, example: String, spec: String,
      defaults: Seq[ujson.Value], inputKinds: Seq[SnapInputKind]): Primitive =
    injected(snapSelector, pythonName, Nil, pythonName, example, PaletteTab.Embroidery, spec, "embroidery", defaults, inputKinds)

  import SnapInputKind.{Bool, Color, Numeric}

  private val none: Seq[SnapInputKind] = Nil
  private val n: Seq[SnapInputKind] = List(Numeric)
  private val nn: Seq[SnapInputKind] = List(Numeric, Numeric)
  private val nnb: Seq[SnapInputKind] = List(Numeric, Numeric, Bool)
  private val nb: Seq[SnapInputKind] = List(Numeric, Bool)
  private val b: Seq[SnapInputKind] = List(Bool)
  private val c: Seq[SnapInputKind] = List(Color)

  val Primitives: Seq[Primitive] = List(
    Primitive("receiveGo", "receive_go", Nil, "receive_go", "receive_go()", PaletteTab.Control, inputKinds = none),
    Primitive("doWait", "do_wait", Nil, "do_wait", "do_wait(seconds)", PaletteTab.Control, inputKinds = n),
    Primitive("forward", "forward", List("fd"), "forward", "forward(steps)", PaletteTab.Motion, inputKinds = n),
    Primitive("turn", "turn_right", List("turn", "right", "rt"), "right", "turn_right(degrees)", PaletteTab.Motion, inputKinds = n),
    Primitive("turnLeft", "turn_left", List("left", "lt"), "left", "turn_left(degrees)", PaletteTab.Motion, inputKinds = n),
    Primitive("gotoXY", "goto_x_y", List("goto", "setpos", "setposition"), "goto", "goto_x_y(x, y)", PaletteTab.Motion, inputKinds = nn),
    Primitive("setHeading", "set_heading", List("setheading", "seth"), "setheading", "set_heading(degrees)", PaletteTab.Motion, inputKinds = n),
    Primitive("setXPosition", "set_x", List("setx"), "setx", "set_x(x)", PaletteTab.Motion, inputKinds = n),
    Primitive("setYPosition", "set_y", List("sety"), "sety", "set_y(y)", PaletteTab.Motion, inputKinds = n),
    injected("backward", "backward", List("back", "bk"), "backward", "backward(steps)", PaletteTab.Motion,
      "move back %n steps", "motion", List(10), n),
    injected("home", "home", Nil, "home", "home()", PaletteTab.Motion, "go to origin", "motion", Nil, none),
    injected("circle", "circle", Nil, "circle", "circle(radius)", PaletteTab.Motion, "circle radius %n", "motion", List(50), n),
    Primitive("clear", "clear", List("clearscreen"), "clear", "clear()", PaletteTab.Pen, inputKinds = none),
    Primitive("down", "down", List("pendown", "pd", "pen_down"), "pendown", "down()", PaletteTab.Pen, inputKinds = none),
    Primitive("up", "up", List("penup", "pu", "pen_up"), "penup", "up()", PaletteTab.Pen, inputKinds = none),
    Primitive("setColor", "color", List("pencolor"), "color", "color(value)", PaletteTab.Pen, inputKinds = c),
    Primitive("setSize", "pensize", Nil, "pensize", "pensize(width)", PaletteTab.Pen, inputKinds = n),
    embroidery("runningStitch", "running_stitch", "running_stitch(steps)", "running stitch by %n steps", List(10), n),
    embroidery("crossStitch", "cross_stitch", "cross_stitch(size, size, center)",
      "cross stitch in %n by %n center %b", List(10, 10, true), nnb),
    embroidery("beanStitch", "bean_stitch", "bean_stitch(length)", "triple run by %n", List(10), n),
    embroidery("zigzagStitch", "zigzag_stitch", "zigzag_stitch(density, width, center)",
      "zigzag with density %n width %n center %b", List(20, 20, true), nnb),
    embroidery("ZStitch", "z_stitch", "z_stitch(density, width, center)",
      "Z-stitch with density %n width %n center %b", List(20, 10, true), nnb),
    embroidery("satinStitch", "satin_stitch", "satin_stitch(width, center)",
      "satin stitch with width %n center %b", List(20, true), nb),
    embroidery("tatamiStitch", "tatami_stitch", "tatami_stitch(width, interval, center)",
      "tatami stitch width %n interval %n center %b", List(100, 40, true), nnb),
    embroidery("jumpStitch", "jump_stitch", "jump_stitch(enabled)", "jump stitch %b", List(true), b),
    embroidery("tieStitch", "tie_stitch", "tie_stitch()", "tie stitch", Nil, none),
    injected("trimStitch", "trim_stitch", List("trim"), "trim", "trim_stitch()", PaletteTab.Embroidery, "trim", "embroidery", Nil, none),
    embroidery("stopRunning", "stop_running", "stop_running()", "stop running", Nil, none)
  )

  /** Library blocks with a shared intrinsic but no turtle semantics: printing becomes Snap's `say`
    * bubble and `fetch_text` its `url` reporter. They map selectors and names like turtle primitives,
    * but take no part in the turtle palette or in turtle command observation.
    */
  val LibraryPrimitives: Seq[Primitive] = List(
    Primitive("bubble", "print", List("println"), "print", "print(message)", PaletteTab.Other, inputKinds = List(SnapInputKind.String)),
    Primitive("reportURL", "fetch_text", Nil, "fetch_text", "fetch_text(url)", PaletteTab.Other, inputKinds = List(SnapInputKind.String))
  )

  private val SnapBlocks: Seq[Primitive] = Primitives ++ LibraryPrimitives

  val ExtraPrimitives: Seq[Primitive] = Primitives.filter(_.extraPrimitive)

  val AllowedPythonNames: Set[String] =
    Primitives.flatMap(_.allPythonNames).toSet

  val AllowedSnapSelectors: Set[String] =
    SnapBlocks.map(_.snapSelector).toSet

  val snapSelectorByPythonName: Map[String, String] =
    SnapBlocks.flatMap { primitive =>
      primitive.allPythonNames.map(_ -> primitive.snapSelector)
    }.toMap

  val turtleCommandByPythonName: Map[String, String] =
    Primitives.flatMap { primitive =>
      primitive.allPythonNames.map(_ -> primitive.turtleCommand)
    }.toMap

  val pythonNameBySnapSelector: Map[String, String] =
    SnapBlocks.map(p => p.snapSelector -> p.pythonName).toMap

  val primitiveBySnapSelector: Map[String, Primitive] =
    SnapBlocks.map(p => p.snapSelector -> p).toMap

  val primitiveByPythonName: Map[String, Primitive] =
    SnapBlocks.flatMap { primitive =>
      primitive.allPythonNames.map(_ -> primitive)
    }.toMap

  def primitivesForTab(tab: PaletteTab): Seq[Primitive] =
    Primitives.filter(_.tab == tab)

  def inputKindsForSelector(selector: String): Seq[SnapInputKind] =
    primitiveBySnapSelector.get(selector).map(_.inputKinds).getOrElse(Nil)

  def canonicalPythonName(snapSelectorOrSpec: String): String =
    pythonNameBySnapSelector.getOrElse(
      snapSelectorOrSpec,
      if snapSelectorOrSpec.contains("%") || snapSelectorOrSpec.contains(" ") then
        pythonNameFromCustomSpec(snapSelectorOrSpec)
      else snapSelectorOrSpec
    )

  /**
   * Python function name for a Snap block spec: every label word joined by `_`.
   *
   * All words are needed, not just the first: `draw square %size` and
   * `draw circle %radius` would otherwise both become `draw` and collapse into one
   * Python function.
   */
  def pythonNameFromCustomSpec(spec: String): String = {
    val words = SnapCustomBlockRules
      .parseSpec(spec)
      .filterNot(part => part.startsWith("%") && part.length > 1)
      .map(word => word.filter(ch => ch.isLetterOrDigit || ch == '_'))
      .filter(_.nonEmpty)
    if words.isEmpty then "custom"
    else
      val joined = words.mkString("_")
      if joined.head.isDigit then s"_$joined" else joined
  }

  /**
   * Definition spec for a block Python declares itself: label word plus one
   * `%inputName` per parameter. Snap stores parameter *names* in the definition
   * spec and their declared types only in `<inputs>`.
   */
  def customBlockSemanticSpec(pythonName: String, inputNames: Seq[String]): String =
    pythonName + uniqueSlotNames(inputNames).map(name => s" %$name").mkString

  def inputNamesFromSpec(spec: String): Seq[String] =
    SnapCustomBlockRules.slotNames(spec)

  def slotNameFromPython(name: String): String = {
    val trimmed = name.trim
    if trimmed.nonEmpty && !trimmed.exists(ch => ch.isWhitespace || ch == '%') then trimmed
    else "n"
  }

  /** Valid slot names, numbering repeats from 2 (`n`, `n2`, ...) while skipping names already taken. */
  def uniqueSlotNames(names: Seq[String]): Seq[String] =
    names.foldLeft((Vector.empty[String], Set.empty[String])) { case ((unique, used), raw) =>
      val base = slotNameFromPython(raw)
      val name = if !used(base) then base else Iterator.from(2).map(i => s"$base$i").find(!used(_)).get
      (unique :+ name, used + name)
    }._1

  def arityFromSpec(spec: String): Int =
    SnapCustomBlockRules.slotNames(spec).size

  def validateCallArity(pythonName: String, actual: Int): Option[String] =
    primitiveByPythonName.get(pythonName).flatMap { primitive =>
      if primitive.arity == actual then None
      else Some(s"$pythonName expects ${primitive.arity} argument(s), got $actual")
    }
}
