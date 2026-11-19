package it.evadid.vm.conversion.pythonexpression

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.errors.{BeExpressionUnsupported, BeExpressionUnparsable}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.code.usage.*
import it.evadid.vm.types.*
import munit.FunSuite

class PythonObjectConversionSpec extends FunSuite {
  val source = """class Counter:
    |    def __init__(self, initial: float):
    |        self.value = initial
    |    def read(self) -> float:
    |        return self.value
    |    def next(self) -> float:
    |        return self.read()
    |    @staticmethod
    |    def zero() -> float:
    |        return 0
    |counter = Counter(3)
    |counter.value = 7
    |result = counter.next()
    |zero = Counter.zero()
    |""".stripMargin
  def nodes(expression: BeExpression): Seq[BeExpression] = expression.recToTree(false,
    BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values.collect { case BeExpressionReference(_, value) => value }.toSeq

  test("Python binds constructors, fields, receivers and static calls without source placeholders") {
    val state = ProgrammingStateRaw(Python, source).toBeExpression
    val all = nodes(state.expression)
    assert(!all.exists(value => value.isInstanceOf[BeExpressionUnsupported] || value.isInstanceOf[BeExpressionUnparsable]), state)
    val targets = all.collect { case call: BeFunctionCall => call.target }
    assert(targets.exists(_.isInstanceOf[BeCallTarget.Constructor]))
    assert(targets.count(_.isInstanceOf[BeCallTarget.InstanceMethod]) == 2)
    assert(targets.exists(_.isInstanceOf[BeCallTarget.StaticMethod]))
    assertEquals(all.count(_.isInstanceOf[BeAssignMember]), 2)
    val normalized = state.toPython
    assertEquals(normalized.toBeExpression.toPython.code, normalized.code)
    assert(normalized.code.contains("self.value = initial"), normalized.code)
    assert(normalized.code.contains("counter.next()"), normalized.code)
    intercept[Exception](state.toSnapXml)
  }

  test("inheritance and unknown decorators are retained explicitly instead of silently discarded") {
    for code <- Seq("class Derived(Base):\n    pass\n", "class Decorated:\n    @custom\n    def method(self):\n        pass\n") do {
      val all = nodes(ProgrammingStateRaw(Python, code).toBeExpression.expression)
      assert(all.exists(_.isInstanceOf[BeExpressionUnsupported]), code)
    }
  }
}
