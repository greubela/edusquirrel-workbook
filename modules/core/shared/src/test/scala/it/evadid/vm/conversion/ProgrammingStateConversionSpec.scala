package it.evadid.vm.conversion

import it.evadid.vm.compilation.java.turtle.JavaTurtleCompiler

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.vm.conversion.pythonsnap.PythonToSnap
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.vm.conversion.snapxml.SnapXmlParser
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*

import it.evadid.vm.compilation.java.turtle.{JavaTurtleSymbolResolver as R, JavaTurtleSourceParser, JavaTurtleProgram as P}
import it.evadid.vm.simulation.{BeSimulatorConfig, BeSimulatorState, BeVirtualMachineState}
import it.evadid.vm.simulation.java.JavaTurtleInterpreter as T
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import munit.FunSuite

class ProgrammingStateConversionSpec extends FunSuite {
  private val python = "forward(10)\nturn_right(90)\nforward(5)\n"

  private def squareJava(side: Int = 25, forward: String = "side", turn: Int = 90): String =
    s"""public class Main {
       |    static void square(int side) {
       |        for (int i = 0; i < 4; i += 1) {
       |            Turtle.forward($forward);
       |            Turtle.turnRight($turn);
       |        }
       |    }
       |    public static void main(String[] args) {
       |        square($side);
       |    }
       |}
       |""".stripMargin

  private def squareCommands(side: Int): Vector[T.Command] =
    Vector.fill(4)(Vector(T.Command(R.TurtleCommand.Forward, side), T.Command(R.TurtleCommand.TurnRight, 90))).flatten

  private val roundTripScenarios = List(
    ("recursive branching",
      """def branch(length, depth):
        |    if depth == 0:
        |        forward(length)
        |    else:
        |        branch(length / 2, depth - 1)
        |        turn_left(45)
        |        branch(length / 2, depth - 1)
        |
        |branch(64, 3)
        |""".stripMargin, true),
    ("sum of primes",
      """def sum_primes_below(limit):
        |    total = 0
        |    candidate = 2
        |    while candidate < limit:
        |        divisor = 2
        |        is_prime = True
        |        while divisor * divisor <= candidate:
        |            if candidate % divisor == 0:
        |                is_prime = False
        |            divisor = divisor + 1
        |        if is_prime:
        |            total = total + candidate
        |        candidate = candidate + 1
        |    print(total)
        |
        |sum_primes_below(30)
        |""".stripMargin, false)
  )

  private def normalized(state: ProgrammingState): String = state.toPython.code.trim

  /** Snap saves project variables as well as scripts, so compare execution across that boundary. */
  private def assertAllRoundTrips(source: String, includeJava: Boolean = true)(assertSnap: String => Unit = _ => ()): Unit = {
    val expression = ProgrammingStateRaw(Python, source).toBeExpression
    val expected = normalized(expression)
    val snap = expression.toSnapXml

    assertSnap(snap.code)

    val baseConversions = List[(String, ProgrammingState)](
      "BeExpression -> Python -> BeExpression" -> expression.toPython.toBeExpression,
      "BeExpression -> Snap -> BeExpression" -> snap.toBeExpression,
      "Python -> Snap -> Python" -> ProgrammingStateRaw(Python, source).toSnapXml.toPython,
      "Snap -> Python -> Snap" -> snap.toPython.toSnapXml
    )
    val javaConversions = if includeJava then {
      val java = expression.toJava
      List[(String, ProgrammingState)](
        "BeExpression -> Java -> BeExpression" -> java.toBeExpression,
        "Python -> Java -> Python" -> ProgrammingStateRaw(Python, source).toJava.toPython
      )
    } else Nil

    (baseConversions ++ javaConversions).foreach { (route, result) =>
      if route.contains("Snap") then
        assertEquals(result.toBeExpression.turtleCommandExecutionInfo.completedCommands,
          expression.turtleCommandExecutionInfo.completedCommands, clue = route)
      else assertEquals(normalized(result), expected, clue = route)
    }

    val serializableStates = List[ProgrammingState](expression, expression.toPython, snap) ++
      (if includeJava then List(expression.toJava) else Nil)
    serializableStates.foreach { state =>
      val restored = ProgrammingState.serializer.deserialize(
        ProgrammingState.serializer.serialize(state)
      )
      assertEquals(normalized(restored), normalized(state), clue = s"serialized ${state.getClass.getSimpleName}")
    }
  }

