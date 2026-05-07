package interactionPlugins.fileSubmission.turtleLogic

import munit.FunSuite

class TurtleXmlParserUtilitySpec extends FunSuite {

  test("simplify removes pentrails and thumbnail nodes") {
    val xml =
      """<project name="x">
        |  <thumbnail>large-image-data</thumbnail>
        |  <stage>
        |    <pentrails>other-large-image-data</pentrails>
        |  </stage>
        |</project>""".stripMargin

    val simplified = TurtleXmlParser.simplify(xml)

    assert(!simplified.contains("<thumbnail>"))
    assert(!simplified.contains("<pentrails>"))
    assert(simplified.contains("<project"))
  }

  test("getGreenFlagCommands returns commands from the script containing receiveGo") {
    val xml =
      """<project name="x">
        |  <scenes select="1">
        |    <scene name="s">
        |      <stage>
        |        <sprites select="1">
        |          <sprite name="sp">
        |            <scripts>
        |              <script>
        |                <block s="forward"><l>999</l></block>
        |              </script>
        |              <script>
        |                <block s="receiveGo"></block>
        |                <block s="forward"><l>42</l></block>
        |              </script>
        |            </scripts>
        |          </sprite>
        |        </sprites>
        |      </stage>
        |    </scene>
        |  </scenes>
        |</project>""".stripMargin

    val commands = TurtleXmlParser.getGreenFlagCommands(xml)

    assertEquals(commands, List(TurtleXmlParser.ReceiveGo, TurtleXmlParser.Forward(42.0)))
  }
}
