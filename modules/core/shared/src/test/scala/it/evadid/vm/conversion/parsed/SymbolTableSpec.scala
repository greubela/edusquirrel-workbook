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
  private def context(known: Seq[KnownStructure] = Nil) = new ParseContext(CurrentlyKnownStructures.fromKnown(known))
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
    val ctx = context(Seq(KnownStructure.Variable("global", variable("global"))))
    val outer = ctx.assignVariable("x", BeDataType.Numeric)
    ctx.pushScope()
    assert(ctx.assignVariable("x", BeDataType.String) eq outer)
    val inner = ctx.defineVariable("y", BeDataType.Boolean)
    assertEquals(ctx.lookupVariable("y"), Some(inner))
    ctx.popScope()
    assertEquals(ctx.lookupVariable("y"), None)
    assertEquals(ctx.lookupVariable("global"), Some(variable("global")))
    assertEquals(ctx.definedVariables, List(variable("global"), outer, inner))
    assertEquals(ctx.snapshotStructures.variables.keySet, Set("global", "x", "y"))
  }

  test("classes are replaced by name and resolved from names or class types") {
    val ctx = context()
    ctx.registerClass("Box", clazz("Box"))
    val replacement = BeDefineClass(name("Box"), List(variable("size")), Nil)
    ctx.registerClass("Box", replacement)
    assertEquals(ctx.definedClasses, List(replacement))
    assertEquals(ctx.lookupClass("Box"), Some(replacement))
    assertEquals(ctx.lookupClass("box"), Some(replacement))
    assertEquals(ctx.lookupClass("Other"), None)
    assertEquals(ctx.classForType(BeDataType.ClassType(name("Box"))), Some(replacement))
    assertEquals(ctx.classForType(BeDataType.Numeric), None)
  }

  test("unknown functions are created once with positional parameters and cached by name") {
    val ctx = context()
    val first = ctx.resolveFunction("helper", 2)
    assertEquals(first.inputs.map(_.name), List(name("arg0"), name("arg1")))
    assertEquals(first.functionTypeInfo.isNamed, Some(name("helper")))
    assert(ctx.resolveFunction("helper", 2) eq first)
    assertEquals(ctx.definedFunctions, Nil)
  }

  test("source bindings shadow cached functions and turtle aliases") {
    val ctx = context(Seq(KnownStructure.Function("forward", function("forward", 1))))
    assertEquals(ctx.resolveFunction("forward", 1), function("forward", 1))
    ctx.reserveCallableNames(Set("forward"))
    assertEquals(ctx.sourceCallableNames, Set("forward"))
    val reserved = ctx.resolveFunction("forward", 1)
    assertEquals(reserved.functionTypeInfo.isNamed, Some(name("forward")))
    assertEquals(reserved.inputs.map(_.name), List(name("arg0")))

    ctx.defineVariable("callback", BeDataType.AnyType)
    val fresh = ctx.resolveFunction("callback", 0)
    assert(!(ctx.resolveFunction("callback", 0) eq fresh), "a callable variable must not be cached as a function")
  }

  test("registered functions can be widened to the arity used by a call") {
    val ctx = context()
    val draw = function("draw", 1)
    ctx.registerFunction("draw", draw)
    assertEquals(ctx.definedFunctions, List(draw))
    assert(ctx.ensureFunctionArity("draw", draw, 1) eq draw)
    val widened = ctx.ensureFunctionArity("draw", draw, 3)
    assertEquals(widened.inputs.size, 3)
    assertEquals(widened.inputs.drop(1).map(_.name), List(name("arg1"), name("arg2")))
    assertEquals(ctx.definedFunctions, List(widened))
    assert(ctx.resolveFunction("draw", 3) eq widened)
    assertEquals(ctx.currentStructures.functions("draw"), widened)
  }

  test("operators choose the best-typed candidate and create untyped operators for unknown symbols") {
    val ctx = new ParseContext(CurrentlyKnownStructures.fromKnown(SymbolTable.defaultKnownStructures))
    val numeric = ctx.resolveOperator("+", 2, List(literal("1"), literal("2")))
    assertEquals(numeric.outputs.map(_.variableType), Some(BeDataType.Numeric))
    val text = ctx.resolveOperator("+", 2, List(literal("\"a\""), literal("\"b\"")))
    assertEquals(text.outputs.map(_.variableType), Some(BeDataType.String))

    val custom = ctx.resolveOperator("<=>", 3, List(literal("1"), literal("2"), literal("3")))
    assertEquals(custom.inputs.map(_.name), List(name("left"), name("right"), name("arg2")))
    assert(ctx.resolveOperator("<=>", 3, Nil) eq custom)
    assertEquals(ctx.currentStructures.operators(("<=>", 3)), List(custom))
    val widened = ctx.ensureFunctionArity("<=>", custom, 4)
    assertEquals(widened.inputs.size, 4)
    assertEquals(ctx.currentStructures.functions("<=>"), widened)
  }
}
