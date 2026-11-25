package it.evadid.vm.conversion.pythonexpression

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage

import it.evadid.vm.conversion.parsed.SymbolTable
import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.language.AppLanguage.{English, German}
import it.evadid.core.datastructures.vectorShapes.svg.{TurtleCommand, TurtleStitchMode}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, KnownBeDefineStructures}
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.naming.{BeEntityName, KnownBeEntityNames, NamingStyle}
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeDataType, BeScope}
import it.evadid.vm.code.tree.BeExpressionReference
import munit.FunSuite

class CanonicalFunctionResolutionSpec extends FunSuite {
  private def calls(source: String): List[BeFunctionCall] = {
    val tree = SourceParsing.parse(source, SourceLanguage.Python).parsed().program.fullProgram
      .recToTree(false, BeChildInfo(BeChildRole.NoRole, BeScope.GlobalScope()))
    tree.values.collect { case BeExpressionReference(_, call: BeFunctionCall) => call }.toList
  }
  private def name(call: BeFunctionCall): String = call.funcDef.functionTypeInfo.displayName.universalInterpretation()

  test("text and entity aliases reuse the prepared turtle names and methods") {
    val forward = TurtleCommand.resolveTurtleMethod("forward").get
    List("fd", "forward", "turtle.fd", "Turtle.forward").foreach { alias =>
      assert(TurtleCommand.resolveCanonicalNames(alias).get eq forward.functionTypeInfo.displayName)
      assert(TurtleCommand.resolveTurtleMethod(alias).get eq forward)
      assert(TurtleCommand.resolveTurtleMethod(BeEntityName.fromLiteral(alias)).get eq forward)
    }
    assertEquals(TurtleCommand.resolveCanonicalNames("turtle.turnRight").map(_.universalInterpretation()), Some("right"))
    List("robot.forward", "my_turtle.fd", "unknown", "turtle.forward.extra").foreach { unknown =>
      assertEquals(TurtleCommand.resolveCanonicalNames(unknown), None)
      assertEquals(TurtleCommand.resolveTurtleMethod(unknown), None)
    }
  }

  test("the known-definition registry and typed commands share one turtle definition") {
    val cls = TurtleCommand.turtleClass
    assert(KnownBeDefineStructures.byName("turtle").collectFirst { case c: BeDefineClass => c }.get eq cls)
    assert(cls.staticInformationExpression.syntaxErrors.isEmpty)
    cls.methods.foreach { method =>
      assert(TurtleCommand.resolveTurtleMethod(method.functionTypeInfo.displayName).get eq method)
      assert(method.functionTypeInfo.isMethodInClass.nonEmpty)
    }
    assert(TurtleCommand.Forward(1).name eq TurtleCommand.resolveCanonicalNames("fd").get)
    assert(TurtleStitchMode.Running.name eq TurtleCommand.resolveCanonicalNames("running_stitch").get)
  }

  test("Python calls bind to typed turtle methods under their source aliases") {
    val parsed = calls("fd(10)\nturtle.forward(20)\nrt(90)")
    assertEquals(parsed.map(name).sorted, List("forward", "forward", "right"))
    parsed.foreach { call =>
      assert(call.funcDef eq TurtleCommand.resolveTurtleMethod(name(call)).get)
      assertEquals(call.funcDef.inputs.map(_.variableType), List(BeDataType.Numeric))
    }
  }

  test("source declarations and recursive calls retain their names even before a definition") {
    val parsed = calls("fd(2)\ndef fd(n):\n    if n > 0:\n        fd(n - 1)\nfd(1)")
    val userCalls = parsed.filter(call => name(call) == "fd")
    assertEquals(userCalls.size, 3)
    assert(!parsed.exists(call => name(call) == "forward"))
    assert(userCalls.forall(_.funcDef.functionTypeInfo.isMethodInClass.isEmpty))
  }

