package it.evadid.workbook.elements.interactionElements.programming
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.*

import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleSource, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.{BeSimulatorConfig, BeSimulatorState, BeVirtualMachineState}
import it.evadid.vm.simulation.java.JavaTurtleRuntime as T
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtlePythonBridge
import it.evadid.workbook.elements.interactionElements.programming.state.{JavaToBeExpressionParser, ProgrammingState}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.{ProgrammingStateJavaString, ProgrammingStatePythonString}
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
    "recursive branching" ->
      """def branch(length, depth):
        |    if depth == 0:
        |        forward(length)
        |    else:
        |        branch(length / 2, depth - 1)
        |        turn_left(45)
        |        branch(length / 2, depth - 1)
        |
        |branch(64, 3)
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

  test("modulo conditionals and loops remain executable after switching to Snap") {
    val source = """i = 0
      |while i < 4:
      |    if i % 2 == 0:
      |        forward(10)
      |    else:
      |        forward(20)
      |    i = i + 1
      |""".stripMargin
    val original = ProgrammingStatePythonString(source)
    val snap = original.toSnapXml
    assert(snap.snapXml.contains("reportModulus"))
    val expected = original.toBeExpressionState.deriveTurtleCommands
    assertEquals(expected.flatMap(_.args), List(10.0, 20.0, 10.0, 20.0))
    assertEquals(snap.toBeExpressionState.deriveTurtleCommands, expected)
  }

  test("negative turns and pen-up return distances retain their signs in Snap") {
    val original = ProgrammingStatePythonString("turn(-60)\npenup()\nlength = 30\nforward(-length)\npendown()\n")
    assertEquals(original.toSnapXml.toBeExpressionState.deriveTurtleCommands,
      original.toBeExpressionState.deriveTurtleCommands)
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
    assert(python.contains("def draw(count: float) -> None:"), clue = python)
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
      "public class Main {\r\n\tpublic static void main(String[] args) { float side = 25; }\r\n}\r\n" -> JavaTurtleSource.Problem.UnsupportedType,
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

  test("typed full Java programs reject generic simulation without changing their source") {
    val state = ProgrammingStateJavaString(squareJava())
    val fingerprint = ProgrammingState.fingerprint(state)
    val program = state.toJavaVmProgram.fold(diagnostic => fail(diagnostic.toString), identity)
    val simulator = BeSimulatorState(false, program.root, false, Nil, Nil, BeVirtualMachineState.emptyMachineState)

    intercept[UnsupportedOperationException](program.root.expressionExecutor(BeSimulatorConfig(), simulator))
    assertEquals(T.runVm(program).status, T.Status.Completed)
    assertEquals(state.code, squareJava())
    assertEquals(ProgrammingState.fingerprint(state), fingerprint)
  }

  test("full Java classes have checked editable Python and Snap views while rejecting legacy execution") {
    val state = ProgrammingStateJavaString(squareJava())
    val stored = ProgrammingExercise.StateSerializer.serialize(state)
    val fingerprint = ProgrammingState.fingerprint(state)

    assert(state.isClassProgram)
    val readable = ProgrammingStateJavaString("class Main { public static void main(String[] args) { Turtle.forward(12); } }")
    assert(readable.toBeExpressionState.expression != null)
    assert(readable.toPython.code.contains("def main("), clue = readable.toPython.code)
    assert(readable.toPython.code.contains("turtle.forward(12)"), clue = readable.toPython.code)
    intercept[IllegalArgumentException](state.toLegacyTurtleCommands)
    val snap = state.toSnapXml
    assert(JavaTurtleEditingBridge.hasSnapMetadata(snap.snapXml))
    assertEquals(T.runVm(snap.toJava.toJavaVmProgram.toOption.get).commands, squareCommands(25))
    assertEquals(T.runVm(state.toPython.toJava.toJavaVmProgram.toOption.get).commands, squareCommands(25))
    assertEquals(state.toJava, state)
    assertEquals(state.code, squareJava())
    assertEquals(ProgrammingExercise.StateSerializer.serialize(state), stored)
    assertEquals(ProgrammingState.fingerprint(state), fingerprint)
    assertEquals(T.runVm(state.toJavaVmProgram.toOption.get).commands, squareCommands(25))
  }

  test("unfinished and unsupported Java cannot become an empty legacy turtle program") {
    List(
      "public class Main {",
      "class Main { public static void main(String[] args) { forward(10);",
      "this is unsupported Java"
    ).foreach { source =>
      val state = ProgrammingStateJavaString(source)
      val stored = ProgrammingExercise.StateSerializer.serialize(state)
      intercept[IllegalArgumentException](state.isClassProgram)
      intercept[IllegalArgumentException](state.toLegacyTurtleCommands)
      intercept[IllegalArgumentException](state.toSnapXml)
      assertEquals(state.code, source)
      assertEquals(ProgrammingExercise.StateSerializer.serialize(state), stored)
    }
  }

  test("class words inside Java comments and strings do not classify fragments as classes") {
    val sources = List(
      "// class Fake {}\nforward(12);",
      "String note = \"class Fake { }\"; forward(12);"
    )
    sources.foreach { source =>
      val state = ProgrammingStateJavaString(source)
      assert(!state.isClassProgram, clue = source)
      assertEquals(state.toLegacyTurtleCommands.map(_.name), List("forward"))
      assertEquals(state.toLegacyTurtleCommands.flatMap(_.args), List(12.0))
      assert(state.toSnapXml.snapXml.contains("forward"), clue = source)
      assertEquals(state.code, source)
    }
    val blockComment = ProgrammingStateJavaString("/* class Fake {} */ forward(12);")
    assert(!blockComment.isClassProgram)
    assertEquals(blockComment.code, "/* class Fake {} */ forward(12);")
  }

  test("Java printer entity hints remain compatible with fragment classification") {
    val state = ProgrammingStatePythonString(
      "def draw(distance):\n    forward(distance)\ndraw(12)\n"
    ).toJava
    val source = state.code

    assert(source.contains("//EvaEntityName("), clue = source)
    assert(!state.isClassProgram, clue = source)
    assertEquals(state.toLegacyTurtleCommands.map(_.name), List("forward"))
    assertEquals(state.toLegacyTurtleCommands.flatMap(_.args), List(12.0))
    assert(state.toSnapXml.snapXml.contains("custom-block"), clue = source)
    assertEquals(state.code, source)
  }

  test("entity hints inside literals cannot hide a Java class") {
    val source = "String a = \"//EvaEntityName(\"; class Hidden {} String b = \")\"; forward(12);"
    val state = ProgrammingStateJavaString(source)
    assert(state.isClassProgram)
    intercept[IllegalArgumentException](state.toLegacyTurtleCommands)
    intercept[IllegalArgumentException](state.toSnapXml)
    assertEquals(state.code, source)
    assertEquals(JavaToBeExpressionParser.withoutEntityHints(source), source)
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
      val state = ProgrammingStateJavaString(code)
      val stored = ProgrammingExercise.StateSerializer.serialize(state)
      intercept[IllegalArgumentException](state.isClassProgram)
      intercept[IllegalArgumentException](state.toSnapXml)
      assertEquals(state.code, code)
      assertEquals(ProgrammingExercise.StateSerializer.serialize(state), stored)
    }
    val literal = ProgrammingStateJavaString("String note = \"" + "(" * 2000 + "\"; forward(12);")
    assert(!literal.isClassProgram)
    assertEquals(literal.toLegacyTurtleCommands.flatMap(_.args), List(12.0))
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

  test("compiling Java preserves a separate Snap state's ids during read-only conversions") {
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
      ProgrammingStateSnapXml.empty,
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

  test("Java double declarations keep float hints even with integer initializers") {
    val java = ProgrammingStateJavaString("double steps = 12; forward(steps);")
    val converted = java.toPython
    assert(converted.code.contains("steps: float = 12"), clue = converted.code)
    assertEquals(normalized(converted.toBeExpressionState), converted.code.trim)
  }

  test("Java parameter, return and local types survive conversion to Python") {
    val java = ProgrammingStateJavaString(
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
    assertEquals(normalized(converted.toBeExpressionState), converted.code.trim)
  }

  test("uninitialized Java variables retain their annotations") {
    val converted = ProgrammingStateJavaString("double distance;").toPython
    assert(converted.code.contains("distance : float"), clue = converted.code)
    assertEquals(normalized(converted.toBeExpressionState), converted.code.trim)
  }

  test("unknown parameter types have usable Any hints and round trip to Snap") {
    val converted = ProgrammingStatePythonString("def line(length):\n    forward(length)\nline(10)").toBeExpressionState.toPython
    assert(converted.code.startsWith("from typing import Any\n"), clue = converted.code)
    assert(converted.code.contains("def line(length: Any) -> None:"), clue = converted.code)
    assert(SnapTurtlePythonBridge.applyPython(converted.code).isRight, clue = converted.code)
    assertEquals(normalized(converted.toBeExpressionState), converted.code.trim)
  }

  test("date annotations include their import and survive Python reparsing") {
    val converted = ProgrammingStateJavaString("Date day;").toPython
    assert(converted.code.startsWith("from datetime import date\n"), clue = converted.code)
    assert(converted.code.contains("day : date"), clue = converted.code)
    assertEquals(normalized(converted.toBeExpressionState), converted.code.trim)
  }

  private def checked(source: String): P.Program = P.compile(source).fold(diagnostic => fail(diagnostic.message), identity)
  private def bridgePython(program: P.Program): ProgrammingStatePythonString =
    JavaTurtleEditingBridge.toPython(program).fold(message => fail(message), identity)
  private def bridgeSnap(program: P.Program): ProgrammingStateSnapXml =
    JavaTurtleEditingBridge.toSnap(program).fold(message => fail(message), identity)
  private def bridgeJava(result: Either[String, ProgrammingStateJavaString]): P.Program =
    checked(result.fold(message => fail(message), identity).code)

  test("clean Python AST recognizes None return annotations on editable Java methods") {
    val source = "def draw(length: float) -> None:\n    return\n\ndef main() -> None:\n    draw(2.0)\n"
    val parsed = it.evadid.vm.parsing.python.clean.PythonAstParserSimple.parse(source).fold(error => fail(error.getMessage), identity)
    val methods = parsed.statements.collect { case it.evadid.vm.parsing.python.clean.model.PyAST.StatementWithLineNumber(method: it.evadid.vm.parsing.python.clean.model.PyAST.PyFunctionDef, _) => method.name }
    assertEquals(methods.toList, List("draw", "main"))
  }

  test("typed editing bridge preserves arithmetic scopes mutable parameters and for updates") {
    val source = """class Arithmetic {
      |  static void draw(int count, double length, boolean enabled) {
      |    int value = 2147483647;
      |    value += 1;
      |    if (enabled) {
      |      Turtle.forward(value);
      |      Turtle.forward(-9 / 2);
      |      Turtle.forward(-9 % 2);
      |    }
      |    for (int i = 0; i < count; i++) {
      |      Turtle.forward(length / 2.0);
      |      Turtle.turnRight(30);
      |    }
      |  }
      |  public static void main(String[] args) { draw(3, 5.0, true); }
      |}
      |""".stripMargin
    val program = checked(source)
    val expected = T.runVm(program).commands
    val python = bridgePython(program)
    val snap = bridgeSnap(program)
    assert(python.code.contains("def draw(count: int, length: float, enabled: bool) -> None:"))
    assert(python.code.contains("java_turtle.int_op(\"div\""))
    assert(python.code.contains("java_turtle.int_op(\"rem\""))
    assert(!python.code.contains("exec("))
    assert(snap.snapXml.contains("s=\"reportJavaInt\""))
    assert(snap.snapXml.contains("s=\"doDeclareVariables\""))
    assert(snap.snapXml.contains("s=\"doJavaForward\""))
    assert(!snap.snapXml.contains("s=\"doFor\""))
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromPython(python.code))).commands, expected)
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromSnap(snap))).commands, expected)
  }

  test("typed editing bridge preserves recursive locals early return and mutual method calls") {
    val source = """class Branching {
      |  static void left(int depth, double length) {
      |    double saved = length;
      |    if (depth <= 0) { Turtle.forward(saved); return; }
      |    right(depth - 1, length / 2.0);
      |    Turtle.forward(saved);
      |  }
      |  static void right(int depth, double length) {
      |    if (depth <= 0) { Turtle.forward(length); return; }
      |    left(depth - 1, length / 2.0);
      |    Turtle.turnRight(45);
      |  }
      |  public static void main(String[] args) { left(3, 16.0); }
      |}
      |""".stripMargin
    val program = checked(source)
    val expected = T.runVm(program).commands
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromPython(bridgePython(program).code))).commands, expected)
    val snap = bridgeSnap(program)
    assert(snap.snapXml.contains("<option>this block</option>"))
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromSnap(snap))).commands, expected)
  }

  test("typed editing views preserve prefix postfix ordering and local names in sibling scopes") {
    val source = """class Ordering {
      |  static void draw(int counter) {
      |    Turtle.forward(counter++ + ++counter);
      |    counter++;
      |    if (counter > 0) { int length = 4; Turtle.forward(length); }
      |    if (counter > 0) { int length = 5; Turtle.forward(length); }
      |  }
      |  public static void main(String[] args) { draw(2); }
      |}
      |""".stripMargin
    val program = checked(source)
    val expected = T.runVm(program).commands
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromPython(bridgePython(program).code))).commands, expected)
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromSnap(bridgeSnap(program)))).commands, expected)
  }

  test("edited typed Snap and Python views reconstruct changed programs instead of replaying hidden Java") {
    val program = checked("class Changed { public static void main(String[] args) { Turtle.forward(12); } }")
    val python = bridgePython(program).code.replace("turtle.forward(12)", "turtle.forward(19)")
    val snap = bridgeSnap(program)
    val changedXml = snap.snapXml.replace("<l>12</l>", "<l>23</l>")
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromPython(python))).commands.map(_.value), Vector(19.0))
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = changedXml)))).commands.map(_.value), Vector(23.0))
    assert(!snap.snapXml.contains("Turtle.forward"))
    assertEquals(snap.snapXml, bridgeSnap(program).snapXml)
  }

  test("typed bridge rejects lossy edits unknown blocks loose scripts and malformed metadata without mutation") {
    val program = checked(squareJava())
    val python = bridgePython(program)
    val snap = bridgeSnap(program)
    val badPython = List(
      python.code.replace("java_turtle.int_op(\"add\", i[0], 1)", "i[0] + 1"),
      python.code.replace("square(25)", "square(25, 1)"),
      python.code.replace("\\\"version\\\":1", "\\\"version\\\":2"),
      python.code + "print(1)\n",
      python.code + "#" + "x" * JavaTurtleEditingBridge.MaxRepresentationCharacters
    )
    badPython.foreach { code =>
      assert(code != python.code, clue = "The unsupported edit must change its source fixture.")
      assert(JavaTurtleEditingBridge.fromPython(code).isLeft, clue = code.take(200))
    }
    val badXml = List(
      snap.snapXml.replace("s=\"doJavaForward\"", "s=\"wait\""),
      snap.snapXml.replace("</sprite>", "<scripts><script><block s=\"doJavaForward\"><l>1</l></block></script></scripts></sprite>"),
      snap.snapXml.replace("</project>", "<unknown-extension/></project>"),
      snap.snapXml.replace("s=\"doJavaForward\"", "s=\"doJavaForward\" unknown=\"kept\""),
      snap.snapXml.replace("<l>add</l>", "<l> add </l>"),
      snap.snapXml.replace("<block s=\"doSetVar\"><l>i</l>", "<block s=\"doSetVar\"><l> i </l>"),
      snap.snapXml.replace("<block s=\"receiveGo\"></block>", "<block s=\"receiveGo\"><l>1</l></block>"),
      snap.snapXml.replace("<block s=\"reportJavaRead\"><l>i</l></block>", "<block var=\"i\"/>"),
      snap.snapXml.replace("<block s=\"reportJavaCell\"><l>25</l></block>", "<l>25</l>"),
      snap.snapXml.replace("<block s=\"doJavaReset\"></block>", ""),
      snap.snapXml.replace("category=\"variables\"", "category=\"control\""),
      snap.snapXml.replace("</block-definition>", "<comment>authored</comment></block-definition>"),
      snap.snapXml.replace("</block-definition>", "<header>authored</header></block-definition>"),
      snap.snapXml.replace("</block-definition>", "<translations>authored</translations></block-definition>"),
      snap.snapXml.replace("</project>", "<notes>authored</notes></project>"),
      snap.snapXml.replace("&quot;version&quot;:1", "&quot;version&quot;:2"),
      snap.snapXml.dropRight(10)
    )
    badXml.foreach { xml =>
      assert(xml != snap.snapXml, clue = "The unsupported edit must change its XML fixture.")
      assert(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = xml)).isLeft, clue = xml.take(200))
    }
    assert(JavaTurtleEditingBridge.fromSnap(snap.copy(legacyFloatingObjects = List("kept"))).isLeft)
    assertEquals(bridgePython(program), python)
    assertEquals(bridgeSnap(program), snap)
  }

  test("editing metadata recognition does not capture ordinary text mentions and retains malformed bridge headers") {
    assert(!JavaTurtleEditingBridge.hasPythonMetadata("print(\"__java_turtle_metadata\")\n"))
    assert(!JavaTurtleEditingBridge.hasSnapMetadata("<project><notes>A text about EduSquirrel Java Turtle 1.</notes></project>"))
    assert(JavaTurtleEditingBridge.hasPythonMetadata("import java_turtle\n__java_turtle_metadata = unfinished"))
    assert(JavaTurtleEditingBridge.hasPythonMetadata("import turtle\n\n__java_turtle_metadata = unfinished"))
    assert(!JavaTurtleEditingBridge.hasPythonMetadata("def ordinary():\n    __java_turtle_metadata = \"a local name\"\n"))
    assert(JavaTurtleEditingBridge.hasSnapMetadata("<project><notes>EduSquirrel Java Turtle 1\n{"))
    assert(JavaTurtleEditingBridge.fromPython("import java_turtle\n__java_turtle_metadata = unfinished").isLeft)
    assert(JavaTurtleEditingBridge.fromSnap(ProgrammingStateSnapXml("<project><notes>EduSquirrel Java Turtle 1\n{")).isLeft)
  }

  test("for bodies ending their method never emit unreachable updates in editable views") {
    val bodies = List(
      "Turtle.forward(9); return;",
      "if (enabled) { Turtle.forward(10); return; } else { Turtle.forward(20); return; }"
    )
    bodies.foreach { body =>
      val source = s"""class EndsMethod {
        |  static void draw(boolean enabled) {
        |    for (int i = 0; i < 2; i++) { $body }
        |  }
        |  public static void main(String[] args) { draw(true); draw(false); Turtle.forward(7); }
        |}
        |""".stripMargin
      val program = checked(source)
      val expected = T.runVm(program).commands
      val python = bridgePython(program)
      val snap = bridgeSnap(program)
      assert(!python.code.contains("java_turtle.update(i,"), clue = python.code)
      assert(!snap.snapXml.contains("s=\"reportJavaUpdate\""), clue = snap.snapXml)
      assert(snap.snapXml.contains("s=\"doJavaReset\""), clue = snap.snapXml)
      assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromPython(python.code))).commands, expected)
      assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromSnap(snap))).commands, expected)
    }
  }

  test("native scene notes empty method bodies and baseline numeric defaults preserve the typed bridge") {
    val program = checked("class EmptyMethod { static void idle(int count) {} public static void main(String[] args) { idle(1); } }")
    val snap = bridgeSnap(program)
    val globals = SnapCustomBlockRules.globalDefinitions(snap.snapXml)
    val all = SnapCustomBlockRules.allDefinitions(snap.snapXml)
    assertEquals(globals.map(_.element.start), all.map(_.element.start))
    assertEquals(SnapCustomBlockRules.localDefinitions(snap.snapXml), Nil)
    val metadataNotes = SnapXmlParser.elements(snap.snapXml, "notes").filter(node => SnapXmlParser.unescape(node.inner).startsWith(JavaTurtleEditingBridge.NotesPrefix))
    assertEquals(metadataNotes.size, 2)
    val native = snap.snapXml.replace("<script></script>", "")
      .replace("<input type=\"%n\"></input>", "<input type=\"%n\">0</input>")
    val restored = bridgeJava(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = native)))
    assertEquals(T.runVm(restored).commands, Vector.empty[T.Command])
    assertEquals(restored.root.methods.map(_.binding.originalName), Vector("idle", "main"))
    val projectNoteAt = native.indexOf(metadataNotes.head.outer)
    val onlySceneNotes = native.substring(0, projectNoteAt) + native.substring(projectNoteAt + metadataNotes.head.outer.length)
    assert(JavaTurtleEditingBridge.hasSnapMetadata(onlySceneNotes))
    assert(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = onlySceneNotes)).isRight)
  }

  test("numeric comparison views retain NaN infinities signed zero and integer comparisons on every route") {
    val source = """class Comparisons {
      |  public static void main(String[] args) {
      |    double missing = 0.0 / 0.0;
      |    double infinite = 1.0 / 0.0;
      |    double negativeZero = -0.0;
      |    int count = 4;
      |    boolean enabled = true;
      |    if (missing <= 1.0) { Turtle.forward(99); } else { Turtle.forward(1); }
      |    if (missing >= 1.0) { Turtle.forward(99); } else { Turtle.forward(2); }
      |    if (missing == missing) { Turtle.forward(99); } else { Turtle.forward(3); }
      |    if (missing != missing) { Turtle.forward(4); }
      |    if (negativeZero == 0.0) { Turtle.forward(5); }
      |    if (infinite > 1.0) { Turtle.forward(6); }
      |    if (infinite == infinite) { Turtle.forward(7); }
      |    if (missing < infinite || missing > infinite) { Turtle.forward(99); } else { Turtle.forward(8); }
      |    if (count < 5 && enabled == true) { Turtle.forward(9); }
      |  }
      |}
      |""".stripMargin
    val program = checked(source)
    val expected = (1 to 9).map(value => T.Command(R.TurtleCommand.Forward, value.toDouble)).toVector
    assertEquals(T.runVm(program).commands, expected)
    val python = bridgePython(program)
    val snap = bridgeSnap(program)
    assert(python.code.contains("java_turtle.compare(\"le\""), clue = python.code)
    assert(python.code.contains("java_turtle.compare(\"ge\""), clue = python.code)
    assert(snap.snapXml.contains("s=\"reportJavaCompare\""), clue = snap.snapXml)
    assert(!snap.snapXml.contains("s=\"reportVariadicLessThanOrEquals\""), clue = snap.snapXml)
    assert(!snap.snapXml.contains("s=\"reportVariadicGreaterThanOrEquals\""), clue = snap.snapXml)
    val pythonJava = bridgeJava(JavaTurtleEditingBridge.fromPython(python.code))
    val snapJava = bridgeJava(JavaTurtleEditingBridge.fromSnap(snap))
    assertEquals(T.runVm(pythonJava).commands, expected)
    assertEquals(T.runVm(snapJava).commands, expected)
    val pythonViaSnap = bridgePython(bridgeJava(JavaTurtleEditingBridge.fromSnap(bridgeSnap(pythonJava))))
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromPython(pythonViaSnap.code))).commands, expected)
  }

  test("repeated mixed numeric view changes retain meaning within the existing Java input limits") {
    val program = checked("""class Repeated {
      |  static void draw(int count, double side) {
      |    int twice = count * 2;
      |    double mixed = side + count;
      |    if (mixed >= twice) { Turtle.forward(mixed / 2.0); } else { Turtle.forward(side); }
      |  }
      |  public static void main(String[] args) { draw(2, 7.0); }
      |}
      |""".stripMargin)
    val expected = T.runVm(program).commands
    var viaPython = program
    var viaSnap = program
    (1 to 4).foreach { cycle =>
      viaPython = bridgeJava(JavaTurtleEditingBridge.fromPython(bridgePython(viaPython).code))
      viaSnap = bridgeJava(JavaTurtleEditingBridge.fromSnap(bridgeSnap(viaSnap)))
      assertEquals(T.runVm(viaPython).commands, expected, clue = s"Python cycle $cycle")
      assertEquals(T.runVm(viaSnap).commands, expected, clue = s"Snap cycle $cycle")
    }
  }

  test("method names remain callable when Java parameters and locals use those names") {
    val program = checked("""class Names {
      |  static void draw(int draw) {
      |    if (draw > 0) { draw(draw - 1); }
      |    Turtle.forward(draw);
      |  }
      |  public static void main(String[] args) { int draw = 2; draw(draw); }
      |}
      |""".stripMargin)
    val expected = T.runVm(program).commands
    val python = bridgePython(program)
    assert(python.code.contains("def draw(draw_java_0: int)"), clue = python.code)
    assert(!python.code.contains("\n    draw ="), clue = python.code)
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromPython(python.code))).commands, expected)
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromSnap(bridgeSnap(program)))).commands, expected)
    val shadowing = python.code.replace("draw_java_0", "draw")
    assert(shadowing != python.code)
    assert(JavaTurtleEditingBridge.fromPython(shadowing).isLeft)
  }

  test("native negative-zero literals retain their sign and cannot enter an int cell") {
    val program = checked("class Zero { static void draw(double length) { Turtle.forward(length); } public static void main(String[] args) { draw(0.0); Turtle.forward(0); } }")
    val snap = bridgeSnap(program)
    val changed = snap.snapXml.replace("<l>0.0</l>", "<l>-0</l>")
      .replace("<block s=\"doJavaForward\"><l>0</l></block>", "<block s=\"doJavaForward\"><l>-0</l></block>")
    assert(changed != snap.snapXml)
    val execution = T.runVm(bridgeJava(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = changed))))
    assertEquals(execution.commands.size, 2)
    assert(execution.commands.forall(command => command.value == 0.0 && 1.0 / command.value == Double.NegativeInfinity))
    val integers = bridgeSnap(checked("class ZeroInt { static void draw(int length) { Turtle.forward(length); } public static void main(String[] args) { draw(0); } }"))
    val badInteger = integers.snapXml.replace("<l>0</l>", "<l>-0</l>")
    assert(badInteger != integers.snapXml)
    assert(JavaTurtleEditingBridge.fromSnap(integers.copy(snapXml = badInteger)).isLeft)
  }

  test("typed projects cannot move global Java methods into local sprite blocks by removing the global container") {
    val snap = bridgeSnap(checked("class Localized { public static void main(String[] args) { Turtle.forward(12); } }"))
    val scene = SnapXmlParser.elements(snap.snapXml, "scene").head
    val globals = SnapXmlParser.child(scene.inner, "blocks").get
    val withoutGlobals = snap.snapXml.replace(globals.outer, "")
    val sprite = SnapXmlParser.elements(withoutGlobals, "sprite").head
    val localBlocks = SnapXmlParser.child(sprite.inner, "blocks").get
    val localAt = sprite.start + sprite.outer.indexOf('>') + 1 + localBlocks.start
    val malformed = withoutGlobals.substring(0, localAt) + s"<blocks>${globals.inner}</blocks>" +
      withoutGlobals.substring(localAt + localBlocks.outer.length)
    assert(malformed != snap.snapXml)
    assertEquals(SnapCustomBlockRules.allDefinitions(malformed).size, 1)
    assertEquals(SnapCustomBlockRules.globalDefinitions(malformed), Nil)
    assertEquals(SnapCustomBlockRules.localDefinitions(malformed).size, 1)
    assert(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = malformed)).isLeft)
  }

  test("native encoded note newlines and registered Variables category retain typed project metadata") {
    val program = checked(squareJava())
    val snap = bridgeSnap(program)
    val native = snap.snapXml.replace("\n", "&#xD;")
      .replace("category=\"variables\"", "category=\"Variables\"")
      .replace("<hidden></hidden>", "<palette><category name=\"Variables\" color=\"243,118,29,1\"/></palette><hidden></hidden>")
    assert(native != snap.snapXml)
    assert(JavaTurtleEditingBridge.hasSnapMetadata(native))
    assertEquals(T.runVm(bridgeJava(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = native)))).commands, squareCommands(25))
    val aliases = List("\r", "\r\n", "&#13;", "&#xA;", "&#10;")
    aliases.foreach { newline =>
      val normalized = native.replace("&#xD;", newline)
      assert(JavaTurtleEditingBridge.hasSnapMetadata(normalized), clue = newline)
      assert(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = normalized)).isRight, clue = newline)
    }
    val doubleEncoded = native.replace("&#xD;", "&amp;#xD;")
    assert(!JavaTurtleEditingBridge.hasSnapMetadata(doubleEncoded))
    assert(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = doubleEncoded)).isLeft)
    val authoredCategory = native.replace("category=\"Variables\"", "category=\"Author category\"")
    assert(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = authoredCategory)).isLeft)
  }

  test("native generated preview images do not change conversion or mutate stored XML") {
    val snap = bridgeSnap(checked("class Preview { public static void main(String[] args) { Turtle.forward(12); } }"))
    val image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB"
    val previews = List(s"<pentrails>$image</pentrails>", s"<pentrail>$image</pentrail>",
      s"<pentrails><pentrail>$image</pentrail></pentrails>")
    previews.foreach { preview =>
      val xml = snap.snapXml.replace("</stage>", preview + "</stage>")
        .replace("<scenes", s"<thumbnail>$image</thumbnail><scenes")
      val state = snap.copy(snapXml = xml)
      val stored = ProgrammingExercise.StateSerializer.serialize(state)
      val fingerprint = ProgrammingState.fingerprint(state)
      val restored = bridgeJava(JavaTurtleEditingBridge.fromSnap(state))
      assertEquals(T.runVm(restored).commands.map(_.value), Vector(12.0))
      assertEquals(state.snapXml, xml)
      assertEquals(ProgrammingExercise.StateSerializer.serialize(state), stored)
      assertEquals(ProgrammingState.fingerprint(state), fingerprint)
    }
    val authored = snap.snapXml.replace("</sprite>", s"<costume name=\"authored\">$image</costume></sprite>")
    assert(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = authored)).isLeft)
    val oversized = snap.snapXml.replace("</stage>", "<pentrails>" + "a" * JavaTurtleEditingBridge.MaxRepresentationCharacters + "</pentrails></stage>")
    assert(JavaTurtleEditingBridge.fromSnap(snap.copy(snapXml = oversized)).isLeft)
  }

}
