package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.compilation.java.turtle.JavaTurtleCompiler
import it.evadid.vm.simulation.java.JavaTurtleInterpreter
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaStructured, ProgrammingStateSnapScalaModel}
import munit.FunSuite

class MonksWorkbookSpec extends FunSuite {
  test("Monk choices, tables, slideshows and reorder tasks all expose shared graders") {
    import it.evadid.workbook.abstractions.WorkbookInteractionElement
    import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction
    import it.evadid.workbook.elements.interactionElements.table.AnswerTableInteraction
    import it.evadid.workbook.elements.interactionElements.slideshow.Slideshow
    import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction.ReorderMapIdInteraction
    val exercises = CreateMonksWorkbook(null).createWorkbook.allChildrenFullSubtree.collect {
      case exercise: WorkbookContentChoiceInteraction => exercise
      case exercise: AnswerTableInteraction => exercise
      case exercise: Slideshow => exercise
      case exercise: ReorderMapIdInteraction => exercise
    }
    assert(exercises.nonEmpty)
    exercises.foreach(exercise => assert(exercise.gradingElements.nonEmpty, exercise.elementId))
  }
  test("the four assignment tasks are graded matchings and the survey is an ungraded scale with opt-in consent") {
    import it.evadid.workbook.elements.interactionElements.basic.LabeledCheckboxInteraction
    import it.evadid.workbook.elements.interactionElements.matching.{LikertScaleInteraction, MatchingInteraction}
    val elements = CreateMonksWorkbook(null).createWorkbook.allChildrenFullSubtree
    val matchings = elements.collect { case matching: MatchingInteraction => matching }
    assertEquals(matchings.map(_.elementId).toSet, Set("monks-a4-match", "monks-a5-match", "monks-c1-match", "monks-c3-match"))
    matchings.foreach { matching =>
      assertEquals(matching.expected.map(_.size), Some(matching.left.size), matching.elementId)
      assertEquals(matching.gradingElements.size, 1, matching.elementId)
    }
    val survey = elements.collect { case scale: LikertScaleInteraction => scale }
    assertEquals(survey.map(scale => (scale.elementId, scale.statements.size, scale.scale.size)), Seq(("monks-survey", 8, 5)))
    assert(survey.head.gradingElements.isEmpty)
    val consent = elements.collect { case box: LabeledCheckboxInteraction if box.elementId == "monks-survey-consent" => box }
    assertEquals(consent.map(_.defaultValue), Seq(false))
  }

  private val editorIds = List("e1", "e2", "e3", "e4", "e5", "e6a", "e6b", "k2", "k4")
  private def checked(code: String): JavaTurtleInterpreter.Execution =
    JavaTurtleInterpreter.runVm(JavaTurtleCompiler.compile(code).fold(problem => fail(problem.message), identity))

  test("all drawing tasks retain the workbook's Snap, Python and Java editor choices") {
    val tasks = CreateMonksWorkbook(null).createWorkbook.allChildrenFullSubtree.collect {
      case task: TurtleRecreateShapeInteraction => task
    }
    assertEquals(tasks.size, editorIds.size)
    tasks.foreach { task =>
      assertEquals(task.exerciseConfig.allowedEditorLanguages, Seq(SnapLanguage, Python, Java), task.elementId)
      assert(task.desiredResult.renderLines.nonEmpty, task.elementId)
      assert(task.initProgram.isInstanceOf[ProgrammingStateJavaStructured], task.elementId)
    }
  }

  editorIds.foreach { id =>
    test(s"$id starting code compiles with the current Java model and converts to Snap") {
      val code = CreateMonksWorkbook.EditorCode(id)
      JavaTurtleCompiler.compile(code).fold(problem => fail(problem.message), _ => ())
      val source = ProgrammingStateJavaStructured(code)
      val snap = source.convertToLanguage(SnapLanguage).finalState.getOrElse(fail("No Snap form for " + id))
      assert(snap.isInstanceOf[ProgrammingStateSnapScalaModel])
      // Parsing the exported blocks must also work; producing XML alone is insufficient.
      snap.toBeExpression
    }
  }

  /** Functions of Python's standard turtle module that the shared turtle commands print. */
  private val standardPythonTurtle = Set("forward", "backward", "left", "right", "penup", "pendown", "setheading", "goto",
    "setx", "sety", "dot", "pensize", "speed", "home", "reset", "clear", "circle", "color", "showturtle", "hideturtle", "towards")

  private def python(source: ProgrammingState): ProgrammingState = {
    // Java's int parameters widen to shared numbers with a warning, as in the switch to Snap.
    val result = source.convertToLanguage(Python)
    val python = result.finalState.getOrElse(fail(s"No Python form: ${result.conversionInfo}"))
    val called = "turtle\\.(\\w+)\\(".r.findAllMatchIn(python.toString).map(_.group(1)).toSet
    assert(called.subsetOf(standardPythonTurtle), s"not in Python's turtle module: ${called -- standardPythonTurtle}\n$python")
    python
  }