  test("one immutable state shares conversion and turtle derivations across consumers") {
    val source = ProgrammingStateRaw(Python, "forward(7)")
    assert(source.toBeExpression eq source.toBeExpression)
    assert(source.toJava eq source.toJava)
    assert(source.toSnapXml eq source.toSnapXml)
    assert(source.toBeExpression.turtleCommandExecutionInfo eq source.toBeExpression.turtleCommandExecutionInfo)
    assert(source.toBeExpression.turtleCommandSites eq source.toBeExpression.turtleCommandSites)
    val other = ProgrammingStateRaw(Python, "forward(7)")
    assert(!(source.derivations eq other.derivations))
    assertEquals(other.toBeExpression.turtleCommandExecutionInfo, source.toBeExpression.turtleCommandExecutionInfo)
  }

  test("unsupported program arguments cannot disappear during conversion") {
    val state = ProgrammingStateRaw(Java, "class Drawing { public static void main(String[] args) { print(args); } }")
    val before = state.code
    intercept[IllegalArgumentException](state.toPython)
    intercept[IllegalArgumentException](state.toSnapXml)
    assertEquals(state.code, before)
  }

  test("grouped arithmetic and boolean conditions keep their behavior through all target states") {
    val original = ProgrammingStateRaw(Python, "distance = (2 + 3) * 4\nif distance == 20 and not False:\n    forward(distance)\n")
    val expected = original.toBeExpression.turtleCommandExecutionInfo.completedCommands
    assertEquals(expected.flatMap(_.args), List(20.0))
    val restored = Seq(original.toJava, original.toSnapXml, original.toJava.toSnapXml.toPython,
      original.toSnapXml.toJava.toPython)
    restored.foreach(state => assertEquals(state.toBeExpression.turtleCommandExecutionInfo.completedCommands, expected))
  }

  test("user helpers and native turtle aliases retain distinct identities across languages") {
    val original = ProgrammingStateRaw(Python, "def right(distance):\n    forward(distance)\nright(7)\nrt(90)\n")
    val expected = original.toBeExpression.turtleCommandExecutionInfo.completedCommands
    assertEquals(expected.map(_.name.universalInterpretation()), List("forward", "right"))
    Seq(original.toJava, original.toSnapXml, original.toSnapXml.toPython).foreach { state =>
      assertEquals(state.toBeExpression.turtleCommandExecutionInfo.completedCommands, expected, clue = state.toString)
    }
  }

  test("for loop survives all ProgrammingState round trips and becomes a Snap repeat block") {
    assertAllRoundTrips(
      """for _ in range(4):
        |    forward(10)
        |    turn_right(90)
        |""".stripMargin,
      includeJava = false // Java reserves `_`; the Java representation cannot spell this anonymous loop variable.
    ) { xml =>
      assert(xml.contains("""s="doRepeat"""), clue = xml)
      assert(!xml.contains("""s="doFor"""), clue = xml)
    }
  }

  test("variables survive all ProgrammingState round trips") {
    assertAllRoundTrips(
      """steps = 10
        |forward(steps)
        |steps = steps + 5
        |forward(steps)
        |""".stripMargin
    ) { xml =>
      assert(xml.contains("""<variable name="steps"><l>10</l></variable>"""), clue = xml)
      assert(xml.contains("""s="doChangeVar"""), clue = xml)
      assert(xml.contains("""var="steps"""), clue = xml)
    }
  }

  test("custom functions survive all ProgrammingState round trips as custom blocks") {
    assertAllRoundTrips(
      """def line(length):
        |    forward(length)
        |
        |line(25)
        |""".stripMargin
    ) { xml =>
      assert(xml.contains("""<block-definition s="line %length"""), clue = xml)
      assert(xml.contains("""<custom-block s="line %n"""), clue = xml)
    }
  }

