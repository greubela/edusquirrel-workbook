package it.evadid.vm.conversion.snapexpression

import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import munit.FunSuite

class SnapStateConversionSpec extends FunSuite {
  private def sprite(scripts: String, extra: String = "") = s"""<sprite name="Turtle">$extra<scripts>$scripts</scripts></sprite>"""
  private def project(sprites: String, sceneExtra: String = "", stageExtra: String = "") =
    s"""<project name="p"><scenes select="1"><scene name="s">$sceneExtra<stage name="Stage">$stageExtra<sprites select="1">$sprites</sprites></stage></scene></scenes></project>"""
  private val drawing = """<script><block s="receiveGo"/><block s="forward"><l>10</l></block><block s="turn"><l>-90.5</l></block></script>"""
  private def readExpression(xml: String) = it.evadid.vm.parsing.source.SourceParsing.parse(xml,
    it.evadid.vm.parsing.source.SourceParsing.SourceLanguage.Snap).syntax.left.map(_.message)
      .flatMap(SnapStateConversion.expressionForExecution)

  private def error(xml: String): String = readExpression(xml).swap.getOrElse(fail(s"Accepted $xml"))

  test("a single green-flag script of numeric turtle blocks is accepted for execution") {
    val expression = readExpression(project(sprite(drawing + """<comment>note</comment>""")))
    val python = expression.map(ExpressionToPython.printedPython).getOrElse(fail(expression.toString))
    assert(python.contains("forward(10)"), python)
  }

  test("projects outside the verified single-scene, single-sprite subset are rejected with a reason") {
    assertEquals(error("<blocks></blocks>"), "Unit tests need a complete Snap project.")
    assertEquals(error(s"""<project><scenes><scene><stage/></scene><scene><stage/></scene></scenes></project>"""),
      "Unit tests currently support one Snap scene.")
    assertEquals(error(s"""<project><stage/><scenes><scene><stage/></scene></scenes></project>"""),
      "A Snap project cannot mix legacy and scene-based stages for unit tests.")
    assertEquals(error(project(sprite(drawing) + sprite(""))), "Unit tests currently support one Snap sprite.")
    assertEquals(error(project(sprite(drawing, extra = "<scripts></scripts>"))), "Ambiguous Snap script containers cannot be executed by unit tests.")
    assertEquals(error(project(sprite(drawing, extra = """<variables><variable name="x"><l>1</l></variable></variables>"""))),
      "Saved Snap variables cannot yet be included in unit-test execution.")
    assertEquals(error(project(sprite(drawing, extra = """<blocks><block-definition s="jump" type="command"/></blocks>"""))),
      "Custom Snap blocks are not yet supported by unit-test execution.")
  }

  test("scripts must use one green flag, plain block content and numeric literal inputs") {
    assert(error(project(sprite("""<script><block s="receiveGo"/><block s="doBroadcast"><l>go</l></block></script>"""))).startsWith("Unsupported Snap blocks:"))
    assertEquals(error(project(sprite(drawing + drawing))), "Unit tests currently support one green-flag script.")
    assertEquals(error(project(sprite("""<script><block s="receiveGo"/><l>3</l></script>"""))), "Unsupported content in a Snap script.")
    val unverified = "These Snap blocks do not yet have a verified unit-test translation."
    assertEquals(error(project(sprite("""<script><block s="receiveGo"/><block s="forward"><block var="x"/></block></script>"""))), unverified)
    assertEquals(error(project(sprite("""<script><block s="receiveGo"/><block s="forward"><l>ten</l></block></script>"""))), unverified)
    assertEquals(error(project(sprite("""<script><block s="receiveGo"/><block s="forward"><l>1</l><l>2</l></block></script>"""))), unverified)
    assertEquals(error(project(sprite("""<script><block s="receiveGo"/><block var="x"/></script>"""))), unverified)
  }
}
