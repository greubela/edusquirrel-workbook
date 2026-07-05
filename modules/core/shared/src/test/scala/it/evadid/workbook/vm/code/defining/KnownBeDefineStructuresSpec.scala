package it.evadid.workbook.vm.code.defining

import it.evadid.workbook.vm.types.BeDataType
import munit.FunSuite

class KnownBeDefineStructuresSpec extends FunSuite {
  test("known definition cache returns all function overloads associated with an entity name") {
    val plusDefinitions = KnownBeDefineStructures.byName("+").collect { case fn: BeDefineFunction => fn }
    assert(plusDefinitions.size >= 5)
    assert(plusDefinitions.forall(_.functionTypeInfo.displayName.universalInterpretation() == "+"))
  }

  test("known definition cache keeps classes and functions queryable by name") {
    assert(KnownBeDefineStructures.byName("string").exists(_.isInstanceOf[BeDefineClass]))
    assert(KnownBeDefineStructures.byName("forward").exists(_.isInstanceOf[BeDefineFunction]))
  }

  test("turtle commands are methods on the turtle class") {
    val turtleClass = KnownBeDefineStructures.byName("turtle").collectFirst { case cls: BeDefineClass => cls }.get
    val forward = KnownBeDefineStructures.byName("forward").collectFirst { case fn: BeDefineFunction => fn }.get

    assert(turtleClass.methods.contains(forward))
    assertEquals(forward.functionTypeInfo.isMethodInClass.map(_.name.universalInterpretation()), Some(turtleClass.name.universalInterpretation()))
    assert(!KnownBeDefineStructures.functions.contains(forward))
  }

  test("operator definitions are actual BeDefineFunctions with multiple typed alternatives") {
    val comparisonDefinitions = KnownBeDefineStructures.byName(">").collect { case fn: BeDefineFunction => fn }
    assert(comparisonDefinitions.size >= 2)
    assert(comparisonDefinitions.forall(_.inputs.size == 2))
  }

  test("all cached classes are named and retrievable") {
    KnownBeDefineStructures.classes.foreach { clazz =>
      val universalName = clazz.name.universalInterpretation()

      assert(universalName.nonEmpty)
      assert(KnownBeDefineStructures.byName(universalName).contains(clazz))
      assert(clazz.staticInformationExpression.syntaxErrors.isEmpty)
    }
  }

  test("turtle class is a sensible class definition with fully formed methods") {
    val turtleClass = KnownBeDefineStructures.byName("turtle").collectFirst { case cls: BeDefineClass => cls }.get
    val expectedMethodNames = Set("forward", "backward", "left", "right", "pen_up", "pen_down", "circle", "goto", "setheading", "speed", "color", "pensize")
    val actualMethodNames = turtleClass.methods.map(_.functionTypeInfo.displayName.universalInterpretation()).toSet

    assertEquals(actualMethodNames, expectedMethodNames)
    assert(turtleClass.methods.nonEmpty)
    assert(turtleClass.attributes.isEmpty)
    assert(turtleClass.bodyExtras.isEmpty)
    assert(turtleClass.staticInformationExpression.syntaxErrors.isEmpty)

    turtleClass.methods.foreach { method =>
      assertEquals(method.functionTypeInfo.isMethodInClass.map(_.name.universalInterpretation()), Some(turtleClass.name.universalInterpretation()))
      assert(method.functionTypeInfo.isNamed.nonEmpty)
      assert(method.functionTypeInfo.displayName.universalInterpretation().nonEmpty)
      assert(method.outputs.nonEmpty)
      assertEquals(method.outputs.map(_.variableType), Some(BeDataType.Unit))
      assert(KnownBeDefineStructures.methods.contains(method))
      assert(!KnownBeDefineStructures.functions.contains(method))
      assert(KnownBeDefineStructures.byName(method.functionTypeInfo.displayName.universalInterpretation()).contains(method))
    }
  }

  test("turtle method parameter definitions preserve expected names and types") {
    val methodsByName = KnownBeDefineStructures.byName("turtle").collectFirst { case cls: BeDefineClass => cls }.get.methods
      .map(method => method.functionTypeInfo.displayName.universalInterpretation() -> method)
      .toMap

    assertEquals(methodsByName("forward").inputs.map(_.name.universalInterpretation()), List("distance"))
    assertEquals(methodsByName("forward").inputs.map(_.variableType), List(BeDataType.Numeric))
    assertEquals(methodsByName("goto").inputs.map(_.name.universalInterpretation()), List("x", "y"))
    assertEquals(methodsByName("goto").inputs.map(_.variableType), List(BeDataType.Numeric, BeDataType.Numeric))
    assertEquals(methodsByName("color").inputs.map(_.name.universalInterpretation()), List("value"))
    assertEquals(methodsByName("color").inputs.map(_.variableType), List(BeDataType.String))
    assert(methodsByName("pen_up").inputs.isEmpty)
    assert(methodsByName("pen_down").inputs.isEmpty)
  }

  test("standalone builtin functions are fully named and separate from turtle methods") {
    val builtinNames = KnownBeDefineStructures.functions.map(_.functionTypeInfo.displayName.universalInterpretation()).toSet

    assert(builtinNames.contains("str"))
    assert(builtinNames.contains("print"))
    assert(!builtinNames.contains("forward"))
    KnownBeDefineStructures.functions.foreach { function =>
      assert(function.functionTypeInfo.isMethodInClass.isEmpty)
      assert(function.functionTypeInfo.isNamed.nonEmpty)
      assert(function.functionTypeInfo.displayName.universalInterpretation().nonEmpty)
      assert(function.outputs.nonEmpty)
      assert(KnownBeDefineStructures.byName(function.functionTypeInfo.displayName.universalInterpretation()).contains(function))
    }
  }

  test("operator overloads keep names, parameters, outputs, and lookup coverage") {
    KnownBeDefineStructures.operators.foreach { operator =>
      val name = operator.functionTypeInfo.displayName.universalInterpretation()

      assert(name.nonEmpty)
      assert(operator.functionTypeInfo.isNamed.nonEmpty)
      assert(operator.inputs.nonEmpty)
      assert(operator.outputs.nonEmpty)
      assert(operator.inputs.forall(input => input.name.universalInterpretation().nonEmpty))
      assert(KnownBeDefineStructures.byName(name).contains(operator))
    }

    assert(KnownBeDefineStructures.byName("+").collect { case fn: BeDefineFunction => fn }.exists(_.inputs.map(_.variableType) == List(BeDataType.String, BeDataType.String)))
    assert(KnownBeDefineStructures.byName("+").collect { case fn: BeDefineFunction => fn }.exists(_.inputs.map(_.variableType) == List(BeDataType.Numeric, BeDataType.Numeric)))
    assert(KnownBeDefineStructures.byName("not in").collect { case fn: BeDefineFunction => fn }.exists(_.outputs.exists(_.variableType == BeDataType.Boolean)))
  }

  test("operator aliases resolve to the same cached definitions") {
    assertEquals(KnownBeDefineStructures.byName("&&"), KnownBeDefineStructures.byName("and"))
    assertEquals(KnownBeDefineStructures.byName("||"), KnownBeDefineStructures.byName("or"))
    assertEquals(KnownBeDefineStructures.byName("!"), KnownBeDefineStructures.byName("not"))
  }
}
