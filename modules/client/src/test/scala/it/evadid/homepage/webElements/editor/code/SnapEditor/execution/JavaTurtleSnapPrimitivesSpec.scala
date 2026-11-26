package it.evadid.homepage.webElements.editor.code.SnapEditor.execution

import com.raquo.airstream.state.Var
import munit.FunSuite
import scala.scalajs.js

/** The Java blocks installed into Snap's Process and SpriteMorph prototypes, with Snap's globals stood in for. */
class JavaTurtleSnapPrimitivesSpec extends FunSuite {
  private val globals = js.Dynamic.global.globalThis
  private val calls = Var(Vector.empty[String])
  /** A sprite that records the turtle calls it receives. */
  private def sprite: js.Dynamic = {
    def log(name: String, args: js.Any*): Unit = calls.update(_ :+ s"$name(${args.mkString(",")})")
    def r0(name: String): js.Function0[Unit] = () => log(name)
    def r1(name: String): js.Function1[js.Any, Unit] = a => log(name, a)
    def r2(name: String): js.Function2[js.Any, js.Any, Unit] = (a, b) => log(name, a, b)
    js.Dynamic.literal(forward = r1("forward"), turn = r1("turn"), up = r0("up"), down = r0("down"),
      gotoXY = r2("gotoXY"), setHeading = r1("setHeading"), setColorRGBA = r1("setColorRGBA"),
      setColorDimension = r2("setColorDimension"), setSize = r1("setSize"), clear = r0("clear"))
  }
  private def withSnap[T](body: (js.Dynamic, js.Dynamic) => T): T = {
    val previous = List("Process", "SpriteMorph").map(name => name -> globals.selectDynamic(name))
    val process = js.Dynamic.literal(prototype = js.Dynamic.literal())
    val initialized = Var(0)
    val spritePrototype = js.Dynamic.literal(blocks = js.Dynamic.literal())
    spritePrototype.updateDynamic("initBlocks")((((self: js.Dynamic) => {
      initialized.update(_ + 1)
      self.updateDynamic("blocks")(js.Dynamic.literal())
      "initialized"
    }): js.ThisFunction0[js.Dynamic, String]))
    globals.updateDynamic("Process")(process)
    globals.updateDynamic("SpriteMorph")(js.Dynamic.literal(prototype = spritePrototype))
    try body(process.prototype, spritePrototype)
    finally previous.foreach((name, value) => globals.updateDynamic(name)(value))
  }
  private def call(target: js.Dynamic, name: String, self: js.Any, args: js.Any*): js.Dynamic =
    target.selectDynamic(name).applyDynamic("apply")(self, js.Array(args*))

  test("installing adds the Java blocks to Snap's palette and keeps them after Snap rebuilds its blocks") {
    withSnap { (process, spritePrototype) =>
      JavaTurtleSnapPrimitives.install()
      val names = js.Object.keys(spritePrototype.blocks.asInstanceOf[js.Object]).toList
      assertEquals(names.sorted, List("doJavaForward", "doJavaReset", "doJavaTurnRight", "reportJavaCell", "reportJavaCompare",
        "reportJavaDouble", "reportJavaInt", "reportJavaRead", "reportJavaUpdate"))
      assertEquals(spritePrototype.blocks.reportJavaCompare.selectDynamic("type").asInstanceOf[String], "predicate")
      assertEquals(spritePrototype.blocks.doJavaForward.spec.asInstanceOf[String], "Java forward %n")
      val wrapped = spritePrototype.initBlocks
      JavaTurtleSnapPrimitives.install()
      assert(spritePrototype.initBlocks eq wrapped, "installing twice wraps Snap's initialisation once")
      assertEquals(call(spritePrototype, "initBlocks", spritePrototype).asInstanceOf[String], "initialized")
      assert(!js.isUndefined(spritePrototype.blocks.reportJavaInt), "rebuilt blocks include the Java blocks again")

      assertEquals(call(process, "reportJavaInt", null, "mul", 6, 7).asInstanceOf[Double], 42.0)
      assertEquals(call(process, "reportJavaDouble", null, "div", 1, 4).asInstanceOf[Double], 0.25)
      assertEquals(call(process, "reportJavaDouble", null, "neg", 2.5, 0).asInstanceOf[Double], -2.5)
      assertEquals(call(process, "reportJavaDouble", null, "pos", 2.5, 0).asInstanceOf[Double], 2.5)
      assertEquals(call(process, "reportJavaInt", null, "pos", 3, 0).asInstanceOf[Double], 3.0)
      assert(call(process, "reportJavaCompare", null, "ge", 3, 3, false).asInstanceOf[Boolean])
      val cell = call(process, "reportJavaCell", null, 5)
      assertEquals(cell.value.asInstanceOf[Double], 5.0)
    }
  }

