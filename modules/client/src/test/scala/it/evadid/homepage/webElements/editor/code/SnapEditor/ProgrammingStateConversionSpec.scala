package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseState.{PythonSource, SnapXml}
import it.evadid.workbook.elements.interactionElements.programming.SnapTurtlePythonBridge
import munit.FunSuite

class ProgrammingStateConversionSpec extends FunSuite {

  private val twoScriptsXml =
    """<project name="two" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="two"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="forward"><l>100</l></block></script><script x="200" y="150"><block s="turn"><l>90</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes></project>"""

  private val waitXml =
    """<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts><script x="70" y="80"><block s="wait"><l>1</l></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""

  private def requireSnap(result: Either[String, SnapXml]): SnapXml =
    result.fold(message => fail(message), identity)

  private def requirePython(result: Either[String, PythonSource]): PythonSource =
    result.fold(message => fail(message), identity)

  test("snap to python is blocked when the project has unsupported blocks") {
    val result = ProgrammingStateConversion.snapToPython(SnapXml(waitXml))
    assert(result.isLeft, clue = result)
  }

  test("python outside the subset cannot switch to snap") {
    val result = ProgrammingStateConversion.pythonToSnap(PythonSource("print(1)\n"))
    assert(result.isLeft, clue = result)
  }

  test("snap to python to snap without an edit returns the original xml") {
    val python = requirePython(ProgrammingStateConversion.snapToPython(SnapXml(twoScriptsXml)))
    assert(python.snapBase.contains(twoScriptsXml))
    assert(python.source.contains("# @script x=70 y=80"), clue = python.source)
    assert(python.source.contains("# @script x=200 y=150"), clue = python.source)
    assertEquals(requireSnap(ProgrammingStateConversion.pythonToSnap(python)).xml, twoScriptsXml)
  }

  test("an added line in script 2 keeps both script positions") {
    val python = requirePython(ProgrammingStateConversion.snapToPython(SnapXml(twoScriptsXml)))
    val edited = python.copy(source = python.source.trim + "\nforward(5)\n")
    val xml = requireSnap(ProgrammingStateConversion.pythonToSnap(edited)).xml
    assert(xml.contains("""<script x="70" y="80">"""), clue = xml)
    assert(xml.contains("""<script x="200" y="150">"""), clue = xml)
    assert(xml.contains("""s="forward""""), clue = xml)
  }

  test("deleted markers keep the snapBase layout when statement counts still match") {
    val python = requirePython(ProgrammingStateConversion.snapToPython(SnapXml(twoScriptsXml)))
    val stripped = python.source.linesIterator.filterNot(_.trim.startsWith("# @script")).mkString("\n")
    val xml = requireSnap(ProgrammingStateConversion.pythonToSnap(python.copy(source = stripped))).xml
    assert(xml.contains("""<script x="70" y="80">"""), clue = xml)
    assert(xml.contains("""<script x="200" y="150">"""), clue = xml)
  }

  test("a custom block label survives an edited round trip") {
    val previous = snapProjectWith(
      """<block-definition s="draw square %size" type="command" category="Variables"><header></header><code></code><translations></translations><inputs><input type="%s"></input></inputs><script><block s="forward"><block var="size"/></block></script></block-definition>"""
    )
    val snap = SnapTurtlePythonBridge.applyPython(
      "def draw_square(size):\n    forward(size)\n\ndraw_square(10)\n",
      previousXml = previous
    )
    val base = requireSnap(snap)
    val python = requirePython(ProgrammingStateConversion.snapToPython(base))
    val edited = python.copy(source = python.source.replace("draw_square(10)", "draw_square(11)"))
    val xml = requireSnap(ProgrammingStateConversion.pythonToSnap(edited)).xml
    assert(xml.contains("""<block-definition s="draw square %size""""), clue = xml)
    assert(xml.contains("""<input type="%s">"""), clue = xml)
  }

  test("a broken marker does not block the switch") {
    val result = ProgrammingStateConversion.pythonToSnap(PythonSource("# @script x=abc\nforward(10)\n"))
    assert(result.isRight, clue = result)
  }

  test("two new receive_go scripts get default stacked positions") {
    val xml = requireSnap(ProgrammingStateConversion.pythonToSnap(PythonSource(
      """receive_go()
        |forward(10)
        |receive_go()
        |forward(20)
        |""".stripMargin
    ))).xml
    assert(xml.contains("""<script x="156" y="66">"""), clue = xml)
    val stackedY = 66 + 2 * 24 + 32
    assert(xml.contains(s"""<script x="156" y="$stackedY">"""), clue = xml)
  }

  test("a new script after a marked one stacks under that marker") {
    val xml = requireSnap(ProgrammingStateConversion.pythonToSnap(PythonSource(
      """# @script x=70 y=80
        |receive_go()
        |forward(10)
        |receive_go()
        |forward(30)
        |""".stripMargin
    ))).xml
    assert(xml.contains("""<script x="70" y="80">"""), clue = xml)
    val stackedY = 80 + 2 * 24 + 32
    assert(xml.contains(s"""<script x="70" y="$stackedY">"""), clue = xml)
  }

  test("for _ in range(edges) is unchanged after blocks and back") {
    val source =
      """def circ(n):
        |    edges = 4
        |    for _ in range(edges):
        |        forward(n / edges)
        |        turn_right(360 / edges)
        |
        |# @script x=156 y=66
        |receive_go()
        |goto_x_y(0, 0)
        |clear()
        |down()
        |x = 360
        |circ(x)
        |""".stripMargin
    val snap = requireSnap(ProgrammingStateConversion.pythonToSnap(PythonSource(source)))
    val python = requirePython(ProgrammingStateConversion.snapToPython(snap))
    assert(python.source.contains("for _ in range(edges):"), clue = python.source)
    assert(!python.source.contains("for 0 in range"), clue = python.source)
    assert(!python.source.contains("edges - 1 + 1"), clue = python.source)
  }

  private def snapProjectWith(definitions: String): String =
    s"""<project name="t" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="t"><notes></notes><palette><category name="Variables" color="243,118,29,1"/></palette><hidden></hidden><headers></headers><code></code><blocks>$definitions</blocks><primitives></primitives><stage name="Stage" width="480" height="360"><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1"><blocks></blocks><variables></variables><scripts></scripts></sprite></sprites></stage><variables></variables></scene></scenes></project>"""
}
