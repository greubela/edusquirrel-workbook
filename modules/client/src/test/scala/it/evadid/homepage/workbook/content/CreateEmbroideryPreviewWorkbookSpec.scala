package it.evadid.homepage.workbook.content

import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.LineResult
import it.evadid.workbook.elements.interactionElements.Turtle.{TurtleRecreateShapeInteraction, TurtleStitchRecreateShapeInteractionLegacy}
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExercise
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStatePythonString
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class CreateEmbroideryPreviewWorkbookSpec extends FunSuite {
  private def drawn(target: TurtleGraphic) = TurtleJsxGraphRenderer.buildScene[Double](Nil, target, 1e-7, gradeJumps = false).lines.filterNot(_.jump)
  private def matches(code: String, target: TurtleGraphic): Unit = {
    val state = ProgrammingStatePythonString(code)
    val actual = state.toBeExpressionState.deriveTurtleCommands
    assert(actual.nonEmpty, "Reference program must parse and execute")
    val scene = TurtleJsxGraphRenderer.buildScene(actual, target, 1e-7, gradeJumps = false)
    val strokes = scene.lines.filterNot(_.jump)
    assert(strokes.nonEmpty)
    assert(strokes.forall(_.result == LineResult.Correct), strokes.filter(_.result != LineResult.Correct).toString)
    val snapCommands = state.toSnapXml.toBeExpressionState.deriveTurtleCommands
    val snapScene = TurtleJsxGraphRenderer.buildScene(snapCommands, target, 1e-7, gradeJumps = false)
    assert(snapScene.lines.filterNot(_.jump).forall(_.result == LineResult.Correct),
      "The same supplied program must also execute after conversion to Snap:\n" +
        state.toSnapXml.toPython.code + "\n" + snapScene.lines.filter(_.result != LineResult.Correct).take(5))
  }

  test("separate edition preserves the original and replaces every raster interaction") {
    val original = CreateEmbroideryWorkbook(null).createWorkbook
    val revised = CreateEmbroideryPreviewWorkbook(null).createWorkbook
    assertEquals(original.workbookId, "EmbroideryWorkbook")
    assertEquals(revised.workbookId, "EmbroideryPreviewWorkbook")
    assertEquals(revised.sections.map(_.elementId), List("SectionIntro", "Section1", "Section2", "Section3", "Section4", "Section5", "Section6", "Section7"))
    val elements = revised.allChildrenFullSubtree
    assert(!elements.exists(_.isInstanceOf[TurtleStitchRecreateShapeInteractionLegacy]))
    assert(original.allChildrenFullSubtree.exists(_.isInstanceOf[TurtleStitchRecreateShapeInteractionLegacy]))
    val shapes = elements.collect { case e: TurtleRecreateShapeInteraction => e }
    assertEquals(shapes.size, 30)
    assert(shapes.forall(_.availablePalette == ProgrammingEditorPalette.Embroidery))
    assertEquals(elements.map(_.elementId).distinct.size, elements.size)
    assert(elements.exists { case e: ProgrammingExercise => e.referencePython.isEmpty; case _ => false })
  }

  test("geometric equivalents retain all source shape families and pen-up gaps") {
    val counts = Map("square" -> 4, "two_squares" -> 8, "four_squares" -> 16, "five_triangles" -> 15,
      "pinwheel" -> 9, "squarewheel" -> 24, "blocky_eight" -> 24, "repeat_large" -> 30,
      "houses_larger" -> 24, "circles_larger" -> 720)
    assertEquals(EmbroideryPreviewTargets.shapeNames, counts.keySet)
    counts.foreach { (name, size) => assertEquals(drawn(EmbroideryPreviewTargets.shape(name)).size, size, name) }
    val eight = drawn(EmbroideryPreviewTargets.shape("blocky_eight")).flatMap(line => List(line.start, line.end))
    val width = eight.map(_.x).max - eight.map(_.x).min
    val height = eight.map(_.y).max - eight.map(_.y).min
    assert(math.abs(height - 2 * width) < 1e-7, "The figure eight stays vertical, as in the source")
    intercept[IllegalArgumentException](EmbroideryPreviewTargets.shape("typo"))
  }

  test("Koch levels have the intended segment counts and width") {
    for (depth <- 0 to 3; width <- List(270.0, 50 * math.pow(3, depth))) {
      val lines = drawn(EmbroideryPreviewTargets.koch(depth, width))
      assertEquals(lines.size, math.pow(4, depth).toInt)
      assert(math.abs(lines.last.end.x - width) < 1e-7)
      assert(math.abs(lines.last.end.y) < 1e-7)
      assertEquals(drawn(EmbroideryPreviewTargets.snowflake(depth)).size, 3 * math.pow(4, depth).toInt)
    }
    val branches = drawn(EmbroideryPreviewTargets.branches)
    assertEquals(branches.size, 78)
    def rounded(p: it.evadid.core.datastructures.geometry.Point[Double]) = (math.round(p.x * 1e6), math.round(p.y * 1e6))
    val segments = branches.map(l => Set(rounded(l.start), rounded(l.end)))
    assertEquals(segments.distinct.size, segments.size, "Every branch is stitched once")
  }

  test("supplied conditionals and recursion examples execute against their actual targets") {
    matches(EmbroideryPreviewTargets.conditionExample, EmbroideryPreviewTargets.alternatingShapes)
    matches(EmbroideryPreviewTargets.nestedExample, EmbroideryPreviewTargets.nestedShapes)
    matches(EmbroideryPreviewTargets.kochExample, EmbroideryPreviewTargets.koch(0, 50))
    for (depth <- 0 to 3)
      matches(EmbroideryPreviewTargets.kochExample.replace("koch(0)", s"koch($depth)"),
        EmbroideryPreviewTargets.koch(depth, 50 * math.pow(3, depth)))
  }

  test("the complete edition round-trips through both workbook serializers") {
    val workbook = CreateEmbroideryPreviewWorkbook(null).createWorkbook
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook)
  }
}