  test("turtle commands move the receiving sprite and reject non-finite values or a missing sprite") {
    withSnap { (process, _) =>
      JavaTurtleSnapPrimitives.install()
      calls.set(Vector.empty)
      val turtle = sprite
      val running = js.Dynamic.literal(blockReceiver = (() => turtle): js.Function0[js.Dynamic])
      call(process, "doJavaForward", running, 25)
      call(process, "doJavaTurnRight", running, 90)
      call(process, "doJavaReset", running)
      assertEquals(calls.now(), Vector("forward(25)", "turn(90)", "up()", "gotoXY(0,0)", "setHeading(90)", "setColorRGBA(0)",
        "setColorDimension(3,0)", "setSize(1)", "clear()", "down()"))
      val messages = List[() => Any](
        () => call(process, "doJavaForward", running, Double.PositiveInfinity),
        () => call(process, "doJavaTurnRight", running, "90"),
        () => call(process, "doJavaForward", js.Dynamic.literal(blockReceiver = (() => null): js.Function0[Null]), 10),
        () => call(process, "reportJavaDouble", null, 3, 1, 2),
        () => call(process, "reportJavaDouble", null, "pow", 1, 2),
        () => call(process, "reportJavaCompare", null, "eq", 1, 2, "yes")
      ).map(run => intercept[js.JavaScriptException](run()).exception.asInstanceOf[js.Dynamic].message.asInstanceOf[String])
      assertEquals(messages, List("A Java turtle command needs a finite numeric value.", "Java arithmetic needs numeric operands.",
        "A Java turtle command needs a sprite receiver.", "Choose a supported Java arithmetic operation.",
        "Choose a supported Java arithmetic operation.", "Use a boolean Java operation flag."))
      assertEquals(calls.now().size, 10, "rejected commands do not move the sprite")
    }
  }

