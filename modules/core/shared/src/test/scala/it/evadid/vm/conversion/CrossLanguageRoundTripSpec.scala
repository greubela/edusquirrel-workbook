package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python, SnapLanguage}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

/** Language A → BeExpression → language B → BeExpression → language A, after normalising A once.
  *
  * Normalisation (A → BeExpression → A) removes formatting differences, so comparisons check that
  * functionality survives rather than whitespace. Between typed languages (Python, Java) the text
  * must come back unchanged. Snap has no variable types and stores every project variable with a
  * value, so a route through Snap may change hints and initial values once; a second pass must then
  * change nothing. Every intermediate state must draw the same turtle commands, which guards
  * against content hidden in unsupported placeholders.
  */
class CrossLanguageRoundTripSpec extends FunSuite {
  private def converted(state: ProgrammingState, language: ProgrammingLanguage): ProgrammingState = {
    val result = state.convertToLanguage(language)
    assert(!result.hasLostFunctionality, s"$language lost functionality: ${result.conversionInfo}\n$state")
    result.finalState.getOrElse(fail(s"no $language state for\n$state\n${result.conversionInfo}"))
  }
  private def rendered(state: ProgrammingState, language: ProgrammingLanguage): String = language match {
    case Python => state.toPythonStructured.toString
    case Java => state.toJavaStructured.toString
    case SnapLanguage => state.toSnapXml.code
    case other => fail(s"unexpected language $other")
  }
  private def commands(state: ProgrammingState) =
    state.toBeExpression.turtleCommandExecutionInfo.completedCommands.map(command => command.getClass.getSimpleName -> command.args)
  private def through(state: ProgrammingState, target: ProgrammingLanguage, origin: ProgrammingLanguage): ProgrammingState =
    converted(converted(state.toBeExpression, target).toBeExpression, origin)

  /** Collects every failing route, so one run shows the complete picture. */
  private def assertRoundTrips(source: ProgrammingState, languages: Seq[ProgrammingLanguage],
      expectedCommands: Option[Seq[(String, Seq[Double])]]): Unit = {
    val problems = Vector.newBuilder[String]
    def check(ok: => Boolean, message: => String): Unit =
      try { if !ok then problems += message } catch { case e: Throwable => problems += s"$message\n  EXCEPTION ${e.getMessage}" }
    def diff(a: String, b: String) = {
      val (x, y) = (a.linesIterator.toVector, b.linesIterator.toVector)
      x.zipAll(y, "<none>", "<none>").zipWithIndex.filter { case ((l, r), _) => l != r }.take(6)
        .map { case ((l, r), i) => s"    line $i: expected [$l]\n            obtained [$r]" }.mkString("\n")
    }
    def drawsExpected(state: ProgrammingState) = expectedCommands.forall(commands(state) == _.toList)
    val origin = source.associatedLanguage
    check(drawsExpected(source), s"SOURCE commands ${commands(source)}")
    val normalized = converted(source.toBeExpression, origin)
    val expected = rendered(normalized, origin)
    val again = rendered(converted(normalized.toBeExpression, origin), origin)
    check(again == expected, s"$origin normalisation is unstable\n${diff(expected, again)}")
    languages.foreach { target =>
      val route = s"$origin -> $target -> $origin"
      try {
        val viaTarget = converted(normalized.toBeExpression, target)
        check(drawsExpected(viaTarget), s"$route draws ${commands(viaTarget)} in $target")
        val back = through(normalized, target, origin)
        check(drawsExpected(back), s"$route draws ${commands(back)} after returning")
        if Set(origin, target).subsetOf(Set(Python, Java)) then
          check(rendered(back, origin) == expected, s"$route changed the text\n${diff(expected, rendered(back, origin))}")
        else {
          val second = rendered(through(back, target, origin), origin)
          check(second == rendered(back, origin), s"$route is not stable after one pass\n${diff(rendered(back, origin), second)}")
        }
      } catch { case e: Throwable => problems += s"$route EXCEPTION ${e.getMessage}" }
    }
    val found = problems.result()
    if found.nonEmpty then fail(s"${found.size} round-trip problems starting from $origin:\n${found.mkString("\n")}")
  }
  private def forward(distance: Double) = "Forward" -> Seq(distance)
  private def right(angle: Double) = "Right" -> Seq(angle)
  private def left(angle: Double) = "Left" -> Seq(angle)
  private val all = Seq(SnapLanguage, Python, Java)

