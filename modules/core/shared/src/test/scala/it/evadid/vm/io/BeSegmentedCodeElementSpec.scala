package it.evadid.vm.io

import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.controlflow.ControlFlowType.{ControlFlowDown, RepeatBranch}
import it.evadid.vm.io.BeSegmentedCodeElement.*
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeDataValueLiteral, BeScope}
import munit.FunSuite
import upickle.default.{read, write}

class BeSegmentedCodeElementSpec extends FunSuite {
  private val info = BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())
  private val expression = BeExpressionLine(ControlFlowDown(), BeExpressionReference(info, BeUseValue(BeDataValueLiteral("1"), None)))
  private val control = BeControlFlowLine(RepeatBranch())
  private val segment = BeSegment(Some(RepeatBranch()), info, Seq(control, BeSegment(None, info, Seq(expression))))

  test("segments flatten nested lines and keep only expression lines as expressions") {
    assertEquals(segment.allLines(), Seq(control, expression))
    assertEquals(segment.allLinesWithExpressions, Seq(expression))
    assertEquals(control.getExpression, None)
    assertEquals(expression.getExpression.map(_.expr), Some(BeUseValue(BeDataValueLiteral("1"), None)))
    assertEquals(control.allLinesWithExpressions, Seq.empty)
  }

  test("every segment kind survives the tagged codec") {
    List[BeSegmentedCodeElement](control, expression, segment).foreach { value =>
      assertEquals(read[BeSegmentedCodeElement](write(value)), value)
    }
    val unknown = write[BeSegmentedCodeElement](control).replace("control-flow", "unknown")
    val error = intercept[Exception](read[BeSegmentedCodeElement](unknown))
    assert(Iterator.iterate[Throwable](error)(_.getCause).takeWhile(_ != null).exists(_.getMessage == "Unknown code segment: unknown"))
  }

  test("rendering lines expose the static information of their expression") {
    val withExpression = BeRenderingLine(1, null, BeUseValue(BeDataValueLiteral("1"), None), Some(BeUseValue(BeDataValueLiteral("2"), None)))
    assertEquals(withExpression.staticInfo.staticValue, Some(BeDataValueLiteral("2")))
    assertEquals(withExpression.copy(associatedLineExpression = None).staticInfo.staticValue, None)
  }
}
