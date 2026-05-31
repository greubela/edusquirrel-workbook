package interactionPlugins.fileSubmission.turtleLogic

import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleLogic.TurtleXmlParser
import munit.FunSuite

class TurtleXmlParserControlStructureSpec extends FunSuite {

  test("parse keeps doIf body as IfThen command") {
    val commands = TurtleXmlParser.parse(TurtleXmlTestSamples.ifDemoXml)

    assert(commands.contains(TurtleXmlParser.ReceiveGo))
    assert(commands.contains(TurtleXmlParser.IfThen(List(TurtleXmlParser.Forward(12.0), TurtleXmlParser.TurnRight(30.0)))))
  }

  test("parse keeps doUntil body as WhileLoop command") {
    val commands = TurtleXmlParser.parse(TurtleXmlTestSamples.whileDemoXml)

    assert(commands.contains(TurtleXmlParser.ReceiveGo))
    assert(commands.contains(TurtleXmlParser.WhileLoop(List(TurtleXmlParser.Forward(5.0), TurtleXmlParser.TurnLeft(10.0)))))
  }
}
