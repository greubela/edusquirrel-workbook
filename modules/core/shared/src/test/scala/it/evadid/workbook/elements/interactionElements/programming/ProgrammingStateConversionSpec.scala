package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleSource, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.{BeSimulatorConfig, BeSimulatorState, BeVirtualMachineState}
import it.evadid.vm.simulation.java.JavaTurtleRuntime as T
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
    "Koch snowflake" ->
      """def koch(length, depth):
        |    if depth == 0:
        |        forward(length)
        |    else:
        |        koch(length / 3, depth - 1)
        |        turn_left(60)
        |        koch(length / 3, depth - 1)
        |        turn_right(120)
        |        koch(length / 3, depth - 1)
        |        turn_left(60)
        |        koch(length / 3, depth - 1)
        |
        |def koch_snowflake(length, depth):
        |    for side in range(3):
        |        koch(length, depth)
        |        turn_right(120)
        |
        |koch_snowflake(81, 3)
        |""".stripMargin,
    "sum of primes" ->
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
        |""".stripMargin
  )

  private def normalized(state: ProgrammingState): String = state.toPython.code.trim

  /**
   * Exercise every representation boundary rather than only checking that one
   * generated string looks plausible.  Python is the canonical comparison
   * format because Snap adds project metadata and Java changes surface syntax.
   */
  private def assertAllRoundTrips(source: String, includeJava: Boolean = true)(assertSnap: String => Unit = _ => ()): Unit = {
    val expression = ProgrammingStatePythonString(source).toBeExpressionState
    val expected = normalized(expression)
    val snap = expression.toSnapXml

    assertSnap(snap.snapXml)

    val baseConversions = List[(String, ProgrammingState)](
      "BeExpression -> Python -> BeExpression" -> expression.toPython.toBeExpressionState,
      "BeExpression -> Snap -> BeExpression" -> snap.toBeExpressionState,
      "Python -> Snap -> Python" -> ProgrammingStatePythonString(source).toSnapXml.toPython,
      "Snap -> Python -> Snap" -> snap.toPython.toSnapXml
    )
    val javaConversions = if includeJava then {
      val java = expression.toJava
      List[(String, ProgrammingState)](
        "BeExpression -> Java -> BeExpression" -> java.toBeExpressionState,
        "Python -> Java -> Python" -> ProgrammingStatePythonString(source).toJava.toPython
      )
    } else Nil

    (baseConversions ++ javaConversions).foreach { (route, result) =>
      assertEquals(normalized(result), expected, clue = route)
    }

    val serializableStates = List[ProgrammingState](expression, expression.toPython, snap) ++
      (if includeJava then List(expression.toJava) else Nil)
    serializableStates.foreach { state =>
      val restored = ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(state)
      )
      assertEquals(normalized(restored), expected, clue = s"serialized ${state.getClass.getSimpleName}")
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
      assert(xml.contains("""s="doSetVar"""), clue = xml)
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

  roundTripScenarios.foreach { (name, source) =>
    test(s"$name round trips through BeExpression, Python, Java, and serialized Snap") {
      val original = ProgrammingStatePythonString(source).toBeExpressionState
      val throughPython = original.toPython.toBeExpressionState
      val java = original.toJava
      val loweredJava = new JavaToBeExpressionParser().toPython(java.code)
      val throughJava = java.toBeExpressionState
      val snap = original.toSnapXml
      val restoredSnap = ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(snap)
      ).toSnapXml

      assertEquals(normalized(throughPython), normalized(original))
      assertEquals(normalized(throughJava), normalized(original), clue = s"Java:\n${java.code}\nLowered:\n$loweredJava")
      assertEquals(restoredSnap, snap)
    }
  }

  test("BeExpression and Python round trip") {
    val expression = ProgrammingStatePythonString(python).toBeExpressionState
    assertEquals(normalized(expression.toPython.toBeExpressionState), normalized(expression))
  }

  test("BeExpression and Snap round trip") {
    val expression = ProgrammingStatePythonString(python).toBeExpressionState
    assertEquals(normalized(expression.toSnapXml.toBeExpressionState), normalized(expression))
  }

  test("Snap and Python round trip") {
    val snap = ProgrammingStatePythonString(python).toSnapXml
    assertEquals(normalized(snap.toPython.toSnapXml), normalized(snap))
  }

  test("behavior is available after switching representations") {
    val commands = ProgrammingStatePythonString("forward(12)").toSnapXml
      .toBeExpressionState.deriveTurtleCommands
    assertEquals(commands.map(_.name), List("forward"))
    assertEquals(commands.flatMap(_.args), List(12.0))
  }

  test("BeExpression and Java round trip") {
    val expression = ProgrammingStatePythonString(
      "steps = 10\nif steps > 5:\n    forward(steps)\nelse:\n    backward(2)\n"
    ).toBeExpressionState
    val java = expression.toJava
    assertEquals(java.toJava, java)
    assertEquals(normalized(java.toBeExpressionState), normalized(expression))
  }

  test("Java transforms to Python and Snap via BeExpression") {
    val java = ProgrammingStateJavaString("int steps = 12; forward(steps);")
    assert(java.toPython.code.contains("forward(steps)"), clue = java.toPython.code)
    assert(java.toSnapXml.snapXml.contains("forward"), clue = java.toSnapXml.snapXml)
  }

  test("Java and Snap round trip") {
    val java = ProgrammingStateJavaString("forward(10); turn_right(90); forward(5);")
    val restoredJava = java.toSnapXml.toJava

    assertEquals(normalized(restoredJava), normalized(java))
    assert(restoredJava.code.contains("forward(10)"), clue = restoredJava.code)
    assert(restoredJava.code.contains("turnRight(90)"), clue = restoredJava.code)
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
    val python = new JavaToBeExpressionParser().toPython(java)
    assert(python.contains("def draw(count):"), clue = python)
    assert(python.contains("while i < count  and  True:"), clue = python)
    assert(python.contains("println(\"!;{}\")"), clue = python)
    assert(ProgrammingStateJavaString(java).toBeExpressionState.expression != null)
  }

  test("full Java classes compile through the state boundary and execute a parameterized square") {
    val state = ProgrammingStateJavaString(squareJava())
    val compiled = state.toJavaVmProgram.fold(diagnostic => fail(diagnostic.toString), identity)
    val execution = T.runVm(compiled)

    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, squareCommands(25))
    assertEquals(execution.commands.size, 8)
    assertEquals(compiled.root.entryPoint.binding.originalName, "main")
    assertEquals(compiled.root.methods.map(_.binding.originalName), Vector("square", "main"))
    assertEquals(P.compile(state.code).map(program => T.runVm(program)), Right(execution))
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
      val program = ProgrammingStateJavaString(source).toJavaVmProgram.fold(diagnostic => fail(diagnostic.toString), identity)
      val execution = T.runVm(program)
      assertEquals(execution.status, T.Status.Completed)
      assertEquals(execution.commands.size, 8)
      assertEquals(execution.commands == squareCommands(25), correct, clue = source)
    }
  }

  test("failed full Java compilation keeps the raw source representation and diagnostics") {
    val cases = List(
      "\tpublic class Main {\r\n\tpublic static void main(String[] args) {\r\n\t\tTurtle.forward(10);\r\n" -> JavaTurtleSource.Problem.UnsupportedSyntax,
      "public class Main {\r\n\tint side = 25;\r\n\tpublic static void main(String[] args) { Turtle.forward(side); }\r\n}\r\n" -> JavaTurtleSource.Problem.UnsupportedStructure,
      "public class Main {\r\n\tpublic static void main(String[] args) { double side = 25; }\r\n}\r\n" -> JavaTurtleSource.Problem.UnsupportedType,
      "public class Main {\r\n\tpublic static void main(String[] args) { missing(); }\r\n}\r\n" -> JavaTurtleSource.Problem.UnknownMethod
    )
    cases.foreach { (source, expectedProblem) =>
      val state = ProgrammingStateJavaString(source)
      val serialized = ProgrammingExercise.StateSerializer.serialize(state)
      val fingerprint = ProgrammingState.fingerprint(state)
      state.toJavaVmProgram match {
        case Left(diagnostic) =>
          assertEquals(diagnostic.problem, expectedProblem, clue = source)
          assert(diagnostic.message.nonEmpty)
        case Right(_) => fail(s"Unsupported Java source compiled: $source")
      }
      assertEquals(P.compile(source).left.map(_.problem), Left(expectedProblem))
      assertEquals(state.toJava.code, source)
      assertEquals(serialized, s"${ProgrammingExercise.StateHeader}\nJAVA\n$source")
      assertEquals(ProgrammingExercise.StateSerializer.serialize(state), serialized)
      assertEquals(ProgrammingState.fingerprint(state), fingerprint)
      assertEquals(ProgrammingExercise.StateSerializer.deserialize(serialized), state)
    }
  }

  test("serialized full Java classes retain CRLF tabs and runnable helper methods") {
    val source = "\r\n" + squareJava().replace("    ", "\t").replace("\n", "\r\n")
    val original = ProgrammingStateJavaString(source)
    val stored = ProgrammingExercise.StateSerializer.serialize(original)
    val restored = ProgrammingExercise.StateSerializer.deserialize(stored)

    assert(restored.isInstanceOf[ProgrammingStateJavaString])
    assertEquals(restored, original)
    assertEquals(restored.toJava.code, source)
    assertEquals(ProgrammingState.fingerprint(restored), s"java:$source")
    assertEquals(ProgrammingExercise.StateSerializer.serialize(restored), stored)
    val program = restored.toJava.toJavaVmProgram.fold(diagnostic => fail(diagnostic.toString), identity)
    assertEquals(T.runVm(program).commands, squareCommands(25))
    assertEquals(original.code, source)
  }

  test("typed full Java programs stay outside legacy conversion and generic simulation") {
    val state = ProgrammingStateJavaString(squareJava())
    val fingerprint = ProgrammingState.fingerprint(state)
    val program = state.toJavaVmProgram.fold(diagnostic => fail(diagnostic.toString), identity)
    val simulator = BeSimulatorState(false, program.root, false, Nil, Nil, BeVirtualMachineState.emptyMachineState)

    intercept[UnsupportedOperationException](program.root.expressionExecutor(BeSimulatorConfig(), simulator))
    intercept[IllegalArgumentException](state.toBeExpressionState)
    intercept[IllegalArgumentException](state.toPython)
    intercept[IllegalArgumentException](state.toSnapXml)
    assertEquals(T.runVm(program).status, T.Status.Completed)
    assertEquals(state.code, squareJava())
    assertEquals(ProgrammingState.fingerprint(state), fingerprint)
  }

  test("full Java state programs enforce execution limits on endless loops") {
    val source = "class Main { public static void main(String[] args) { while (true) {} } }"
    val program = ProgrammingStateJavaString(source).toJavaVmProgram.fold(diagnostic => fail(diagnostic.toString), identity)
    assertEquals(T.runVm(program, T.Limits(maxSteps = 20)), T.Execution(T.Status.LimitExceeded, Vector.empty, 20))
  }

  test("full Java state execution retains int32 overflow and negative integer division") {
    val source = "class Main { public static void main(String[] args) { " +
      "Turtle.forward(2147483647 + 1); Turtle.forward(-9 / 2); Turtle.forward(-9 % 2); } }"
    val program = ProgrammingStateJavaString(source).toJavaVmProgram.fold(diagnostic => fail(diagnostic.toString), identity)
    val execution = T.runVm(program)
    assertEquals(execution.status, T.Status.Completed)
    assertEquals(execution.commands, Vector(Int.MinValue, -4, -1).map(value => T.Command(R.TurtleCommand.Forward, value)))
  }

  test("the full Java exercise default compiles without replacing its source or id") {
    val exercise = ProgrammingExerciseFullJava("kept-java-exercise")
    val source = exercise.defaultValue.toJava
    val stored = exercise.serializerInteractionContent.serialize(source)
    val program = source.toJavaVmProgram.fold(diagnostic => fail(diagnostic.toString), identity)
    val result = T.runVm(program)
    assertEquals(result.status, T.Status.Completed)
    assertEquals(result.commands, Vector.empty[T.Command])
    assertEquals(exercise.elementId, "kept-java-exercise")
    assertEquals(exercise.defaultValue, source)
    assertEquals(exercise.serializerInteractionContent.serialize(source), stored)
  }

  test("compiling Java leaves existing Snap ids and read-only conversion routes intact") {
    val xml = """<project><scripts><script x="70" y="80"><block id="kept-forward" s="forward"><l>12</l></block></script></scripts></project>"""
    val snap = ProgrammingStateSnapXml(xml)
    val fingerprint = ProgrammingState.fingerprint(snap)
    val stored = ProgrammingExercise.StateSerializer.serialize(snap)
    val program = ProgrammingStateJavaString(squareJava()).toJavaVmProgram.fold(diagnostic => fail(diagnostic.toString), identity)

    assertEquals(T.runVm(program).commands, squareCommands(25))
    assertEquals(snap.toBeExpressionState.deriveTurtleCommands.flatMap(_.args), List(12.0))
    assert(snap.toPython.code.contains("forward(12)"), clue = snap.toPython.code)
    assert(snap.toJava.code.contains("forward(12)"), clue = snap.toJava.code)
    assertEquals(snap.toSnapXml.snapXml, xml)
    assertEquals(ProgrammingState.fingerprint(snap), fingerprint)
    assertEquals(ProgrammingExercise.StateSerializer.serialize(snap), stored)
    assertEquals(ProgrammingExercise.StateSerializer.deserialize(stored), snap)
  }

  test("empty Snap projects convert without treating block containers as commands") {
    val states = List(
      ProgrammingExerciseState.empty,
      ProgrammingStateSnapXml("<project><blocks/><scripts/></project>"),
      ProgrammingStateSnapXml("<project><blocks></blocks><scripts><script/></scripts></project>")
    )

    states.foreach { state =>
      assertEquals(state.toBeExpressionState.deriveTurtleCommands, Nil)
      assertEquals(state.toPython.toBeExpressionState.deriveTurtleCommands, Nil)
      assertEquals(state.toJava.toBeExpressionState.deriveTurtleCommands, Nil)
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
      val state = ProgrammingStateSnapXml(xml)
      val fingerprint = ProgrammingState.fingerprint(state)
      intercept[IllegalArgumentException](state.toPython)
      intercept[IllegalArgumentException](state.toJava)
      assertEquals(state.toSnapXml.snapXml, xml)
      assertEquals(ProgrammingState.fingerprint(state), fingerprint)
      assertEquals(ProgrammingExercise.StateSerializer.deserialize(
        ProgrammingExercise.StateSerializer.serialize(state)
      ), state)
    }
  }
}