  test("source classes and callable parameters shadow qualified and cached turtle names") {
    val sourceClass = calls("Turtle.forward(1)\nclass Turtle:\n    @staticmethod\n    def forward(n):\n        pass")
    assert(sourceClass.exists(call => name(call) == "forward"))
    assert(sourceClass.forall(call => !TurtleCommand.isTurtleMethod(call.funcDef)))
    val parsed = calls("fd(1)\ndef apply(fd):\n    fd(2)\nfd(3)")
    assertEquals(parsed.map(name).sorted, List("fd", "forward", "forward"))
  }

  test("explicit supplied definitions retain their multilingual names") {
    val translated = BeEntityName.fromMapInCodeNotation(Map(English -> "advance", German -> "vorwärts"))
    val definition = BeDefineFunction(Nil, None, BeExpression.pass, BeDefineFunction.functionInfo(translated))
    val context = new SymbolTable.ParseContext(SymbolTable.CurrentlyKnownStructures.fromKnown(
      Seq(SymbolTable.KnownStructure.Function("fd", definition))))
    val resolved = context.resolveFunction("fd", 0)
    assert(resolved eq definition)
    assertEquals(resolved.functionTypeInfo.displayName.getNameIn(German, NamingStyle.SnakeCase), "vorwärts")
  }

  test("system printing aliases share a canonical name while user printing definitions remain user-owned") {
    val canonical = KnownBeEntityNames.resolveSystemMethods("print").get
    List("print", "println", "System.out.println").foreach { alias =>
      assert(KnownBeEntityNames.resolveCanonicalNames(alias).get eq canonical)
    }
    val parsed = calls("print(1)\nprintln(2)\nSystem.out.println(3)")
    assert(parsed.forall(_.funcDef.intrinsic.contains(it.evadid.vm.code.defining.BeIntrinsic.Print)))
    assert(parsed.forall(_.target.isInstanceOf[it.evadid.vm.code.usage.BeCallTarget.StaticMethod]))
    assert(parsed.forall(_.funcDef.functionTypeInfo.isMethodInClass.exists(_.name.universalInterpretation() == "SystemFunctionality")))
    val userCalls = calls("def println(n):\n    println(n)\nprintln(1)")
    assertEquals(userCalls.map(name), List("println", "println"))
  }

  test("binding variable argument counts never mutates the shared class signature") {
    val signature = TurtleCommand.resolveTurtleMethod("circle").get
    val parsed = calls("circle(10, 180)\ncircle(20)\nrunning_stitch()")
    assertEquals(signature.inputs.size, 1)
    assertEquals(parsed.find(call => name(call) == "running_stitch").get.parameterValueMap.size, 0)
    assertEquals(TurtleCommand.resolveTurtleMethod("running_stitch").get.inputs.size, 1)
  }
  test("a user class named turtle cannot acquire native library semantics") {
    val parsed = calls("class turtle:\n    @staticmethod\n    def forward(n: float):\n        pass\nturtle.forward(5)\n")
    assertEquals(parsed.size, 1)
    assert(!TurtleCommand.isTurtleMethod(parsed.head.funcDef))
  }

  test("printing aliases and user print functions keep separate semantics through Java") {
    val builtin = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(Python, "print(3)\n")
    assert(builtin.toJava.code.contains("System.out.println(3);"), builtin.toJava.code)
    assertEquals(builtin.toJava.toPython.code.trim, builtin.toBeExpression.toPython.code.trim)
    val user = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(Python,
      "def print(value: float):\n    forward(value)\nprint(3)\n")
    assert(!user.toJava.code.contains("System.out.println"), user.toJava.code)
    assertEquals(user.toJava.toPython.code.trim, user.toBeExpression.toPython.code.trim)
  }

  test("Java rejects a user class that would hide native turtle operations") {
    val source = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(Python,
      "class Turtle:\n    pass\nforward(3)\n")
    val result = source.convertToLanguage(it.evadid.core.datastructures.language.AppLanguage.Java)
    assert(result.finalState.isEmpty)
    assert(result.conversionInfo.exists(_.warningMsg.contains("conflicts")))
  }

}
