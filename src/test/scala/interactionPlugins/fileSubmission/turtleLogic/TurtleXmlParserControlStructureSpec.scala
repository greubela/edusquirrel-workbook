package interactionPlugins.fileSubmission.turtleLogic

import munit.FunSuite

class TurtleXmlParserControlStructureSpec extends FunSuite {

  test("parse keeps doIf body as IfThen command") {
    val xml =
      """<project name="if-demo"><scenes select="1"><scene name="s"><stage><sprites select="1"><sprite name="Sprite"><scripts><script><block s="receiveGo"></block><block s="doIf"><block s="reportTrue"></block><script><block s="forward"><l>12</l></block><block s="turn"><l>30</l></block></script></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""

    val commands = TurtleXmlParser.parse(xml)

    assert(commands.contains(TurtleXmlParser.ReceiveGo))
    assert(commands.contains(TurtleXmlParser.IfThen(List(TurtleXmlParser.Forward(12.0), TurtleXmlParser.TurnRight(30.0)))))
  }

  test("parse keeps doUntil body as WhileLoop command") {
    val xml =
      """<project name="while-demo"><scenes select="1"><scene name="s"><stage><sprites select="1"><sprite name="Sprite"><scripts><script><block s="receiveGo"></block><block s="doUntil"><block s="reportTouchingObject"><l><option>mouse-pointer</option></l></block><script><block s="forward"><l>5</l></block><block s="turnLeft"><l>10</l></block></script></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""

    val commands = TurtleXmlParser.parse(xml)

    assert(commands.contains(TurtleXmlParser.ReceiveGo))
    assert(commands.contains(TurtleXmlParser.WhileLoop(List(TurtleXmlParser.Forward(5.0), TurtleXmlParser.TurnLeft(10.0)))))
  }
}