  editorIds.foreach { id =>
    test(s"$id starting code converts to standard Python, and the Python back to Java and Snap") {
      val fromPython = python(ProgrammingStateJavaStructured(CreateMonksWorkbook.EditorCode(id)))
      Seq(Java, SnapLanguage).foreach { target =>
        val result = fromPython.convertToLanguage(target)
        assert(!result.hasLostFunctionality, s"$id Python -> $target: ${result.conversionInfo}")
        result.finalState.getOrElse(fail(s"No $target form for $id: ${result.conversionInfo}")).toBeExpression
      }
    }
  }

  test("the deliberately missing base case terminates at the Java execution limit") {
    val code = CreateMonksWorkbook.EditorCode("e6a")
    assertEquals(checked(code).status, JavaTurtleInterpreter.Status.LimitExceeded)
    val shared = ProgrammingStateJavaStructured(code).toBeExpression.turtleCommandExecutionInfo
    assertEquals(shared.executionStatus, it.evadid.vm.simulation.turtle.TurtleExecutionStatus.LimitExceeded)
  }

  test("the stairs without the way back miss the target until the turtle returns") {
    def drawing(code: String) = checked(code).commands.map(_.toDrawingCommand).toSeq
    val code = CreateMonksWorkbook.EditorCode("e6b")
    val expected = CreateMonksWorkbook.editorTarget("e6b").toTurtleProgram
    assertNotEquals(drawing(code), expected)
    assertEquals(drawing(code.replace("zeichneTreppe(n - 1);", "zeichneTreppe(n - 1);\n        runter();")), expected)
  }

  test("a solved recursive pyramid draws the same target after switching through Snap") {
    val body = """if (n == 0) { return; }
                 |stein(n * 20);
                 |hoch();
                 |springe(10);
                 |zeichnePyramide(n - 1);
                 |springe(-10);
                 |runter();""".stripMargin
    val source = ProgrammingStateJavaStructured(CreateMonksWorkbook.EditorCode("e5").replace("// TODO", body))
    val execution = checked(source.toString)
    assertEquals(execution.status, JavaTurtleInterpreter.Status.Completed)
    val expected = CreateMonksWorkbook.editorTarget("e5").toTurtleProgram
    assertEquals(execution.commands.map(_.toDrawingCommand).toSeq, expected)
    val snap = source.convertToLanguage(SnapLanguage).finalState.getOrElse(fail("No Snap form for the pyramid"))
    val fromSnap = snap.toBeExpression.turtleCommandExecutionInfo.completedCommands
    assertEquals(fromSnap.toSeq, expected)
    assert(fromSnap.contains(TurtleCommand.PenUp[Double]()))
    assert(fromSnap.contains(TurtleCommand.PenDown[Double]()))
    val viaPython = python(source)
    assertEquals(viaPython.toBeExpression.turtleCommandExecutionInfo.completedCommands.toSeq, expected, "Python draws the pyramid")
    Seq(Java, SnapLanguage).foreach { target =>
      val back = viaPython.convertToLanguage(target).finalState.getOrElse(fail(s"No $target form of the Python pyramid"))
      assertEquals(back.toBeExpression.turtleCommandExecutionInfo.completedCommands.toSeq, expected, s"Python -> $target")
    }
  }

  private val kochBody = """if (tiefe == 0) {
                           |    Turtle.forward(laenge);
                           |    return;
                           |}
                           |koch(laenge / 3, tiefe - 1);
                           |Turtle.turnLeft(60);
                           |koch(laenge / 3, tiefe - 1);
                           |Turtle.turnRight(120);
                           |koch(laenge / 3, tiefe - 1);
                           |Turtle.turnLeft(60);
                           |koch(laenge / 3, tiefe - 1);""".stripMargin

  private val snowflakeMain = """koch(270, 2);
                                |Turtle.turnRight(120);
                                |koch(270, 2);
                                |Turtle.turnRight(120);
                                |koch(270, 2);
                                |Turtle.turnRight(120);""".stripMargin

  List("k2" -> List(kochBody), "k4" -> List(kochBody, snowflakeMain)).foreach { (id, bodies) =>
    test(s"$id solved with the sample Koch method draws the target, also after switching through Snap") {
      val code = bodies.foldLeft(CreateMonksWorkbook.EditorCode(id))((source, body) => source.replaceFirst("// TODO", body))
      val execution = checked(code)
      assertEquals(execution.status, JavaTurtleInterpreter.Status.Completed)
      val expected = CreateMonksWorkbook.editorTarget(id).toTurtleProgram
      assertEquals(execution.commands.map(_.toDrawingCommand).toSeq, expected)
      val snap = ProgrammingStateJavaStructured(code).convertToLanguage(SnapLanguage).finalState
        .getOrElse(fail("No Snap form for the Koch curve"))
      assertEquals(snap.toBeExpression.turtleCommandExecutionInfo.completedCommands.toSeq, expected)
      val viaPython = python(ProgrammingStateJavaStructured(code))
      assertEquals(viaPython.toBeExpression.turtleCommandExecutionInfo.completedCommands.toSeq, expected, "Python draws the Koch curve")
      Seq(Java, SnapLanguage).foreach { target =>
        val back = viaPython.convertToLanguage(target).finalState.getOrElse(fail(s"No $target form of the Python Koch curve"))
        assertEquals(back.toBeExpression.turtleCommandExecutionInfo.completedCommands.toSeq, expected, s"Python -> $target")
      }
    }
  }
}