  test("for loop, variables, and custom functions survive all ProgrammingState round trips together") {
    assertAllRoundTrips(
      """def polygon(sides, length):
        |    angle = 360 / sides
        |    for _ in range(4):
        |        forward(length)
        |        turn_right(angle)
        |
        |side_count = 4
        |polygon(side_count, 30)
        |""".stripMargin,
      includeJava = false // Keep the anonymous range so Snap can use its simpler repeat block.
    ) { xml =>
      assert(xml.contains("""<block-definition s="polygon %sides %length"""), clue = xml)
      assert(xml.contains("""s="doRepeat"""), clue = xml)
      assert(xml.contains("""s="doSetVar"""), clue = xml)
      assert(xml.contains("""<custom-block s="polygon %n %n"""), clue = xml)
    }
  }

  roundTripScenarios.foreach { (name, source, snapSupported) =>
    test(s"$name preserves text round trips and validates Snap conversion") {
      val original = ProgrammingStateRaw(Python, source).toBeExpression
      val throughPython = original.toPython.toBeExpression
      val java = original.toJava
      val throughJava = java.toBeExpression
      assertEquals(normalized(throughPython), normalized(original))
      assertEquals(normalized(throughJava), normalized(original), clue = s"Java:\n${java.code}")
      if snapSupported then {
        val snap = original.toSnapXml
        val restoredSnap = ProgrammingState.serializer.deserialize(
          ProgrammingState.serializer.serialize(snap)
        ).toSnapXml
        assertEquals(restoredSnap, snap)
      } else intercept[IllegalArgumentException](original.toSnapXml)
    }
  }

  test("BeExpression and Python round trip") {
    val expression = ProgrammingStateRaw(Python, python).toBeExpression
    assertEquals(normalized(expression.toPython.toBeExpression), normalized(expression))
  }

  test("BeExpression and Snap round trip") {
    val expression = ProgrammingStateRaw(Python, python).toBeExpression
    assertEquals(normalized(expression.toSnapXml.toBeExpression), normalized(expression))
  }

  test("Snap and Python round trip") {
    val snap = ProgrammingStateRaw(Python, python).toSnapXml
    assertEquals(normalized(snap.toPython.toSnapXml), normalized(snap))
  }

  test("behavior is available after switching representations") {
    val commands = ProgrammingStateRaw(Python, "forward(12)").toSnapXml
      .toBeExpression.turtleCommandExecutionInfo.completedCommands
    assertEquals(commands.map(_.name.universalInterpretation()), List("forward"))
    assertEquals(commands.flatMap(_.args), List(12.0))
  }

  test("modulo conditionals and loops remain executable after switching to Snap") {
    val source = """i = 0
      |while i < 4:
      |    if i % 2 == 0:
      |        forward(10)
      |    else:
      |        forward(20)
      |    i = i + 1
      |""".stripMargin
    val original = ProgrammingStateRaw(Python, source)
    val snap = original.toSnapXml
    assert(snap.code.contains("reportModulus"))
    val expected = original.toBeExpression.turtleCommandExecutionInfo.completedCommands
    assertEquals(expected.flatMap(_.args), List(10.0, 20.0, 10.0, 20.0))
    assertEquals(snap.toBeExpression.turtleCommandExecutionInfo.completedCommands, expected)
  }

  test("negative turns and pen-up return distances retain their signs in Snap") {
    val original = ProgrammingStateRaw(Python, "turn(-60)\npenup()\nlength = 30\nforward(-length)\npendown()\n")
    assertEquals(original.toSnapXml.toBeExpression.turtleCommandExecutionInfo.completedCommands,
      original.toBeExpression.turtleCommandExecutionInfo.completedCommands)
  }

  test("BeExpression and Java round trip") {
    val expression = ProgrammingStateRaw(Python,
      "steps = 10\nif steps > 5:\n    forward(steps)\nelse:\n    backward(2)\n"
    ).toBeExpression
    val java = expression.toJava
    assertEquals(java.toJava, java)
    assertEquals(normalized(java.toBeExpression), normalized(expression))
  }

