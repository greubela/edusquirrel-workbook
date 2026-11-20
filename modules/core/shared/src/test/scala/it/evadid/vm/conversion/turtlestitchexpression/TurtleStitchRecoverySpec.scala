package it.evadid.vm.conversion.turtlestitchexpression

import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import munit.FunSuite

class TurtleStitchRecoverySpec extends FunSuite {
  private val script =
    """<script x="40" y="60"><block s="receiveGo"></block>""" +
      """<block s="doSetVar"><l>size</l><l>10</l></block>""" +
      """<block s="doChangeVar"><l>size</l><l>5</l></block>""" +
      """<block s="doIf"><block s="reportVariadicGreaterThan"><list><block var="size"/><l>3</l></list></block><script><block s="forward"><block var="size"/></block></script></block>""" +
      """<block s="doIfElse"><block s="reportNot"><block s="reportBoolean"><l><bool>false</bool></l></block></block><script><block s="turn"><l>90</l></block></script><script><block s="turnLeft"><l>45</l></block></script></block>""" +
      """<block s="doFor"><l>i</l><l>1</l><l>3</l><script><block s="forward"><block s="reportVariadicProduct"><list><block var="i"/><l>2</l></list></block></block></script></block>""" +
      """<block s="doUntil"><block s="reportVariadicEquals"><list><block var="size"/><l>0</l></list></block><script><block s="doSetVar"><l>size</l><l>0</l></block></script></block>""" +
      """<block s="setColor"><color>255,0,0,1</color></block>""" +
      """<block s="gotoXY"><l>1</l><block s="reportDifference"><l>5</l><l>2</l></block></block></script>"""

  private val project =
    s"""<project name="p" app="TurtleStitch" version="2"><scenes select="1"><scene name="p"><stage name="Stage"><sprites select="1"><sprite name="Turtle"><scripts>$script</scripts></sprite></sprites></stage><variables><variable name="size"><l>0</l></variable></variables></scene></scenes></project>"""

  private def python(xml: String): String = ExpressionToPython.printedPython(TurtleStitchToBeExpressionParser.parseXml(xml))

  test("a well-formed project parses every supported block kind") {
    val code = python(project)
    Seq("size: Any = 10", "size = size + 5", "if size > 3:", "turtle.forward(size)", "if not False:", "turtle.right(90)", "turtle.left(45)",
      "for i in range(1, 3 + 1):", "turtle.forward(i * 2)", "while not (size == 0):", "turtle.color(\"red\")", "turtle.goto(1, 5 - 2)").foreach { part =>
      assert(code.contains(part), s"'$part' missing in\n$code")
    }
  }

  test("an unterminated project recovers the same program from its script text") {
    val truncated = project.takeWhile(_ != '\u0000').stripSuffix("</project>").replace("</scenes>", "").replace("</scene>", "")
      .replace("</stage>", "").replace("</sprites>", "").replace("</sprite>", "") + "<sprite"
    val recovered = TurtleStitchToBeExpressionParser.parseXmlWithLayout(truncated)
    assertEquals(ExpressionToPython.printedPython(recovered.expression), python(project))
    assertEquals(recovered.canvasLayout.scripts.map(s => (s.x, s.y)), List((40, 60)))
  }

  test("loose blocks without any script element are still recovered") {
    val loose = """<project><block s="forward"><l>15</l></block><block s="turn"><l>30</l></block>"""
    val code = ExpressionToPython.printedPython(TurtleStitchToBeExpressionParser.parseXml(loose))
    assert(code.contains("forward(15)") && code.contains("turtle.right(30)"), code)
  }
}