  test("numeric variables must be Java cells; updates need a name and a step of one") {
    val stored = Var[js.Any](js.Dynamic.literal(value = 3))
    val process = js.Dynamic.literal(
      context = js.Dynamic.literal(variables = js.Dynamic.literal(getVar = ((_: String, _: js.Any) => stored.now()): js.Function2[String, js.Any, js.Any])),
      doSetVar = ((_: String, value: js.Any) => stored.set(value)): js.Function2[String, js.Any, Unit])
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.read(process, "n"))
    stored.set(null)
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.read(process, "n"))
    stored.set(JavaTurtleSnapPrimitives.cell(1.5))
    assertEquals(JavaTurtleSnapPrimitives.update(process, "n", 1, true, true), 2.5, "doubles step by one")
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.update(process, "", 1, true, true))
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.update(process, "n", 2, true, true))
    assertEquals(JavaTurtleSnapPrimitives.read(process, "n"), 2.5)
  }

  test("without Snap's process class installing changes nothing") {
    withSnap { (_, spritePrototype) =>
      globals.updateDynamic("Process")(js.undefined)
      val initBlocks = spritePrototype.initBlocks
      JavaTurtleSnapPrimitives.install()
      assertEquals(js.Object.keys(spritePrototype.blocks.asInstanceOf[js.Object]).toList, Nil)
      assert(spritePrototype.initBlocks eq initBlocks)
    }
  }

  test("typed Java comparisons retain NaN infinity and signed zero semantics") {
    for op <- Seq("lt", "le", "gt", "ge", "eq") do {
      assert(!JavaTurtleSnapPrimitives.compare(op, Double.NaN, 1.0, true), clue = op)
      assert(!JavaTurtleSnapPrimitives.compare(op, 1.0, Double.NaN, true), clue = op)
      assert(!JavaTurtleSnapPrimitives.compare(op, Double.NaN, Double.NaN, true), clue = op)
    }
    assert(JavaTurtleSnapPrimitives.compare("ne", Double.NaN, Double.NaN, true))
    for op <- Seq("eq", "le", "ge") do assert(JavaTurtleSnapPrimitives.compare(op, 0.0, -0.0, true), clue = op)
    assert(JavaTurtleSnapPrimitives.compare("eq", Double.PositiveInfinity, Double.PositiveInfinity, true))
    assert(JavaTurtleSnapPrimitives.compare("lt", Double.NegativeInfinity, Double.PositiveInfinity, true))
    assert(JavaTurtleSnapPrimitives.compare("lt", Int.MinValue, Int.MaxValue, false))
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.compare("eq", true, 1, false))
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.compare("eq", true, 1.0, true))
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.compare("eq", 0.5, 1, false))
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.compare("eq", 1, 1, 0))
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.compare("eval", 1, 1, false))
  }

  test("native Java numeric operations retain int32 and IEEE semantics") {
    val native = JavaTurtleSnapPrimitives
    assertEquals(native.intOperation("add", Int.MaxValue, 1), Int.MinValue.toDouble)
    assertEquals(native.intOperation("mul", Int.MaxValue, Int.MaxValue), 1.0)
    assertEquals(native.intOperation("neg", Int.MinValue, 0), Int.MinValue.toDouble)
    assertEquals(native.intOperation("div", -7, 3), -2.0)
    assertEquals(native.intOperation("rem", -7, 3), -1.0)
    assertEquals(native.intOperation("div", Int.MinValue, -1), Int.MinValue.toDouble)
    intercept[js.JavaScriptException](native.intOperation("div", 1, 0))
    intercept[js.JavaScriptException](native.intOperation("add", true, 1))
    intercept[js.JavaScriptException](native.intOperation("add", 0.5, 1))
    intercept[js.JavaScriptException](native.intOperation("eval", 1, 2))
    assertEquals(native.doubleOperation("div", 1.0, -0.0), Double.NegativeInfinity)
    assert(native.doubleOperation("div", 0.0, 0.0).isNaN)
    assertEquals(native.doubleOperation("rem", -7.5, 2.0), -1.5)
    intercept[js.JavaScriptException](native.doubleOperation("add", false, 1.0))
  }

  test("native Java updates read and write the active lexical variable once") {
    var current: js.Any = JavaTurtleSnapPrimitives.cell(Int.MaxValue)
    var writes = 0
    val get: js.Function2[String, js.Any, js.Any] = (name: String, owner: js.Any) => {
      assertEquals(name, "n")
      current
    }
    val set: js.Function2[String, js.Any, Unit] = (name: String, value: js.Any) => {
      assertEquals(name, "n")
      writes += 1
      current = value
    }
    val process = js.Dynamic.literal(context = js.Dynamic.literal(variables = js.Dynamic.literal(getVar = get)), doSetVar = set)
    assertEquals(JavaTurtleSnapPrimitives.update(process, "n", 1, false, false), Int.MaxValue.toDouble)
    assertEquals(JavaTurtleSnapPrimitives.read(process, "n"), Int.MinValue.toDouble)
    assertEquals(JavaTurtleSnapPrimitives.update(process, "n", -1, true, false), Int.MaxValue.toDouble)
    assertEquals(writes, 2)
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.update(process, "n", true, false, false))
    current = false
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.update(process, "n", 1, false, false))
    assertEquals(writes, 2)
  }

  test("native Java numeric cells preserve NaN and signed zero across Snap variable binding") {
    def normalized(value: js.Any): js.Any =
      if js.typeOf(value) == "number" && value.asInstanceOf[Double] == 0.0 then 0
      else if js.typeOf(value) == "boolean" && !value.asInstanceOf[Boolean] then false
      else if js.typeOf(value) == "string" && value.asInstanceOf[String].isEmpty then ""
      else if js.Dynamic.global.Boolean(value).asInstanceOf[Boolean] then value
      else 0
    assertEquals(normalized(Double.NaN).asInstanceOf[Double], 0.0)
    assertEquals(1.0 / normalized(-0.0).asInstanceOf[Double], Double.PositiveInfinity)
    var current: js.Any = normalized(JavaTurtleSnapPrimitives.cell(Double.NaN))
    var writes = 0
    val get: js.Function2[String, js.Any, js.Any] = (_: String, _: js.Any) => normalized(current)
    val set: js.Function2[String, js.Any, Unit] = (_: String, value: js.Any) => { current = value; writes += 1 }
    val process = js.Dynamic.literal(context = js.Dynamic.literal(variables = js.Dynamic.literal(getVar = get)), doSetVar = set)
    assert(JavaTurtleSnapPrimitives.read(process, "n").isNaN)
    assert(JavaTurtleSnapPrimitives.update(process, "n", 1, false, true).isNaN)
    assert(JavaTurtleSnapPrimitives.read(process, "n").isNaN)
    current = normalized(JavaTurtleSnapPrimitives.cell(-0.0))
    assertEquals(1.0 / JavaTurtleSnapPrimitives.read(process, "n"), Double.NegativeInfinity)
    val previous = JavaTurtleSnapPrimitives.update(process, "n", 1, false, true)
    assertEquals(1.0 / previous, Double.NegativeInfinity)
    assertEquals(JavaTurtleSnapPrimitives.read(process, "n"), 1.0)
    assertEquals(writes, 2)
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.cell(false))
    current = 0
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.read(process, "n"))
    current = js.Dynamic.literal(value = 1)
    intercept[js.JavaScriptException](JavaTurtleSnapPrimitives.read(process, "n"))
  }
}
