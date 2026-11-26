package it.evadid.vm.conversion.parsed

import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.conversion.parsed.SymbolTable.{CurrentlyKnownStructures, KnownStructure, ParseContext}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import munit.FunSuite
import upickle.default.{read, write}

class SymbolTableSpec extends FunSuite {
  private def name(text: String) = BeEntityName.fromUniversalNameInParts(text)
  private def variable(text: String, dataType: BeDataType = BeDataType.Numeric) = BeDefineVariable(name(text), dataType)
  private def function(text: String, inputs: Int = 0) =
    BeDefineFunction((0 until inputs).map(i => variable(s"p$i")).toList, None, BeSequence.optionalBody(Nil), BeDefineFunction.functionInfo(name(text)))
  private def clazz(text: String) = BeDefineClass(name(text), Nil, Nil)
  private def context(known: Seq[KnownStructure] = Nil) = ParseContext.initial(CurrentlyKnownStructures.fromKnown(known), it.evadid.vm.conversion.dependencies.ProgrammingEnvironment.all)
  private def literal(text: String) = BeUseValue(BeDataValueLiteral(text), None)

  test("known structures accumulate by kind and operators are grouped by symbol and arity") {
    val plus = function("plus", 2)
    val structures = CurrentlyKnownStructures.fromKnown(Seq(
      KnownStructure.Variable("x", variable("x")),
      KnownStructure.Function("draw", function("draw")),
      KnownStructure.Operator("+", plus),
      KnownStructure.Operator("+", plus),
      KnownStructure.Class("Box", clazz("Box"))))
    assertEquals(structures.variables.keySet, Set("x"))
    assertEquals(structures.functions.keySet, Set("draw", "+"))
    assertEquals(structures.operators(("+", 2)), List(plus))
    assertEquals(structures.classes.keySet, Set("Box"))
    assertEquals(read[CurrentlyKnownStructures](write(structures)), structures)
    assertEquals(SymbolTable.defaultKnownStructures.collect { case KnownStructure.Operator(symbol, _) => symbol }.toSet.contains("+"), true)
  }

  test("variables are scoped while the defined-variable list keeps every definition") {
    val start = context(Seq(KnownStructure.Variable("global", variable("global"))))
    val (outer, withOuter) = start.assignVariable("x", BeDataType.Numeric)
    val nested = withOuter.pushScope
    val (assigned, _) = nested.assignVariable("x", BeDataType.String)
    assert(assigned eq outer)
    val (inner, withInner) = nested.defineVariable("y", BeDataType.Boolean)
    assertEquals(withInner.lookupVariable("y"), Some(inner))
    val closed = withInner.popScope
    assertEquals(closed.lookupVariable("y"), None)
    assertEquals(closed.lookupVariable("global"), Some(variable("global")))
    assertEquals(closed.definedVariables, List(variable("global"), outer, inner))
    assertEquals(closed.structures.variables.keySet, Set("global", "x", "y"))
    assertEquals(start.definedVariables, List(variable("global")), "earlier contexts are unchanged")
  }

  test("classes are replaced by name and resolved from names or class types") {
    val replacement = BeDefineClass(name("Box"), List(variable("size")), Nil)
    val ctx = context().registerClass("Box", clazz("Box")).registerClass("Box", replacement)
    assertEquals(ctx.definedClasses, List(replacement))
    assertEquals(ctx.lookupClass("Box"), Some(replacement))
    assertEquals(ctx.lookupClass("box"), Some(replacement))
    assertEquals(ctx.lookupClass("Other"), None)
    assertEquals(ctx.classForType(BeDataType.ClassType(name("Box"))), Some(replacement))
    assertEquals(ctx.classForType(BeDataType.Numeric), None)
  }

  test("unknown functions are created once with positional parameters and cached by name") {
    val (first, ctx) = context().resolveFunction("helper", 2)
    assertEquals(first.inputs.map(_.name), List(name("arg0"), name("arg1")))
    assertEquals(first.functionTypeInfo.isNamed, Some(name("helper")))
    assert(ctx.resolveFunction("helper", 2)._1 eq first)
    assertEquals(ctx.definedFunctions, Nil)
  }

  test("source bindings shadow cached functions and turtle aliases") {
    val ctx = context(Seq(KnownStructure.Function("forward", function("forward", 1))))
    assertEquals(ctx.resolveFunction("forward", 1)._1, function("forward", 1))
    val reserving = ctx.reserveCallableNames(Set("forward"))
    assertEquals(reserving.declaredCallableNames, Set("forward"))
    val (reserved, _) = reserving.resolveFunction("forward", 1)
    assertEquals(reserved.functionTypeInfo.isNamed, Some(name("forward")))
    assertEquals(reserved.inputs.map(_.name), List(name("arg0")))

    val (_, withCallback) = reserving.defineVariable("callback", BeDataType.AnyType)
    val (fresh, afterCall) = withCallback.resolveFunction("callback", 0)
    assert(!(afterCall.resolveFunction("callback", 0)._1 eq fresh), "a callable variable must not be cached as a function")
  }

  test("registered functions can be widened to the arity used by a call") {
    val draw = function("draw", 1)
    val ctx = context().registerFunction("draw", draw)
    assertEquals(ctx.definedFunctions, List(draw))
    assert(ctx.ensureFunctionArity("draw", draw, 1)._1 eq draw)
    val (widened, afterWidening) = ctx.ensureFunctionArity("draw", draw, 3)
    assertEquals(widened.inputs.size, 3)
    assertEquals(widened.inputs.drop(1).map(_.name), List(name("arg1"), name("arg2")))
    assertEquals(afterWidening.definedFunctions, List(widened))
    assert(afterWidening.resolveFunction("draw", 3)._1 eq widened)
    assertEquals(afterWidening.structures.functions("draw"), widened)
  }

  test("operators choose the best-typed candidate and create untyped operators for unknown symbols") {
    val ctx = ParseContext.initial(CurrentlyKnownStructures.fromKnown(SymbolTable.defaultKnownStructures), it.evadid.vm.conversion.dependencies.ProgrammingEnvironment.all)
    val (numeric, _) = ctx.resolveOperator("+", 2, List(literal("1"), literal("2")))
    assertEquals(numeric.outputs.map(_.variableType), Some(BeDataType.Numeric))
    val (text, _) = ctx.resolveOperator("+", 2, List(literal("\"a\""), literal("\"b\"")))
    assertEquals(text.outputs.map(_.variableType), Some(BeDataType.String))

    val (custom, withCustom) = ctx.resolveOperator("<=>", 3, List(literal("1"), literal("2"), literal("3")))
    assertEquals(custom.inputs.map(_.name), List(name("left"), name("right"), name("arg2")))
    assert(withCustom.resolveOperator("<=>", 3, Nil)._1 eq custom)
    assertEquals(withCustom.structures.operators(("<=>", 3)), List(custom))
    val (widened, afterWidening) = withCustom.ensureFunctionArity("<=>", custom, 4)
    assertEquals(widened.inputs.size, 4)
    assertEquals(afterWidening.structures.functions("<=>"), widened)
    assertEquals(afterWidening.structures.operators(("<=>", 4)), List(widened))
    assertEquals(afterWidening.structures.operators(("<=>", 3)), Nil)
    assert(afterWidening.resolveOperator("<=>", 4, Nil)._1 eq widened, "the widened operator is found under its new arity")
  }
}
