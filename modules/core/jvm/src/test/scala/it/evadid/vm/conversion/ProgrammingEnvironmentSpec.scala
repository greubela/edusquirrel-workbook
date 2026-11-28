package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.code.defining.BeIntrinsic
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.conversion.dependencies.{ProgrammingEnvironment, ProgrammingLibrary}
import it.evadid.workbook.interactions.programming.state.*
import munit.FunSuite

class ProgrammingEnvironmentSpec extends FunSuite {
  private val turtleOnly = ProgrammingEnvironment.of(ProgrammingLibrary.Turtle)

  private def intrinsics(state: ProgrammingState, environment: ProgrammingEnvironment) =
    state.inEnvironment(environment).expression.expression.subtree.collect { case call: BeFunctionCall => call.funcDef.intrinsic }.flatten.toSet

  private def unknown(state: ProgrammingState, environment: ProgrammingEnvironment) =
    state.inEnvironment(environment).expression.unknownDependency

  test("calls into libraries the environment provides bind to them; other library calls become unknown") {
    val python = ProgrammingStateRaw(Python, "forward(10)\nprint(1)\nNumberList.length()\n")
    assertEquals(intrinsics(python, ProgrammingEnvironment.all), Set(BeIntrinsic.Turtle, BeIntrinsic.Print, BeIntrinsic.NumberList))
    assertEquals(intrinsics(python, turtleOnly), Set(BeIntrinsic.Turtle))
    val missing = unknown(python, turtleOnly).get
    assertEquals(missing.definedFunctions.map(_.functionTypeInfo.displayName.spelling()), List("print"))
    assertEquals(missing.rootClasses.map(_.name.spelling()), List("NumberList"))
    assertEquals(unknown(python, ProgrammingEnvironment.all), None)
  }

  test("Java system output and turtle calls follow the environment with their written paths") {
    val java = ProgrammingStateRaw(Java, "class Main { static void main(String[] a) { Turtle.forward(10); System.out.println(1); } }")
    assertEquals(intrinsics(java, ProgrammingEnvironment.of(ProgrammingLibrary.System)), Set(BeIntrinsic.Print))
    val missing = unknown(java, ProgrammingEnvironment.of(ProgrammingLibrary.System)).get
    assertEquals(missing.rootClasses.map(root => root.name.spelling() -> root.methods.map(_.functionTypeInfo.displayName.spelling())),
      List("Turtle" -> List("forward")))
    assertEquals(unknown(java, turtleOnly).get.rootClasses.map(_.name.spelling()), List("System"))
  }

  test("built-in functions always exist, and each environment's derivations are computed once per state") {
    val state = ProgrammingStateRaw(Python, "x = max(1, int('2'))\n")
    assertEquals(unknown(state, ProgrammingEnvironment.of()), None)
    assert(state.inEnvironment(turtleOnly) eq state.inEnvironment(ProgrammingEnvironment.of(ProgrammingLibrary.Turtle)))
    assert(state.inEnvironment(ProgrammingEnvironment.all) eq state.derivations)
  }

  test("environments are stored by library names") {
    assertEquals(upickle.default.write(turtleOnly), """{"libraries":["Turtle"]}""")
    assertEquals(upickle.default.read[ProgrammingEnvironment](upickle.default.write(ProgrammingEnvironment.all)), ProgrammingEnvironment.all)
  }

  test("exercise types bring default environments to their graders; content can override them") {
    import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.*
    import it.evadid.workbook.interactions.programming.programmingExerciseRegular.SortListExercise
    def environments(graders: Seq[Any]) = graders.collect {
      case grader: RecreateShapeGrader => grader.environment
      case grader: TurtleCommandUsageGrader => grader.environment
      case grader: ProgramStructureGrader => grader.environment
      case grader: it.evadid.workbook.interactions.programming.programmingExerciseRegular.ProgramDataTestGrader => grader.environment
    }.toSet
    val shape = TurtleRecreateShapeInteraction("shape", ProgrammingStateRaw(Python, "forward(10)"), TurtleGraphic.TurtleGraphicSvgString("M0,0 L10,0"),
      structureTests = Seq(ProgramStructureGrader(ProgramStructureRequirement.UsesLoop, "loop", it.evadid.core.datastructures.language.LanguageMapContentId("x/y"))))
    assertEquals(environments(shape.gradingElements), Set(TurtleRecreateShapeInteraction.defaultEnvironment))
    val sort = SortListExercise("sort", ProgrammingStateRaw(Python, "def s():\n    pass"), "s")
    assertEquals(environments(sort.gradingElements), Set(SortListExercise.defaultEnvironment))
    val custom = shape.copy(environment = ProgrammingEnvironment.of(ProgrammingLibrary.System))
    assertEquals(environments(custom.gradingElements), Set(ProgrammingEnvironment.of(ProgrammingLibrary.System)))
  }

  test("graders read the program in their exercise's environment") {
    import it.evadid.workbook.abstractions.grading.{GradingImportance, GradingStatus}
    import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.{RecreateShapeGrader, TurtleGraphic}
    import scala.concurrent.Await
    import scala.concurrent.duration.*
    val grader = RecreateShapeGrader(TurtleGraphic.TurtleGraphicSvgString("M0,0 L10,0"), GradingImportance.REQUIRED_TEST)
    val state = ProgrammingStateRaw(Python, "forward(10)\n")
    def grade(environment: ProgrammingEnvironment) = Await.result(grader.inEnvironment(environment)
      .gradeStateSafe(state, true, it.evadid.workbook.sync.UpdateImportance.MAJOR,
        it.evadid.workbook.abstractions.ExecutionContextInformation.default), 10.seconds).gradingStatus
    assertEquals(grade(turtleOnly), GradingStatus.Correct)
    // Without the turtle library, `forward` is undefined code and draws nothing.
    assertNotEquals(grade(ProgrammingEnvironment.of(ProgrammingLibrary.System)), GradingStatus.Correct)
  }
}
