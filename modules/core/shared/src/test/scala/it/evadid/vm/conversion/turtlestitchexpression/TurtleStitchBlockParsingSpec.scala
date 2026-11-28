package it.evadid.vm.conversion.turtlestitchexpression

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import munit.FunSuite

class TurtleStitchBlockParsingSpec extends FunSuite {
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

  private def python(xml: String): String = ExpressionToPython.printedPython(TurtleStitchToBeExpressionParser.parseProjectWithLayout(SourceParsing.parse(xml, SourceLanguage.Snap).project.toTry.get).expression)

  test("a well-formed project parses every supported block kind") {
    val code = python(project)
    Seq("size: Any = 10", "size = size + 5", "if size > 3:", "turtle.forward(size)", "if not False:", "turtle.right(90)", "turtle.left(45)",
      "for i in range(1, 4):", "turtle.forward(i * 2)", "while not (size == 0):", "turtle.color(\"red\")", "turtle.goto(1, 5 - 2)").foreach { part =>
      assert(code.contains(part), s"'$part' missing in\n$code")
    }
  }

  private def scriptProject(blocks: String, definitions: String = "") =
    s"""<project><scenes select="1"><scene><blocks>$definitions</blocks><stage><sprites select="1"><sprite><scripts><script><block s="receiveGo"/>$blocks</script></scripts></sprite></sprites></stage></scene></scenes></project>"""

  test("blocks with empty slots use Snap's defaults instead of failing") {
    val code = python(scriptProject(
      """<block s="doRepeat"/><block s="doFor"/><block s="doIf"/><block s="doIfElse"/><block s="doUntil"/>""" +
        """<block s="doSetVar"/><block s="doChangeVar"/><block s="forward"><block s="reportGetVar"><l>size</l></block></block>"""))
    Seq("for _ in range(0):", "for i in range(1, 2):", "if False:", "while True:", "x: Any = 0", "x = x + 1", "turtle.forward(size)").foreach { part =>
      assert(code.contains(part), s"'$part' missing in\n$code")
    }
  }

  test("custom block definitions keep boolean slots, unnamed slots and colour arguments") {
    val definition = """<block-definition s="draw %'flag' %'shade'" type="command" category="pen"><inputs><input type="%b"></input><input type="%clr"></input></inputs>""" +
      """<script><block s="forward"><l>1</l></block></script></block-definition>"""
    val code = python(scriptProject("""<custom-block s="draw %b %clr"><l><bool>true</bool></l><color>0,0,255,1</color></custom-block>""", definition))
    assert(code.contains("def draw(flag: bool, shade"), code)
    assert(code.contains("draw(True"), code)
    assert(code.contains("blue") || code.contains("0, 0, 255"), code)
  }

  test("empty Snap C-slots print as pass so the Python stays valid") {
    val xml = scriptProject("""<block s="doRepeat"><l>3</l><script></script></block><block s="doIf"><l><bool>true</bool></l><script></script></block>""")
    val code = it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw(
      it.evadid.core.datastructures.language.AppLanguage.SnapLanguage, xml).toPython.code
    val lines = code.linesIterator.toVector
    lines.zipWithIndex.filter(_._1.trim.endsWith(":")).foreach { (line, index) =>
      val next = lines.lift(index + 1).getOrElse("")
      assert(next.takeWhile(_ == ' ').length > line.takeWhile(_ == ' ').length, s"'$line' has no indented body in\n$code")
    }
  }
}
