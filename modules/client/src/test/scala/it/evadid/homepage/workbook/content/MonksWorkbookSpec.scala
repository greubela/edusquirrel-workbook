package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.compilation.java.turtle.JavaTurtleCompiler
import it.evadid.vm.simulation.java.JavaTurtleInterpreter
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaStructured, ProgrammingStateSnapScalaModel}
import munit.FunSuite

import scala.scalajs.js

class MonksWorkbookSpec extends FunSuite {
  private lazy val workbook = CreateMonksWorkbook(null).createWorkbook

  /** Language-map keys of the monk workbook in order of first use, read from the element constructors. */
  private def usedKeys(value: Any): List[String] = value match {
    case id: LanguageMapContentId => if id.languageMapId == "monksworkbook" then List(id.entryKey) else Nil
    case values: IterableOnce[?] => values.iterator.toList.flatMap(usedKeys)
    case product: Product => product.productIterator.toList.flatMap(usedKeys)
    case _ => Nil
  }

  /** Keys of a monk language map in file order, lowercased like [[LanguageMapContentId]]. */
  private def mapKeys(language: String): List[String] = {
    val json = js.Dynamic.global.require("fs")
      .readFileSync(s"resources/languageMaps/eva/monksworkbook/map-$language.json", "utf8").asInstanceOf[String]
    js.Object.keys(js.JSON.parse(json).asInstanceOf[js.Object]).toList.map(_.toLowerCase)
  }

  test("each language map holds exactly the keys the workbook uses, in order of first use") {
    val used = usedKeys(workbook).distinct
    val universal = mapKeys("universal")
    // File paths are language independent; every other key is translated.
    assertEquals(universal, used.filter(universal.contains), "universal map")
    val translated = used.filterNot(universal.contains)
    List("de", "en", "dk").foreach { language =>
      val keys = mapKeys(language)
      assertEquals(keys.diff(used), Nil, s"$language: unused keys")
      assertEquals(translated.diff(keys), Nil, s"$language: missing keys")
      assertEquals(keys, translated, s"$language: key order")
    }
  }

  test("Monk choices, tables, slideshows and reorder tasks all expose shared graders") {
    import it.evadid.workbook.abstractions.WorkbookInteractionElement
    import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction
    import it.evadid.workbook.elements.interactionElements.table.AnswerTableInteraction
    import it.evadid.workbook.elements.interactionElements.slideshow.Slideshow
    import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction.ReorderMapIdInteraction
    val exercises = workbook.allChildrenFullSubtree.collect {
      case exercise: WorkbookContentChoiceInteraction => exercise
      case exercise: AnswerTableInteraction => exercise
      case exercise: Slideshow => exercise
      case exercise: ReorderMapIdInteraction => exercise
    }
    assert(exercises.nonEmpty)
    exercises.foreach(exercise => assert(exercise.gradingElements.nonEmpty, exercise.elementId))
  }
  test("the assignment tasks are graded matchings and the survey is an ungraded scale with opt-in consent") {
    import it.evadid.workbook.elements.interactionElements.basic.LabeledCheckboxInteraction
    import it.evadid.workbook.elements.interactionElements.matching.{LikertScaleInteraction, MatchingInteraction}
    val elements = workbook.allChildrenFullSubtree
    val matchings = elements.collect { case matching: MatchingInteraction => matching }
    assertEquals(matchings.map(_.elementId).toSet, Set("monks-a4-match", "monks-c1-match"))
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

  test("the workbook ends with the submission form") {
    import it.evadid.workbook.elements.displayElements.WorkbookSubmissionElement
    assertEquals(workbook.allChildrenFullSubtree.collect { case form: WorkbookSubmissionElement => form.elementId }, Seq("monks-submission"))
    assertEquals(workbook.sections.last.groupElements.last.childrenOfThisElement.last, WorkbookSubmissionElement("monks-submission"))
  }

  private val editorIds = List("e1", "e3", "e4", "e5", "e6b", "k4")
  private def checked(code: String): JavaTurtleInterpreter.Execution =
    JavaTurtleInterpreter.execute(JavaTurtleCompiler.compile(code).fold(problem => fail(problem.message), identity))

  test("all drawing tasks retain the workbook's Snap, Python and Java editor choices") {
    val tasks = workbook.allChildrenFullSubtree.collect {
      case task: TurtleRecreateShapeInteraction => task
    }
    assertEquals(tasks.map(_.elementId).toList, editorIds.map("monks-" + _))
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

  test("each editor shows the lead text that precedes it in the workbook as its instruction") {
    import it.evadid.workbook.elements.displayElements.DisplayLangMapContent
    import it.evadid.workbook.elements.structureElements.ExerciseContainer
    val pairs = workbook.allChildrenFullSubtree.collect { case container: ExerciseContainer => container.containerContent }
      .flatMap(content => content.zip(content.drop(1)))
      .collect { case (lead, task: TurtleRecreateShapeInteraction) => (lead, task) }
    assertEquals(pairs.map(_._2.elementId).toSet, editorIds.map("monks-" + _).toSet)
    pairs.foreach {
      case (lead: DisplayLangMapContent, task) => assertEquals(task.instructionText, Some(lead.content), task.elementId)
      case (other, task) => fail(s"${task.elementId} follows ${other.elementId} instead of a lead text")
    }
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
    assert(fromSnap.contains(TurtleCommand.PenUp))
    assert(fromSnap.contains(TurtleCommand.PenDown))
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

  List("k4" -> List(kochBody, snowflakeMain)).foreach { (id, bodies) =>
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