  test("Java transforms to Python and Snap via BeExpression") {
    val java = ProgrammingStateRaw(Java, "int steps = 12; forward(steps);")
    assert(java.toPython.code.contains("forward(steps)"), clue = java.toPython.code)
    assert(java.toSnapXml.code.contains("forward"), clue = java.toSnapXml.code)
  }

  test("Java method fragments with returns reject Snap without changing their raw source") {
    List(
      "int value(int n) { return n; } value(2);",
      "int draw(int n) { while (n > 0) { if (n == 2) { return n; } forward(n); n = n - 1; } return 0; } draw(3);"
    ).foreach { fragment =>
      val source = "\r\n" + fragment + "\r\n\t "
      val state = ProgrammingStateRaw(Java, source)
      val fingerprint = state.fingerprint()
      val stored = ProgrammingState.serializer.serialize(state)
      val expression = state.toBeExpression
      assert(!state.isClassProgram, clue = source)
      assert(expression.toPython.code.contains("return"), clue = source)
      assert(SnapExpressionSupport.validateSubset(expression.expression).isLeft, clue = source)
      intercept[IllegalArgumentException](state.toSnapXml)
      assertEquals(state.toJava.code, source)
      assertEquals(state.fingerprint(), fingerprint)
      assertEquals(ProgrammingState.serializer.serialize(state), stored)
    }
  }

  test("generic Python expression states with returns reject Snap without mutation") {
    List(
      "def value(n):\n    return n\nvalue(2)\n",
      "def draw(n):\n    while n > 0:\n        if n == 2:\n            return\n        forward(n)\n        n = n - 1\ndraw(3)\n"
    ).foreach { source =>
      val raw = ProgrammingStateRaw(Python, source)
      val rawFingerprint = raw.fingerprint()
      val state = raw.toBeExpression
      val fingerprint = state.fingerprint()
      val rendered = state.toPython
      assert(rendered.code.contains("return"), clue = source)
      assert(SnapExpressionSupport.validateSubset(state.expression).isLeft, clue = source)
      intercept[IllegalArgumentException](state.toSnapXml)
      assertEquals(state.toPython, rendered)
      assertEquals(state.fingerprint(), fingerprint)
      assertEquals(raw.code, source)
      assertEquals(raw.fingerprint(), rawFingerprint)
    }
  }

  test("supported Java method fragments still convert conditionals and loops to Snap") {
    val source = "void draw(int count) { int i = 0; while (i < count) { if (i % 2 == 0) { forward(10); } else { forward(20); } i = i + 1; } } draw(3);"
    val state = ProgrammingStateRaw(Java, source)
    val fingerprint = state.fingerprint()
    val expression = state.toBeExpression
    assert(SnapExpressionSupport.validateSubset(expression.expression).isRight, clue = source)
    val commands = expression.turtleCommandExecutionInfo.completedCommands
    assertEquals(commands.flatMap(_.args), List(10.0, 20.0, 10.0))
    assertEquals(state.toSnapXml.toBeExpression.turtleCommandExecutionInfo.completedCommands, commands)
    assertEquals(state.toJava.code, source)
    assertEquals(state.fingerprint(), fingerprint)
  }

  test("Java and Snap round trip") {
    val java = ProgrammingStateRaw(Java, "forward(10); turn_right(90); forward(5);")
    val restoredJava = java.toSnapXml.toJava

    assertEquals(normalized(restoredJava), normalized(java))
    assert(restoredJava.code.contains("forward(10)"), clue = restoredJava.code)
    assert(restoredJava.code.contains("right(90)"), clue = restoredJava.code)
  }