  /** Shared module variables, their reassignment, and nested arithmetic used by main and a helper. */
  private val calculationPython =
    """side = 40
      |gap = side / 2 + 10
      |def step(factor):
      |    forward((side + gap) * factor - gap / 2)
      |step(2)
      |turn_right((side % 7) * 10 + 5)
      |side = side * 2 - (gap - 5) * 3
      |step((side - gap) / (gap + 10))
      |""".stripMargin
  // side = 40, gap = 30; step(2) = 125; turn 55; side = 5; step(-0.625) = -36.875
  private val calculationCommands = Seq(forward(125), right(55), forward(-36.875))

  test("shared variables and nested calculations survive every language pair, starting in Python") {
    assertRoundTrips(ProgrammingStateRaw(Python, calculationPython), all, Some(calculationCommands))
  }

  test("shared variables and nested calculations survive every language pair, starting in Java") {
    val java = """class Main {
      |    static double side = 40;
      |    static double gap;
      |    static void step(double factor) {
      |        Turtle.forward((side + gap) * factor - gap / 2);
      |    }
      |    public static void main(String[] args) {
      |        gap = side / 2 + 10;
      |        step(2);
      |        Turtle.turnRight((side % 7) * 10 + 5);
      |        side = side * 2 - (gap - 5) * 3;
      |        step((side - gap) / (gap + 10));
      |    }
      |}
      |""".stripMargin
    assertRoundTrips(ProgrammingStateJavaStructured(java), all, Some(calculationCommands))
  }

  test("shared variables and nested calculations survive every language pair, starting in Snap") {
    assertRoundTrips(ProgrammingStateRaw(Python, calculationPython).toSnapXml, all, Some(calculationCommands))
  }

  test("formatting does not matter: tabs, two-space indentation, CRLF and spacing normalise identically") {
    val variants = Seq(
      calculationPython,
      calculationPython.replace("    ", "\t"),
      calculationPython.replace("    ", "  ").replace(" = ", "=").replace("\n", "\r\n"),
      calculationPython.replace("(side + gap) * factor", "( side+gap )*factor"))
    val normalized = variants.map(variant => ProgrammingStateRaw(Python, variant).toBeExpression.toPythonStructured.toString)
    assertEquals(normalized.distinct.size, 1, normalized.mkString("\n---\n"))
    variants.foreach(variant => assertRoundTrips(ProgrammingStateRaw(Python, variant), all, Some(calculationCommands)))
    val java = ProgrammingStateRaw(Python, calculationPython).toJavaStructured.toString
    val compactJava = java.replace("\n", " ").replaceAll(" +", " ")
    assertEquals(ProgrammingStateJavaStructured(compactJava).toBeExpression.toJavaStructured.toString, java)
  }

  test("scenario A: the basic turtle script with a helper survives every language pair") {
    val python = """x = 100
      |def doSomething():
      |    forward(20)
      |    forward(20)
      |    forward(20)
      |def main():
      |    doSomething()
      |    forward(x)
      |main()
      |""".stripMargin
    val expected = Seq(forward(20), forward(20), forward(20), forward(100))
    Seq(ProgrammingStateRaw(Python, python), ProgrammingStateRaw(Python, python).toJavaStructured,
      ProgrammingStateRaw(Python, python).toSnapXml).foreach(assertRoundTrips(_, all, Some(expected)))
  }

  test("scenario B: while, fixed repetition, if, if-else and if-elif-else survive every language pair") {
    val python = """length = 10
      |count = 0
      |while count < 3:
      |    forward(length)
      |    count = count + 1
      |for i in range(2):
      |    turn_right(90)
      |if length > 5:
      |    forward(1)
      |if count == 2:
      |    forward(2)
      |else:
      |    turn_left(30)
      |if length < 5:
      |    forward(3)
      |elif length < 20:
      |    forward(4)
      |else:
      |    forward(5)
      |""".stripMargin
    val expected = Seq(forward(10), forward(10), forward(10), right(90), right(90), forward(1), left(30), forward(4))
    Seq(ProgrammingStateRaw(Python, python), ProgrammingStateRaw(Python, python).toJavaStructured,
      ProgrammingStateRaw(Python, python).toSnapXml).foreach(assertRoundTrips(_, all, Some(expected)))
  }

  test("scenario C: a class with attributes, constructor and method survives Python and Java pairs") {
    val python = """class Walker:
      |    def __init__(self, step):
      |        self.step = step
      |    def walk(self, times):
      |        forward(self.step * times)
      |w = Walker(15)
      |w.walk(3)
      |""".stripMargin
    val languages = Seq(Python, Java)
    Seq(ProgrammingStateRaw(Python, python), ProgrammingStateRaw(Python, python).toJavaStructured)
      // The shared drawing simulator does not execute objects yet, so only the source text is compared.
      .foreach(assertRoundTrips(_, languages, None))
  }
}
