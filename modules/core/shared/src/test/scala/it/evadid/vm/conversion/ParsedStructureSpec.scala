package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable, BeIntrinsic}
import it.evadid.vm.code.errors.BeSingleLineComment
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.*
import it.evadid.vm.conversion.printing.ModuleLayout
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeDataValueUnit, BeUseValueReference}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import munit.FunSuite

/** The expression tree a parser builds, written as a language-neutral outline.
  *
  * Equivalent Python, Java and Snap sources must build the same outline: the same node kinds,
  * universal names, types, bound call targets and operator nesting.
  */
class ParsedStructureSpec extends FunSuite {
  private def name(entity: BeEntityName): String = entity.universalInterpretation()
  private def typeName(dataType: BeDataType): String = dataType match {
    case BeDataType.Numeric => "num"
    case BeDataType.String => "str"
    case BeDataType.Boolean => "bool"
    case BeDataType.AnyType => "any"
    case BeDataType.Unit => "unit"
    case BeDataType.ClassType(className) => name(className)
    case other => other.toString
  }
  private def variable(definition: BeDefineVariable): String = s"${name(definition.name)}: ${typeName(definition.variableType)}"
  private def arguments(call: BeFunctionCall): String =
    call.arguments.map(outline).mkString("(", ", ", ")")
  private def block(sequence: BeSequence): String = sequence.body.filterNot(_.isInstanceOf[BeSingleLineComment]).map(outline).mkString("[", "; ", "]")

  def outline(expression: BeExpression): String = expression match {
    case BeStartProgram(body) => body.map(outline).getOrElse("[]")
    case sequence: BeSequence => block(sequence)
    case BeAssignVariable(target, value) => s"${variable(target)} = ${outline(value)}"
    case definition: BeDefineVariable => s"var ${variable(definition)}"
    case BeUseValue(BeDataValueLiteral(raw), _) => raw
    case BeUseValue(BeUseValueReference(target), _) => name(target.name)
    case BeUseValue(BeDataValueUnit(), _) => "None"
    case call: BeFunctionCall => call.funcDef.functionTypeInfo.funcType match {
      case BeDefineFunction.Operator(_) =>
        val operands = call.arguments.map(outline)
        val symbol = name(call.funcDef.functionTypeInfo.displayName)
        if operands.size == 1 then s"($symbol ${operands.head})" else operands.mkString("(", s" $symbol ", ")")
      case _ =>
        val callee = name(call.funcDef.functionTypeInfo.displayName)
        call.target match {
          case BeCallTarget.InstanceMethod(receiver, _) => s"${outline(receiver)}.$callee${arguments(call)}"
          case BeCallTarget.Constructor(definition) => s"new ${name(definition.functionTypeInfo.isMethodInClass.get.name)}${arguments(call)}"
          case BeCallTarget.StaticMethod(definition) => s"${name(definition.functionTypeInfo.isMethodInClass.get.name)}.$callee${arguments(call)}"
          case BeCallTarget.Function(definition) =>
            val library = definition.intrinsic.map(intrinsic => s"<$intrinsic>").getOrElse("")
            s"$library$callee${arguments(call)}"
        }
    }
    case definition: BeDefineFunction =>
      val kind = definition.functionTypeInfo.funcType match {
        case BeDefineFunction.Constructor => "init"
        case BeDefineFunction.Method => "method"
        case BeDefineFunction.StaticMethod => "static"
        case _ => "def"
      }
      val result = definition.outputs.map(output => s" -> ${typeName(output.variableType)}").getOrElse("")
      val title = if kind == "init" then kind else s"$kind ${name(definition.functionTypeInfo.displayName)}"
      s"$title${definition.inputs.map(variable).mkString("(", ", ", ")")}$result ${block(definition.body)}"
    case definition: BeDefineClass =>
      s"class ${name(definition.name)} {${(definition.attributes.map(variable) ++ definition.methods.map(outline)).mkString("; ")}}"
    case BeReturn(value) => s"return ${value.map(outline).getOrElse("")}".trim
    case BeIfElse(condition, thenBody, elseBody) =>
      s"if ${block(condition)} ${block(thenBody)}" + (if elseBody.body.isEmpty then "" else s" else ${block(elseBody)}")
    case BeWhile(condition, body) => s"while ${block(condition)} ${block(body)}"
    case BeRepeatNr(times, body) => s"repeat ${outline(times)} ${block(body)}"
    case BeFor(counter, start, end, body, direction) => s"for ${name(counter.name)} = ${outline(start)}..${outline(end)} $direction ${block(body)}"
    case BeCurrentInstance(_) => "self"
    case BeMemberAccess(receiver, member) => s"${outline(receiver)}.${name(member.name)}"
    case BeAssignMember(receiver, member, value) => s"${outline(receiver)}.${name(member.name)} = ${outline(value)}"
    case other => s"<${other.getClass.getSimpleName}>"
  }

