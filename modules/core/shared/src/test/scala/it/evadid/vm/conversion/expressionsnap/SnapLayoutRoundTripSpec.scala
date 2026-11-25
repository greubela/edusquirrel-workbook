package it.evadid.vm.conversion.expressionsnap

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchToBeExpressionParser
import munit.FunSuite

class SnapLayoutRoundTripSpec extends FunSuite {
  private def project(scripts: String) =
    s"""<project><scenes select="1"><scene><stage><sprites select="1"><sprite><scripts>$scripts</scripts></sprite></sprites></stage></scene></scenes></project>"""

  test("green-flag and detached scripts keep their canvas positions through Snap export") {
    val xml = project(
      """<script x="20" y="30"><block s="receiveGo"/><block s="forward"><l>10</l></block></script>""" +
        """<script x="300" y="40"><block s="turn"><l>90</l></block><block s="forward"><l>5</l></block></script>""")
    val parsed = TurtleStitchToBeExpressionParser.parseProjectWithLayout(SourceParsing.parse(xml, SourceLanguage.Snap).project)
    val exported = SnapProjectXml.toXml(parsed.expression, canvasLayout = parsed.canvasLayout)
    val reparsed = TurtleStitchToBeExpressionParser.parseProjectWithLayout(SourceParsing.parse(exported, SourceLanguage.Snap).project)
    assertEquals(reparsed.expression, parsed.expression)
    assertEquals(reparsed.canvasLayout, parsed.canvasLayout)
    assert(exported.contains("""<script x="300" y="40">"""), exported)
  }

  test("without a saved layout a detached script stays detached and does not run on the green flag") {
    val xml = project(
      """<script x="20" y="30"><block s="receiveGo"/><block s="forward"><l>10</l></block></script>""" +
        """<script x="300" y="40"><block s="turn"><l>90</l></block></script>""")
    // The semantic Snap reader keeps detached scripts in unusedCode; the raw TurtleStitch reader keeps them in order plus layout.
    val exported = SnapProjectXml.toXml(SourceParsing.parse(xml, SourceLanguage.Snap).parsed.expression)
    assertEquals("<script ".r.findAllIn(exported).size, 2, exported)
    def commands(snap: String) = it.evadid.workbook.elements.interactionElements.programming.state
      .ProgrammingStateRaw(it.evadid.core.datastructures.language.AppLanguage.SnapLanguage, snap).toBeExpression.turtleCommandExecutionInfo.completedCommands
    assertEquals(commands(exported), commands(xml))
    assertEquals(commands(xml).map(_.args), List(List(10.0)))
  }
}
