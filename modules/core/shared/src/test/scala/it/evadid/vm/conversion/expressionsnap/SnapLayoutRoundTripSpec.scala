package it.evadid.vm.conversion.expressionsnap

import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchToBeExpressionParser
import munit.FunSuite

class SnapLayoutRoundTripSpec extends FunSuite {
  private def project(scripts: String) =
    s"""<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts>$scripts</scripts></sprite></sprites></stage></scene></scenes></project>"""

  test("green-flag and detached scripts keep their canvas positions through Snap export") {
    val xml = project(
      """<script x="20" y="30"><block s="receiveGo"/><block s="forward"><l>10</l></block></script>""" +
        """<script x="300" y="40"><block s="turn"><l>90</l></block><block s="forward"><l>5</l></block></script>""")
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val exported = SnapProjectXml.toXml(parsed.expression, canvasLayout = parsed.canvasLayout)
    val reparsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(exported)
    assertEquals(reparsed.expression, parsed.expression)
    assertEquals(reparsed.canvasLayout, parsed.canvasLayout)
    assert(exported.contains("""<script x="300" y="40">"""), exported)
  }
}
