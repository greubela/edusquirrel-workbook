package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleLogic

import interactionPlugins.fileSubmission.turtleLogic.TurtleXmlTestSamples
import munit.FunSuite

class TurtleXmlParserRendererIntegrationSpec extends FunSuite {

  test("asdf TurtleStitch XML parses into expected command stream") {
    val commands = TurtleXmlParser.parse(TurtleXmlTestSamples.asdfProjectXml)

    assert(commands.contains(TurtleXmlParser.ReceiveGo))
    assert(commands.contains(TurtleXmlParser.Clear))
    assert(commands.contains(TurtleXmlParser.PenDown))
    assert(commands.contains(TurtleXmlParser.Repeat(36, List(TurtleXmlParser.Forward(20.0), TurtleXmlParser.TurnRight(10.0)))))
    assert(commands.contains(TurtleXmlParser.Repeat(18, List(TurtleXmlParser.GotoXY(0.0, 0.0), TurtleXmlParser.TurnRight(20.0), TurtleXmlParser.Forward(50.0)))))
  }

  test("asdf TurtleStitch XML produces non-empty rendered segments") {
    val commands = TurtleXmlParser.parse(TurtleXmlTestSamples.asdfProjectXml)
    val segments = TurtleRenderer.simulateSegments(commands)

    assert(segments.nonEmpty)
    assert(segments.length >= 60)
  }

  test("more TurtleStitch XML parses and keeps supported commands") {
    val commands = TurtleXmlParser.parse(TurtleXmlTestSamples.moreProjectXml)

    assert(commands.contains(TurtleXmlParser.ReceiveGo))
    assert(commands.contains(TurtleXmlParser.Clear))
    assert(commands.contains(TurtleXmlParser.PenDown))
    assert(commands.contains(TurtleXmlParser.TurnLeft(15.0)))
    assert(commands.contains(TurtleXmlParser.Forward(100.0)))
    assert(commands.contains(TurtleXmlParser.TurnRight(15.0)))
    assert(commands.exists(_ == TurtleXmlParser.Repeat(10, List(TurtleXmlParser.Forward(10.0)))) || commands.exists(_ == TurtleXmlParser.Forward(10.0)))
  }

  test("more TurtleStitch XML can be rendered into non-empty segment output") {
    val commands = TurtleXmlParser.parse(TurtleXmlTestSamples.moreProjectXml)
    val segments = TurtleRenderer.simulateSegments(commands)

    assert(segments.nonEmpty)
    assert(segments.exists(s => s.x1 != s.x2 || s.y1 != s.y2))
  }
}
