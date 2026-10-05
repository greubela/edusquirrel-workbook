package it.evadid.workbook.elements.interactionElements.programming

import munit.FunSuite

class ProgrammingStateConversionSpec extends FunSuite {
  private val python = "forward(10)\nturn_right(90)\nforward(5)\n"

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
}
