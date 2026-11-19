package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer.LineResult
import it.evadid.vm.simulation.data.ListCommand
import munit.FunSuite

class StepPanelsSpec extends FunSuite {
  private val commands = Seq(Forward(100.0), Right(90.0), PenUp[Double](), Forward(50.0), PenDown[Double](), Forward(20.0))

  test("turtle steps draw earlier strokes, highlight the current one and keep later strokes faint") {
    val start = TurtleStepPanel.scene(commands, 0)
    assert(start.lines.forall(_.result == LineResult.Upcoming))
    assertEquals(start.marker, Some(Point(0.0, 0.0)))
    val first = TurtleStepPanel.scene(commands, 1)
    assertEquals(first.lines.map(_.result), Seq(LineResult.Current, LineResult.Upcoming, LineResult.Upcoming))
    assertEquals(first.marker, Some(Point(100.0, 0.0)))
    val turn = TurtleStepPanel.scene(commands, 2)
    assertEquals(turn.lines.map(_.result), Seq(LineResult.Neutral, LineResult.Upcoming, LineResult.Upcoming), "a turn draws nothing new")
    val jump = TurtleStepPanel.scene(commands, 4)
    assertEquals(jump.lines.map(line => (line.result, line.jump)),
      Seq((LineResult.Neutral, false), (LineResult.Current, true), (LineResult.Upcoming, false)))
    val end = TurtleStepPanel.scene(commands, commands.size)
    assertEquals(end.lines.count(_.result == LineResult.Upcoming), 0)
    assertEquals(end.lines.last.result, LineResult.Current)
    assertEquals(start.lines.map(line => (line.start, line.end)), end.lines.map(line => (line.start, line.end)), "bounds stay stable")
  }

  test("turtle step captions name the command with its arguments") {
    assertEquals(TurtleStepPanel.describe(Forward(100.0)), "forward(100)")
    assertEquals(TurtleStepPanel.describe(Right(22.5)), "right(22.5)")
  }

  private val recorded = List(ListCommand.Length(3), ListCommand.Get(0, 3), ListCommand.Get(1, 1), ListCommand.Swap(0, 1),
    ListCommand.Set(2, 9), ListCommand.Get(2, 9))

  test("sorting steps show changes by default and replay the list after each one") {
    val replay = SortStepPanel.Replay(Vector(3, 1, 2), recorded, includeReads = false)
    assertEquals(replay.steps, Vector(ListCommand.Swap(0, 1), ListCommand.Set(2, 9)))
    assertEquals((0 to 2).map(replay.listAt), Seq(Vector(3, 1, 2), Vector(1, 3, 2), Vector(1, 3, 9)))
    assertEquals(replay.highlights(1), (Set.empty[Int], Set(0, 1)))
    assertEquals(replay.highlights(2), (Set.empty[Int], Set(2)))
    assertEquals(replay.count(_.isInstanceOf[ListCommand.Get]), 3)
  }

  test("sorting steps can include reads, which highlight without changing the list") {
    val replay = SortStepPanel.Replay(Vector(3, 1, 2), recorded, includeReads = true)
    assertEquals(replay.steps.size, recorded.size)
    assertEquals(replay.listAt(3), Vector(3, 1, 2))
    assertEquals(replay.highlights(3), (Set(1), Set.empty[Int]))
    assertEquals(replay.listAt(6), Vector(1, 3, 9))
  }
}