  private def outlineOf(state: ProgrammingState): String = outline(state.toBeExpression.expression)

  private def java(members: String, main: String) =
    ProgrammingStateRaw(Java, s"class Main {\n$members\n    public static void main(String[] args) {\n$main\n    }\n}\n")

  test("module variables, a typed helper, loops and branches build one tree in Python and Java") {
    val python = ProgrammingStateRaw(Python, """limit = 10
      |total = 0
      |def grow(value: float, factor: float) -> float:
      |    return value * factor + 1
      |while total < limit:
      |    total = grow(total, 2)
      |if total > 12:
      |    forward(total - limit)
      |else:
      |    turn_left(total % 4)
      |""".stripMargin)
    val javaSource = java(
      """    static double limit = 10;
        |    static double total = 0;
        |    static double grow(double value, double factor) {
        |        return value * factor + 1;
        |    }""".stripMargin,
      """        while (total < limit) {
        |            total = grow(total, 2);
        |        }
        |        if (total > 12) {
        |            Turtle.forward(total - limit);
        |        } else {
        |            Turtle.turnLeft(WorkbookMath.modulo(total, 4));
        |        }""".stripMargin)
    val expected = "[limit: num = 10; total: num = 0; def grow(value: num, factor: num) -> num [return ((value * factor) + 1)]; " +
      "while [(total < limit)] [total: num = grow(total, 2)]; " +
      "if [(total > 12)] [turtle.forward((total - limit))] else [turtle.left((total % 4))]]"
    assertEquals(outlineOf(python), expected)
    val modulo = it.evadid.vm.conversion.dependencies.KnownDependencies.modulo.definition(Java).get
    assertEquals(outlineOf(javaSource.copy(code = javaSource.code + "\n" + modulo)), expected)
  }

  test("Snap builds the same tree with untyped variables") {
    val python = "limit = 10\ntotal = 0\nwhile total < limit:\n    total = total * 2 + 1\nif total > 12:\n    forward(total - limit)\nelse:\n    turn_left(total % 4)\n"
    val snap = ProgrammingStateRaw(Python, python).toSnapXml
    assert(snap.code.contains("<block s=\"doUntil\">"), snap.code)
    assertEquals(outlineOf(snap), "[limit: any = 10; total: any = 0; while [(total < limit)] [total: any = ((total * 2) + 1)]; " +
      "if [(total > 12)] [turtle.forward((total - limit))] else [turtle.left((total % 4))]]")
  }

  test("operators nest by precedence and associativity in both languages") {
    val python = ProgrammingStateRaw(Python,
      "a = 1\nb = 2\nc = 3\nmix = a + b * c - a / (b - c)\npower = 2 ** 3 ** 2\nnegated = -a ** 2\nlogic = not a < b and b < c or c == a\n")
    val javaSource = java("    static double a = 1;\n    static double b = 2;\n    static double c = 3;",
      "        double mix = a + b * c - a / (b - c);\n        double power = Math.pow(2, Math.pow(3, 2));\n" +
        "        double negated = -Math.pow(a, 2);\n        boolean logic = !(a < b) && b < c || c == a;")
    val expected = "[a: num = 1; b: num = 2; c: num = 3; mix: num = ((a + (b * c)) - (a / (b - c))); power: num = (2 ** (3 ** 2)); " +
      "negated: num = (- (a ** 2)); logic: bool = (((not (a < b)) and (b < c)) or (c == a))]"
    assertEquals(outlineOf(python), expected)
    assertEquals(outlineOf(javaSource), expected.replace("a / (b - c)", "a quotient (b - c)"))
  }

