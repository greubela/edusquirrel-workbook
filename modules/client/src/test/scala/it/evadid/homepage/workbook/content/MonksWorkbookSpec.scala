package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.German
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStatePythonString
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.elements.interactionElements.slideshow.Slideshow
import it.evadid.workbook.elements.displayElements.WorkbookImageElement.LanguageMapBasedWorkbookImageElement
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class MonksWorkbookSpec extends FunSuite {
  private def factory = CreateMonksWorkbook(null)

  test("all written PDF tasks have independent empty response fields with stable IDs") {
    val creator = factory
    val workbook = creator.createWorkbook
    val responses = workbook.allChildrenFullSubtree.collect { case text: TextInteraction => text }
    val expected = List("1b", "1c", "1d", "1e", "1f", "1g",
      "2b", "2c", "2d", "2e", "2f", "2g", "2h", "2i", "2j").map("monks-answer-" + _)
    assertEquals(responses.map(_.elementId), expected)
    assert(responses.forall(_.defaultValue.isEmpty))
    assertEquals(workbook.metadata.availableLanguages, List(German))
    assertEquals(workbook.sections.map(_.elementId), List("monks-story", "monks-recursion", "monks-koch"))
    assert(creator.createEverything.loadedWorkbook eq workbook)
    assertEquals(factory.createWorkbook, workbook)
    val ids = (workbook :: workbook.allChildrenFullSubtree).map(_.elementId)
    assertEquals(ids.distinct.size, ids.size)
  }

  test("every story frame has only a dialogue pane and one speaking or silent beat") {
    val slideshows = factory.createWorkbook.allChildrenFullSubtree.collect { case s: Slideshow => s }
    val stories = List(CreateMonksWorkbook.theaterScenes, CreateMonksWorkbook.countingScenes)
    assertEquals(slideshows.map(_.elementId), List("monks-theater", "monks-counting-story"))
    slideshows.zip(stories).foreach { (show, scenes) =>
      assertEquals(show.childrenOfThisElement.map(_.elementId), scenes.map(s => "monks-panel-" + s.id))
      assert(show.childrenOfThisElement.forall(_.isInstanceOf[LanguageMapBasedWorkbookImageElement]))
      scenes.foreach { scene =>
        assert(scene.speaker.isEmpty || scene.action.isEmpty)
        assert(scene.speaker.nonEmpty || scene.dialogueKey == "silence")
      }
      show.childrenOfThisElement.collect { case p: LanguageMapBasedWorkbookImageElement => p }.zip(scenes).foreach { (panel, scene) =>
        assertEquals(panel.description, Some(it.evadid.core.datastructures.language.LanguageMapContentId("monksworkbook/" + scene.dialogueKey)))
      }
    }
    // The only reused artwork is a repeated identical line or a silent pause.
    stories.flatten.groupBy(_.imageKey).values.foreach { uses =>
      assertEquals(uses.map(_.dialogueKey).distinct.size, 1)
    }
  }

  test("each retained card is selected and inserted in its own ordered frames") {
    val ids = CreateMonksWorkbook.theaterScenes.map(_.id)
    for (card <- List(9, 7, 4, 2)) {
      val selection = List("before", "lifting", "held").map(phase => ids.indexOf(s"select-$card-$phase"))
      val placement = List("right", "moving", "left").map(phase => ids.indexOf(s"insert-$card-$phase"))
      assert(selection.forall(_ >= 0) && selection == selection.sorted)
      assert(placement.forall(_ >= 0) && placement == placement.sorted)
      assert(selection.last < ids.indexOf("empty"))
      assert(placement.head > ids.indexOf("giveback"))
    }
    assert(ids.indexOf("insert-2-left") < ids.indexOf("insert-4-right"))
    assert(ids.indexOf("insert-4-left") < ids.indexOf("insert-7-right"))
    assert(ids.indexOf("insert-7-left") < ids.indexOf("insert-9-right"))
    assert(ids.indexOf("insert-9-left") < ids.indexOf("sorted"))
  }

  test("the tower story separates removing, placing and counting every brick") {
    val ids = CreateMonksWorkbook.countingScenes.map(_.id)
    assertEquals(ids.takeRight(10), List("count-remove", "count-place", "count-tally",
      "count-remove-yellow", "count-place-yellow", "count-tally-two",
      "count-remove-blue", "count-place-blue", "count-tally-three", "count-empty"))
    assert(ids.indexOf("family-pause") < ids.indexOf("count-paper"))
  }

  test("Koch practice offers separate editable targets with 1, 4 and 16 connected segments") {
    val exercises = factory.createWorkbook.allChildrenFullSubtree.collect {
      case e: it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction => e
    }
    assertEquals(exercises.map(_.elementId), List("monks-koch-0", "monks-koch-1", "monks-koch-2"))
    exercises.zipWithIndex.foreach { (exercise, depth) =>
      val target = CreateMonksWorkbook.kochTarget(depth)
      assertEquals(exercise.desiredResult, target)
      assertEquals(target.lines.size, math.pow(4, depth).toInt)
      assertEquals(target.lines.head.start, it.evadid.core.datastructures.geometry.Point(0.0, 0.0))
      assertEquals(target.lines.last.end, it.evadid.core.datastructures.geometry.Point(270.0, 0.0))
      target.lines.sliding(2).foreach { pair => if (pair.size == 2) assertEquals(pair.head.end, pair(1).start) }
      target.lines.foreach { line =>
        assertEqualsDouble(math.hypot(line.end.x - line.start.x, line.end.y - line.start.y),
          270 / math.pow(3, depth), 1e-8)
      }
      assertEquals(exercise.availablePalette,
        ProgrammingEditorPalette.PythonCompatibleSnap)
      assert(exercise.initProgram.toBeExpressionState.deriveTurtleCommands.isEmpty)
    }
    val peak = CreateMonksWorkbook.kochTarget(1).lines(1).end
    assertEqualsDouble(peak.x, 135.0, 1e-8)
    assertEqualsDouble(peak.y, -45 * math.sqrt(3), 1e-8)
  }

  test("the same recursive Python function draws all three Koch targets") {
    val function = """def koch(length, depth):
      |    if depth == 0:
      |        forward(length)
      |    else:
      |        koch(length / 3, depth - 1)
      |        turn(-60)
      |        koch(length / 3, depth - 1)
      |        turn(120)
      |        koch(length / 3, depth - 1)
      |        turn(-60)
      |        koch(length / 3, depth - 1)
      |""".stripMargin
    for (depth <- 0 to 2) {
      val commands = ProgrammingStatePythonString(
        function + s"koch(270, $depth)\n").toBeExpressionState.deriveTurtleCommands
      val expected = CreateMonksWorkbook.kochTarget(depth).lines.map(line =>
        it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.LineToRender(line.start, line.end))
      val result = it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.buildScene(commands, expected)
      assertEquals(result.lines.size, expected.size)
      assert(result.lines.forall(_.result ==
        it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.LineResult.Correct))
    }
  }

  test("the full digital workbook preserves panels and responses through JSON serialization") {
    val original = factory.createWorkbook
    val serializer = WorkbookElementFactory.serializerRegularJsonWorkbook
    val restored = serializer.deserialize(serializer.serialize(original))
    assertEquals(restored, original)
    assertEquals(restored.allContainedInteractions.map(_.elementId), original.allContainedInteractions.map(_.elementId))
  }
}
