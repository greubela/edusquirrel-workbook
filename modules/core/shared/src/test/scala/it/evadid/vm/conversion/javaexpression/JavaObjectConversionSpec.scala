package it.evadid.vm.conversion.javaexpression

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.workbook.interactions.programming.state.*
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.code.usage.*
import it.evadid.vm.types.*
import munit.FunSuite

class JavaObjectConversionSpec extends FunSuite {
  val source = """class Counter {
    |    double value;
    |    Counter(double initial) { value = initial; }
    |    double read() { return this.value; }
    |    double next() { return read(); }
    |    static double zero() { return 0; }
    |}
    |class Main {
    |    public static void main(String[] args) {
    |        Counter counter = new Counter(3);
    |        counter.value = 7;
    |        double result = counter.next();
    |        double zero = Counter.zero();
    |    }
    |}
    |""".stripMargin

  test("Java constructors, implicit fields and implicit methods become neutral object expressions") {
    val original = ProgrammingStateRaw(Java, source).toBeExpression
    val normalized = original.toPython
    assert(normalized.code.contains("self.value = initial"), normalized.code)
    assert(normalized.code.contains("return self.read()"), normalized.code)
    assert(normalized.code.contains("Counter(3)"), normalized.code)
    assert(normalized.code.contains("Counter.zero()"), normalized.code)
    assertEquals(normalized.toJava.toPython.code.trim, normalized.code.trim)
    val targets = original.expression.recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope())).values.collect {
      case BeExpressionReference(_, call: BeFunctionCall) => call.target
    }.toSeq
    assert(targets.exists(_.isInstanceOf[BeCallTarget.Constructor]))
    assert(targets.exists(_.isInstanceOf[BeCallTarget.InstanceMethod]))
    assert(targets.exists(_.isInstanceOf[BeCallTarget.StaticMethod]))
  }

  test("a local parameter shadows a field while explicit this still selects the field") {
    val code = "class Counter { double value; Counter(double value) { this.value = value; } double read() { return value; } }"
    val python = ProgrammingStateRaw(Java, code).toPython.code
    assert(python.contains("self.value = value"), python)
    assert(python.contains("return self.value"), python)
  }

  test("Java keyword translation does not rewrite literals") {
    val code = "class Label { String text() { return \"true false null this new\"; } }"
    assert(ProgrammingStateRaw(Java, code).toPython.code.contains("\"true false null this new\""))
  }
  test("ordinary instance methods convert, but unsupported array parameters cannot disappear") {
    val instance = ProgrammingStateRaw(Java, "class Drawing { public void draw() { Turtle.forward(12); } }")
    assert(instance.convertToLanguage(Python).finalState.nonEmpty)
    assert(instance.convertToLanguage(SnapLanguage).finalState.isEmpty)
    val withArray = ProgrammingStateRaw(Java, "class Drawing { public void main(String[] args) { Turtle.forward(12); } }")
    assert(withArray.convertToLanguage(Python).finalState.isEmpty)
    assert(withArray.convertToLanguage(SnapLanguage).finalState.isEmpty)
  }

}
