package it.evadid.vm.conversion.snapexpression

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.vm.conversion.snapxml.SnapXmlParser
import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchToBeExpressionParser
import munit.FunSuite

class SnapDetachedRoundTripSpec extends FunSuite {
  private val source = """<project><scenes><scene><blocks/><variables/><stage><sprites><sprite><scripts>
    |<script x="50" y="60"><block s="receiveGo"/><block s="forward"><l>10</l></block></script>
    |<script x="50" y="240"><block s="forward"><l>99</l></block></script>
    |</scripts></sprite></sprites></stage></scene></scenes></project>""".stripMargin

  test("detached scripts remain unexecuted through Python and Java") {
    val original = ProgrammingStateRaw(SnapLanguage, source).toBeExpression
    assertEquals(original.turtleCommandExecutionInfo.completedCommands.map(_.args.head), List(10.0))
    for converted <- Seq(original.toPython.toBeExpression, original.toJava.toBeExpression) do {
      val snap = converted.toSnapXml
      assert(!snap.code.contains("block-definition s=\"unused"), snap.code)
      val layout = TurtleStitchToBeExpressionParser.parseXmlWithLayout(snap.code).canvasLayout
      assertEquals(layout.scripts.count(_.hasReceiveGo), 1)
      assertEquals(layout.scripts.count(!_.hasReceiveGo), 1)
      assertEquals(snap.toBeExpression.turtleCommandExecutionInfo.completedCommands.map(_.args.head), List(10.0))
      assertEquals(snap.toPython.toBeExpression.toPython.code, original.toPython.code)
    }
  }

  test("a project containing only loose blocks does not gain startup commands") {
    val loose = ProgrammingStateRaw(SnapLanguage, source.replace("<block s=\"receiveGo\"/>", "")).toBeExpression.toSnapXml
    val layout = TurtleStitchToBeExpressionParser.parseXmlWithLayout(loose.code).canvasLayout
    assert(layout.scripts.forall(!_.hasReceiveGo), loose.code)
    assertEquals(loose.toBeExpression.turtleCommandExecutionInfo.completedCommands, Nil)
  }

  test("the reserved detached-code function cannot become a callable Snap block") {
    val code = ProgrammingStateRaw(Python, "def unused_code():\n    forward(3)\nunused_code()\n")
    intercept[Exception](code.toSnapXml)
  }
}
