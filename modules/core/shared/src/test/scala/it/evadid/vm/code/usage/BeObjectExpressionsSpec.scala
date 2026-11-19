package it.evadid.vm.code.usage

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.*
import it.evadid.vm.code.defining.BeDefineFunction.*
import it.evadid.vm.code.others.BeReturn
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.conversion.expressionjava.ExpressionToJava
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.expressionsnap.ExpressionToSnap
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.*
import munit.FunSuite
import upickle.default.*

class BeObjectExpressionsSpec extends FunSuite {
  private def name(value: String) = BeEntityName.fromUniversalNameInParts(value)
  private def variable(value: String, dataType: BeDataType = BeDataType.Int) = BeDefineVariable(name(value), dataType)
  private def ref(value: BeDefineVariable) = BeUseValue(BeUseValueReference(value), None)
  private val field = variable("value")
  private val argument = variable("initial")
  private val owner = BeDefineClass(name("counter"), List(field), Nil)
  private val self = BeCurrentInstance(owner.name)
  private val constructor = BeDefineFunction(List(argument), None,
    BeSequence.optionalBody(List(BeAssignMember(self, field, ref(argument)))),
    BeFunctionTypeInfo(Some(owner), Some(name("initialize")), Constructor()))
  private val getter = BeDefineFunction(Nil, Some(variable("result")),
    BeSequence.optionalBody(List(BeReturn(Some(BeMemberAccess(self, field))))),
    methodFunctionInfo(owner, name("get value")))
  private val factory = BeDefineFunction(Nil, None, BeSequence.optionalBody(Nil),
    BeFunctionTypeInfo(Some(owner), Some(name("reset all")), StaticMethod()))
  private val instance = variable("counter instance", BeDataType.ClassType(owner.name))
  private val literal = BeUseValue(BeDataValueLiteral("3"), None)
  private val construct = BeFunctionCall(BeCallTarget.Constructor(constructor), Map(argument -> literal))
  private val invoke = BeFunctionCall(BeCallTarget.InstanceMethod(ref(instance), getter), Map.empty)
  private val program = BeSequence.optionalBody(List(owner.copy(methods = List(constructor, getter, factory)),
    BeAssignVariable(instance, construct), invoke, BeFunctionCall.fromDefinition(factory, Map.empty)))

  test("object syntax is emitted from the same language-neutral model") {
    val java = ExpressionToJava.convert(program).code
    val python = ExpressionToPython.convert(program).code
    assert(java.contains("Counter(int initial)"), java)
    assert(java.contains("this.value = initial;"), java)
    assert(java.contains("new Counter(3)"), java)
    assert(java.contains("counterInstance.getValue()"), java)
    assert(java.contains("Counter.resetAll()"), java)
    assert(python.contains("def __init__(self, initial: int)"), python)
    assert(python.contains("self.value = initial"), python)
    assert(python.contains("Counter(3)"), python)
    assert(python.contains("counter_instance.get_value()"), python)
    assert(python.contains("@staticmethod"), python)
    assert(python.contains("Counter.reset_all()"), python)
    assert(!python.contains("new Counter"), python)
    val returnedCall = ExpressionToJava.convert(BeReturn(Some(invoke))).code
    assertEquals(returnedCall, "return counterInstance.getValue();")
  }

  test("object trees roundtrip through JSON and MessagePack") {
    val expression: BeExpression = program
    assertEquals(read[BeExpression](write(expression)), expression)
    assertEquals(readBinary[BeExpression](writeBinary(expression)), expression)
    assertEquals(construct.staticInformationExpression.staticType, BeDataType.ClassType(owner.name))
  }

  test("method receivers participate in traversal and replacement") {
    val children = invoke.structureInfo.getChildren(false, BeScope.GlobalScope())
    assert(children.exists { case BeExpressionReference(info, expr) => info.myRoleInParent == BeChildRole.Receiver && expr == ref(instance); case _ => false })
    val replacement = invoke.structureInfo.withReplacedChildren(Map(BeChildRole.Receiver -> self)).asInstanceOf[BeFunctionCall]
    assertEquals(replacement.target.receiver, Some(self))
    assertEquals(replacement.funcDef, getter)
    val assignment = BeAssignMember(self, field, literal)
    val changed = assignment.structureInfo.withReplacedChildren(Map(BeChildRole.ValueInAssignment -> ref(argument))).asInstanceOf[BeAssignMember]
    assertEquals(changed.value, ref(argument))
    assertEquals(changed.receiver, self)
  }

  test("object types are checked without pretending to be literals or numeric values") {
    val kind = BeDataType.ClassType(owner.name)
    assertEquals(kind.canTakeValuesFrom(kind), AssigningPossibleWithSameType(kind))
    assertEquals(kind.canTakeValuesFrom(BeDataType.Int), AssigningNotPossible())
    assertEquals(BeDataType.Int.canTakeValuesFrom(kind), AssigningNotPossible())
    assert(!kind.isValidLiteral("Counter()"))
    val alternatives = BeDataType.BeUnionAllowedTypes(Set(kind, BeDataType.Int))
    assertEquals(BeDataType.typeIntersection(kind, alternatives), Some(kind))
    assertEquals(kind.canTakeValuesFrom(alternatives), AssigningPossibleWithImplicitCast(kind))
    assert(BeAssignMember(self, field, ref(instance)).staticInformationExpression.syntaxErrors.nonEmpty)
  }

  test("Snap rejects object functionality instead of dropping it") {
    intercept[Exception](ExpressionToSnap.convert(program))
    intercept[Exception](ExpressionToSnap.convert(invoke))
  }
  test("preserved source has explicit provenance and cannot leak into another language") {
    val unsupported: BeExpression = it.evadid.vm.code.errors.BeExpressionUnsupported("with resource:\n    pass", Some(Python))
    assertEquals(read[BeExpression](write(unsupported)), unsupported)
    assert(ExpressionToPython.convert(unsupported).code.contains("with resource:"))
    intercept[IllegalArgumentException](ExpressionToJava.convert(unsupported))
  }

  test("call targets reject inconsistent method kinds") {
    intercept[IllegalArgumentException](BeCallTarget.Constructor(getter))
    intercept[IllegalArgumentException](BeCallTarget.InstanceMethod(self, factory))
    intercept[IllegalArgumentException](BeCallTarget.StaticMethod(constructor))
  }

  test("the drawing simulator reports unsupported object execution") {
    val execution = it.evadid.vm.simulation.turtle.TurtleProgramAnalysis(program)
    assert(execution.executionStatus.isInstanceOf[it.evadid.vm.simulation.turtle.TurtleExecutionStatus.Failed], execution)
  }

}