  test("classes keep their attributes, constructor, methods and bound calls in both languages") {
    val python = ProgrammingStateRaw(Python, """class Walker:
      |    def __init__(self, step: float):
      |        self.step = step
      |    def walk(self, times: float) -> None:
      |        forward(self.step * times)
      |    @staticmethod
      |    def unit() -> float:
      |        return 5
      |w = Walker(15)
      |w.walk(Walker.unit())
      |""".stripMargin)
    val javaSource = ProgrammingStateRaw(Java, """class Walker {
      |    double step;
      |    Walker(double step) {
      |        this.step = step;
      |    }
      |    void walk(double times) {
      |        Turtle.forward(step * times);
      |    }
      |    static double unit() {
      |        return 5;
      |    }
      |}
      |class Main {
      |    public static void main(String[] args) {
      |        Walker w = new Walker(15);
      |        w.walk(Walker.unit());
      |    }
      |}
      |""".stripMargin)
    val expected = "[class walker {step: num; init(step: num) [self.step = step]; method walk(times: num) [turtle.forward((self.step * times))]; " +
      "static unit() -> num [return 5]}; w: walker = new walker(15); w.walk(walker.unit())]"
    assertEquals(outlineOf(python), expected)
    assertEquals(outlineOf(javaSource), expected, "the implicit this of `step` is the instance member")
  }

  test("camelCase and snake_case spell the same name, and calls bind to that definition") {
    assertEquals(BeEntityName.fromCodeString("doSomething").universalInterpretation(), BeEntityName.fromCodeString("do_something").universalInterpretation())
    val python = ProgrammingStateRaw(Python, "def do_something(step_size: float) -> None:\n    forward(step_size)\ndo_something(5)\n")
    val javaSource = java("    static void doSomething(double stepSize) {\n        Turtle.forward(stepSize);\n    }", "        doSomething(5);")
    Seq(python, javaSource).foreach { state =>
      assertEquals(outlineOf(state), "[def do_something(step_size: num) [turtle.forward(step_size)]; do_something(5)]")
      val nodes = state.toBeExpression.expression.subtree
      val definition = nodes.collectFirst { case function: BeDefineFunction if name(function.functionTypeInfo.displayName) == "do_something" => function }.get
      val call = nodes.collectFirst { case call: BeFunctionCall if call.target.isInstanceOf[BeCallTarget.Function] => call }.get
      assertEquals(call.funcDef, definition, "the call is bound to the user definition, not to an unknown name")
    }
  }

  test("library calls carry their intrinsic in every spelling") {
    val expected = "[page: str = <FetchText>fetch_text(\"https://example.org\"); SystemFunctionality.printToOut(page)]"
    val pythonOnly = ProgrammingStateRaw(Python, "page = fetch_text(\"https://example.org\")\nprint(page)\n")
    val javaSource = java("", "        String page = Web.fetchText(\"https://example.org\");\n        System.out.println(page);")
    assertEquals(outlineOf(pythonOnly), expected)
    assertEquals(outlineOf(javaSource), expected)
    val conversions = ProgrammingStateRaw(Python, "x = 2.5\ny = int(x)\nz = float(y)\nt = str(z)\nm = max(abs(x), 1)\n")
    val javaConversions = java("", "        double x = 2.5;\n        double y = (int) x;\n        double z = (double) y;\n        String t = String.valueOf(z);\n        double m = Math.max(Math.abs(x), 1);")
    val conversionOutline = "[x: num = 2.5; y: num = <ToInteger>int(x); z: num = <ToNumber>float(y); t: str = <ToText>str(z); m: num = <Maximum>max(<Absolute>abs(x), 1)]"
    assertEquals(outlineOf(conversions), conversionOutline)
    assertEquals(outlineOf(javaConversions), conversionOutline)
  }
}