  test("Java parser handles functions, loops, booleans, and string punctuation") {
    val java =
      """void draw(int count){
        |  int i = 0;
        |  while(i < count && true){
        |    println("!;{}");
        |    i = i + 1;
        |  }
        |}
        |draw(2);
        |""".stripMargin
    val python = ProgrammingStateRaw(Java, java).toPython.code
    assert(python.contains("def draw(count: float) -> None:"), clue = python)
    assert(python.contains("while i < count and True:"), clue = python)
    assert(python.contains("print(\"!;{}\")"), clue = python)
  }

  test("full Java classes compile through the state boundary and execute a parameterized square") {
    val state = ProgrammingStateRaw(Java, squareJava())
    val compiled = state.compileForExecution.fold(diagnostic => fail(diagnostic.toString), identity)
    val execution = T.runVm(compiled)

    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, squareCommands(25))
    assertEquals(execution.commands.size, 8)
    assertEquals(compiled.root.entryPoint.binding.originalName, "main")
    assertEquals(compiled.root.methods.map(_.binding.originalName), Vector("square", "main"))
    assertEquals(JavaTurtleCompiler.compile(state.code).map(program => T.runVm(program)), Right(execution))
    assertEquals(state.toJava, state)
    assertEquals(state.code, squareJava())
  }

  test("full Java state execution distinguishes wrong square sides and turns") {
    val variants = List(
      squareJava() -> true,
      squareJava(side = 40) -> false,
      squareJava(forward = "side + 1") -> false,
      squareJava(turn = 45) -> false
    )
    variants.foreach { (source, correct) =>
      val program = ProgrammingStateRaw(Java, source).compileForExecution.fold(diagnostic => fail(diagnostic.toString), identity)
      val execution = T.runVm(program)
      assertEquals(execution.status, T.Status.Completed)
      assertEquals(execution.commands.size, 8)
      assertEquals(execution.commands == squareCommands(25), correct, clue = source)
    }
  }

  test("failed full Java compilation keeps the raw source representation and diagnostics") {
    val cases = List(
      "\tpublic class Main {\r\n\tpublic static void main(String[] args) {\r\n\t\tTurtle.forward(10);\r\n" -> JavaTurtleSourceParser.Problem.UnsupportedSyntax,
      "public class Main {\r\n\tint side = 25;\r\n\tpublic static void main(String[] args) { Turtle.forward(side); }\r\n}\r\n" -> JavaTurtleSourceParser.Problem.UnsupportedStructure,
      "public class Main {\r\n\tpublic static void main(String[] args) { float side = 25; }\r\n}\r\n" -> JavaTurtleSourceParser.Problem.UnsupportedType,
      "public class Main {\r\n\tpublic static void main(String[] args) { missing(); }\r\n}\r\n" -> JavaTurtleSourceParser.Problem.UnknownMethod
    )
    cases.foreach { (source, expectedProblem) =>
      val state = ProgrammingStateRaw(Java, source)
      val serialized = ProgrammingState.serializer.serialize(state)
      val fingerprint = state.fingerprint()
      state.compileForExecution match {
        case Left(diagnostic) =>
          assertEquals(diagnostic.problem, expectedProblem, clue = source)
          assert(diagnostic.message.nonEmpty)
        case Right(_) => fail(s"Unsupported Java source compiled: $source")
      }
      assertEquals(JavaTurtleCompiler.compile(source).left.map(_.problem), Left(expectedProblem))
      assertEquals(state.toJava.code, source)
      assertEquals(ProgrammingState.serializer.serialize(state), serialized)
      assertEquals(state.fingerprint(), fingerprint)
      assertEquals(ProgrammingState.serializer.deserialize(serialized), state)
    }
  }

  test("serialized full Java classes retain CRLF tabs and runnable helper methods") {
    val source = "\r\n" + squareJava().replace("    ", "\t").replace("\n", "\r\n")
    val original = ProgrammingStateRaw(Java, source)
    val stored = ProgrammingState.serializer.serialize(original)
    val restored = ProgrammingState.serializer.deserialize(stored)

    assert(restored.isInstanceOf[ProgrammingStateRaw])
    assertEquals(restored, original)
    assertEquals(restored.toJava.code, source)
    assertEquals(restored.fingerprint(), s"java:$source")
    assertEquals(ProgrammingState.serializer.serialize(restored), stored)
    val program = restored.toJava.compileForExecution.fold(diagnostic => fail(diagnostic.toString), identity)
    assertEquals(T.runVm(program).commands, squareCommands(25))
    assertEquals(original.code, source)
  }

  test("typed full Java programs reject generic simulation without changing their source") {
    val state = ProgrammingStateRaw(Java, squareJava())
    val fingerprint = state.fingerprint()
    val program = state.compileForExecution.fold(diagnostic => fail(diagnostic.toString), identity)

    assertEquals(T.runVm(program).status, T.Status.Completed)
    assertEquals(state.code, squareJava())
    assertEquals(state.fingerprint(), fingerprint)
  }

  test("unfinished and unsupported Java cannot become an empty legacy turtle program") {
    List(
      "public class Main {",
      "class Main { public static void main(String[] args) { forward(10);",
      "this is unsupported Java"
    ).foreach { source =>
      val state = ProgrammingStateRaw(Java, source)
      val stored = ProgrammingState.serializer.serialize(state)
      intercept[IllegalArgumentException](state.isClassProgram)
      intercept[IllegalArgumentException](state.toLegacyTurtleCommands)
      intercept[IllegalArgumentException](state.toSnapXml)
      assertEquals(state.code, source)
      assertEquals(ProgrammingState.serializer.serialize(state), stored)
    }
  }

  test("class words inside Java comments and strings do not classify fragments as classes") {
    val sources = List(
      "// class Fake {}\nforward(12);",
      "String note = \"class Fake { }\"; forward(12);"
    )
    sources.foreach { source =>
      val state = ProgrammingStateRaw(Java, source)
      assert(!state.isClassProgram, clue = source)
      assertEquals(state.toLegacyTurtleCommands.map(_.name.universalInterpretation()), List("forward"))
      assertEquals(state.toLegacyTurtleCommands.flatMap(_.args), List(12.0))
      assert(state.toSnapXml.code.contains("forward"), clue = source)
      assertEquals(state.code, source)
    }
    val blockComment = ProgrammingStateRaw(Java, "/* class Fake {} */ forward(12);")
    assert(!blockComment.isClassProgram)
    assertEquals(blockComment.code, "/* class Fake {} */ forward(12);")
  }

  test("Java fragments remain executable without embedded source-name hints") {
    val state = ProgrammingStateRaw(Python,
      "def draw(distance):\n    forward(distance)\ndraw(12)\n"
    ).toJava
    val source = state.code

    assert(!source.contains("//EvaEntityName("), clue = source)
    assert(!state.isClassProgram, clue = source)
    assertEquals(state.toLegacyTurtleCommands.map(_.name.universalInterpretation()), List("forward"))
    assertEquals(state.toLegacyTurtleCommands.flatMap(_.args), List(12.0))
    assert(state.toSnapXml.code.contains("custom-block"), clue = source)
    assertEquals(state.code, source)
  }

  test("entity hints inside literals cannot hide a Java class") {
    val source = "String a = \"//EvaEntityName(\"; class Hidden {} String b = \")\"; forward(12);"
    val state = ProgrammingStateRaw(Java, source)
    assert(state.isClassProgram)
    intercept[IllegalArgumentException](state.toLegacyTurtleCommands)
    intercept[IllegalArgumentException](state.toSnapXml)
    assertEquals(state.code, source)
  }

  test("Java turtle classification bounds parser input and rejects unclosed lexemes") {
    val sources = List(
      "forward(" + "(" * 2000 + "1" + ")" * 2000 + ");",
      "boolean flag = " + "!" * 2000 + "true;",
      "forward(1);" * 4000,
      "/* unfinished",
      "String note = \"unfinished",
      "String note = \"line\nnext\";",
      "String note = \"line\\\nnext\";",
      "String note = \"line\\\rnext\";"
    )
    sources.foreach { code =>
      val state = ProgrammingStateRaw(Java, code)
      val stored = ProgrammingState.serializer.serialize(state)
      intercept[IllegalArgumentException](state.isClassProgram)
      intercept[IllegalArgumentException](state.toSnapXml)
      assertEquals(state.code, code)
      assertEquals(ProgrammingState.serializer.serialize(state), stored)
    }
    val literal = ProgrammingStateRaw(Java, "String note = \"" + "(" * 2000 + "\"; forward(12);")
    assert(!literal.isClassProgram)
    assertEquals(literal.toLegacyTurtleCommands.flatMap(_.args), List(12.0))
  }

  test("full Java state programs enforce execution limits on endless loops") {
    val source = "class Main { public static void main(String[] args) { while (true) {} } }"
    val program = ProgrammingStateRaw(Java, source).compileForExecution.fold(diagnostic => fail(diagnostic.toString), identity)
    assertEquals(T.runVm(program, T.Limits(maxSteps = 20)), T.Execution(T.Status.LimitExceeded, Vector.empty, 20))
  }

  test("full Java state execution retains int32 overflow and negative integer division") {
    val source = "class Main { public static void main(String[] args) { " +
      "Turtle.forward(2147483647 + 1); Turtle.forward(-9 / 2); Turtle.forward(-9 % 2); } }"
    val program = ProgrammingStateRaw(Java, source).compileForExecution.fold(diagnostic => fail(diagnostic.toString), identity)
    val execution = T.runVm(program)
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(Int.MinValue, -4, -1).map(value => T.Command(R.TurtleCommand.Forward, value)))
  }

  test("the full Java exercise default compiles without replacing its source or id") {
    val exercise = ProgrammingExerciseFullJava("kept-java-exercise")
    val source = exercise.defaultValue.toJava
    val stored = exercise.interactionFormat.stateSerializer.serialize(source)
    val program = source.compileForExecution.fold(diagnostic => fail(diagnostic.toString), identity)
    val result = T.runVm(program)
    assertEquals(result.status, T.Status.Completed)
    assertEquals(result.commands, Vector.empty[T.Command])
    assertEquals(exercise.elementId, "kept-java-exercise")
    assertEquals(exercise.defaultValue, source)
    assertEquals(exercise.interactionFormat.stateSerializer.serialize(source), stored)
  }

  test("compiling Java preserves a separate Snap state's ids during read-only conversions") {
    val xml = """<project><scripts><script x="70" y="80"><block s="receiveGo"/><block id="kept-forward" s="forward"><l>12</l></block></script></scripts></project>"""
    val snap = ProgrammingStateRaw(SnapLanguage, xml)
    val fingerprint = snap.fingerprint()
    val stored = ProgrammingState.serializer.serialize(snap)
    val program = ProgrammingStateRaw(Java, squareJava()).compileForExecution.fold(diagnostic => fail(diagnostic.toString), identity)

    assertEquals(T.runVm(program).commands, squareCommands(25))
    assertEquals(snap.toBeExpression.turtleCommandExecutionInfo.completedCommands.flatMap(_.args), List(12.0))
    assert(snap.toPython.code.contains("forward(12)"), clue = snap.toPython.code)
    assert(snap.toJava.code.contains("forward(12)"), clue = snap.toJava.code)
    assertEquals(snap.toSnapXml.code, xml)
    assertEquals(snap.fingerprint(), fingerprint)
    assertEquals(ProgrammingState.serializer.serialize(snap), stored)
    assertEquals(ProgrammingState.serializer.deserialize(stored), snap)
  }

  test("empty Snap projects convert without treating block containers as commands") {
    val states = List(
      ProgrammingStateSnapScalaModel.empty.toSnapXml,
      ProgrammingStateRaw(SnapLanguage, "<project><blocks/><scripts/></project>"),
      ProgrammingStateRaw(SnapLanguage, "<project><blocks></blocks><scripts><script/></scripts></project>")
    )

    states.foreach { state =>
      assertEquals(state.toBeExpression.turtleCommandExecutionInfo.completedCommands, Nil)
      assertEquals(state.toPython.toBeExpression.turtleCommandExecutionInfo.completedCommands, Nil)
      assertEquals(state.toJava.toBeExpression.turtleCommandExecutionInfo.completedCommands, Nil)
      assertEquals(state.toSnapXml, state)
    }
  }

  test("unsupported Snap commands still fail conversion without changing their source") {
    val sources = List(
      """<project><scripts><script><block s="wait"><l>1</l></block></script></scripts></project>""",
      """<project><blocks><block s="wait"><l>1</l></block></blocks></project>""",
      """<project><blocks><custom-block s="unknown %n"><l>1</l></custom-block></blocks></project>""",
      "<project><scripts><script><block s=\"wait\""
    )

    sources.foreach { xml =>
      val state = ProgrammingStateRaw(SnapLanguage, xml)
      val fingerprint = state.fingerprint()
      intercept[IllegalArgumentException](state.toPython)
      intercept[IllegalArgumentException](state.toJava)
      assertEquals(state.toSnapXml.code, xml)
      assertEquals(state.fingerprint(), fingerprint)
      assertEquals(ProgrammingState.serializer.deserialize(
        ProgrammingState.serializer.serialize(state)
      ), state)
    }
  }

  test("Java double declarations keep float hints even with integer initializers") {
    val java = ProgrammingStateRaw(Java, "double steps = 12; forward(steps);")
    val converted = java.toPython
    assert(converted.code.contains("steps: float = 12"), clue = converted.code)
    assertEquals(normalized(converted.toBeExpression), converted.code.trim)
  }

  test("Java parameter, return and local types survive conversion to Python") {
    val java = ProgrammingStateRaw(Java,
      """double distance(double length, boolean enabled, String label) {
        |  double result = length;
        |  return result;
        |}
        |boolean active = true;
        |String title = "path";
        |""".stripMargin
    )
    val converted = java.toPython
    assert(converted.code.contains("def distance(length: float, enabled: bool, label: str) -> float:"), clue = converted.code)
    assert(converted.code.contains("result: float = length"), clue = converted.code)
    assert(converted.code.contains("active: bool = True"), clue = converted.code)
    assert(converted.code.contains("title: str = \"path\""), clue = converted.code)
    assertEquals(normalized(converted.toBeExpression), converted.code.trim)
  }

  test("uninitialized Java variables retain their annotations") {
    val converted = ProgrammingStateRaw(Java, "double distance;").toPython
    assert(converted.code.contains("distance : float"), clue = converted.code)
    assertEquals(normalized(converted.toBeExpression), converted.code.trim)
  }

  test("unknown parameter types have usable Any hints and round trip to Snap") {
    val converted = ProgrammingStateRaw(Python, "def line(length):\n    forward(length)\nline(10)").toBeExpression.toPython
    assert(converted.code.contains("from typing import Any\n"), clue = converted.code)
    assert(converted.code.contains("def line(length: Any) -> None:"), clue = converted.code)
    assert(PythonToSnap.applyPython(converted.code).isRight, clue = converted.code)
    assertEquals(normalized(converted.toBeExpression), converted.code.trim)
  }

  test("date annotations include their import and survive Python reparsing") {
    val converted = ProgrammingStateRaw(Java, "Date day;").toPython
    assert(converted.code.startsWith("from datetime import date\n"), clue = converted.code)
    assert(converted.code.contains("day : date"), clue = converted.code)
    assertEquals(normalized(converted.toBeExpression), converted.code.trim)
  }

  test("clean Python AST recognizes None return annotations on editable Java methods") {
    val source = "def draw(length: float) -> None:\n    return\n\ndef main() -> None:\n    draw(2.0)\n"
    val parsed = it.evadid.vm.parsing.python.clean.PythonAstParserSimple.parse(source).fold(error => fail(error.getMessage), identity)
    val methods = parsed.statements.collect { case it.evadid.vm.parsing.python.clean.model.PyAST.StatementWithLineNumber(method: it.evadid.vm.parsing.python.clean.model.PyAST.PyFunctionDef, _) => method.name }
    assertEquals(methods.toList, List("draw", "main"))
  }

}
