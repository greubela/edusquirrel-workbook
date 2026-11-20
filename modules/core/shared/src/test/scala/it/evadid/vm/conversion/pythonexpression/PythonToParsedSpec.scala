package it.evadid.vm.conversion.pythonexpression

import it.evadid.vm.conversion.parsed.{ParsedFunctionKind, ParsedParameter, ParsedStatement, ParsedType}
import it.evadid.vm.conversion.parsed.ParsedExpression as E
import it.evadid.vm.conversion.parsed.ParsedStatement as S
import munit.FunSuite

class PythonToParsedSpec extends FunSuite {
  private def parse(source: String): Seq[ParsedStatement] = PythonToExpression.parse(source).statements

  test("simple statements, comments and declarations map to neutral statements") {
    assertEquals(parse("pass\n# note\nx: float\ny: bool = True\nz = None\nx += 2\nself_less.value = 3\n"), Seq(
      S.Pass,
      S.Comment("note"),
      S.Declare("x", ParsedType.Numeric),
      S.Assign("y", Some(ParsedType.Boolean), E.Literal("True")),
      S.Assign("z", None, E.NoneValue),
      S.Assign("x", None, E.Binary("+", E.Name("x"), E.Literal("2"))),
      S.AssignMember(E.Name("self_less"), "value", E.Literal("3"))))
  }

  test("bridge imports are dropped while other imports remain visible as unsupported") {
    assertEquals(parse("import turtle\nfrom typing import Any\nfrom datetime import date\n"), Seq.empty)
    assertEquals(parse("import math\n"), Seq(S.Unsupported("import math", "Imports are not supported", unparsable = false)))
  }

  test("range loops repeat, count with inclusive ends or are reported") {
    assertEquals(parse("for _ in range(3):\n    pass\n"), Seq(S.Repeat(3, Seq(S.Pass))))
    assertEquals(parse("for i in range(4):\n    pass\n"), Seq(S.For("i", E.Literal("0"), E.Literal("3"), Seq(S.Pass))))
    assertEquals(parse("for i in range(1, n + 1):\n    pass\n"), Seq(S.For("i", E.Literal("1"), E.Name("n"), Seq(S.Pass))))
    assertEquals(parse("for i in range(a, b, 2):\n    pass\n"),
      Seq(S.For("i", E.Name("a"), E.Binary("-", E.Name("b"), E.Literal("1")), Seq(S.Pass))))
    assertEquals(parse("for item in items:\n    pass\n"), Seq(S.Unsupported("for ...", "For statements must use range(...)", unparsable = true)))
  }

  test("control flow and returns keep their bodies") {
    assertEquals(parse("while x < 3:\n    x = x + 1\nif x:\n    return\nelse:\n    return x\n"), Seq(
      S.While(E.Binary("<", E.Name("x"), E.Literal("3")), Seq(S.Assign("x", None, E.Binary("+", E.Name("x"), E.Literal("1"))))),
      S.If(E.Name("x"), Seq(S.Return(None)), Seq(S.Return(Some(E.Name("x")))))))
  }

  test("functions keep typed parameters while decorated or async functions are unsupported") {
    assertEquals(parse("def area(w: float, h: int) -> float:\n    return w * h\n"), Seq(S.FunctionDef("area",
      Seq(ParsedParameter("w", Some(ParsedType.Numeric)), ParsedParameter("h", Some(ParsedType.Numeric))), Some(ParsedType.Numeric),
      Seq(S.Return(Some(E.Binary("*", E.Name("w"), E.Name("h"))))), ParsedFunctionKind.Function)))
    parse("@cache\ndef area():\n    return 1\n") match {
      case Seq(S.Unsupported(_, reason, false)) => assertEquals(reason, "Decorators are not supported outside classes")
      case other => fail(s"Unexpected $other")
    }
  }

  test("type hints map named, union and unknown types") {
    val hints = parse("a: str\nb: None\nc: int | str\nd: date\ne: Turtle\nf: Any\ng: Number\nh: void\n").collect {
      case S.Declare(_, hint) => hint
    }
    assertEquals(hints, Seq(ParsedType.Text, ParsedType.Unit, ParsedType.Union(Seq(ParsedType.Numeric, ParsedType.Text)),
      ParsedType.Date, ParsedType.Named("Turtle"), ParsedType.AnyValue, ParsedType.Numeric, ParsedType.Unit))
  }

  test("expressions map calls, method calls, members and unsupported forms") {
    val evaluated = parse("turtle.forward(10)\nwalker.legs.move(1)\nmake()(2)\nnot ready\npoint.x\nitems[0]\n[1, 2]\nlambda v: v\n").collect {
      case S.Evaluate(expression) => expression
    }
    assertEquals(evaluated.take(2), Seq(E.Call(Seq("turtle", "forward"), Seq(E.Literal("10"))),
      E.Call(Seq("walker", "legs", "move"), Seq(E.Literal("1")))))
    assert(evaluated.contains(E.Unary("not", E.Name("ready"))), evaluated)
    assert(evaluated.contains(E.Member(E.Name("point"), "x")) || evaluated.contains(E.Name("point.x")) ||
      evaluated.exists { case E.Member(_, "x") => true; case _ => false }, evaluated)
    assert(evaluated.exists { case E.Unsupported(_) => true; case _ => false }, evaluated)
  }

  test("subscript assignments and try statements are unsupported") {
    parse("items[0] = 1\n") match {
      case Seq(S.Unsupported(_, reason, false)) => assertEquals(reason, "Subscript assignment is not supported")
      case other => fail(s"Unexpected $other")
    }
    parse("try:\n    x = 1\nexcept ValueError:\n    pass\n") match {
      case Seq(S.Unsupported(_, reason, true)) => assertEquals(reason, "try/except statements are currently unsupported")
      case other => fail(s"Unexpected $other")
    }
  }

  test("unreadable source keeps the statement and explains common mistakes") {
    val reasons = Seq("if x\n    pass\n", "while x\n    pass\n", "global count\n").map(source => parse(source).collectFirst {
      case S.Unsupported(_, reason, _) => reason
    })
    assertEquals(reasons.head, Some("If statements must end with ':'"))
    assertEquals(reasons(1), Some("While statements must end with ':'"))
    assertEquals(reasons(2), Some("global declaration"))
    assertEquals(parse("   "), Seq.empty)
  }

  test("classes become fields and methods, and unsupported class shapes are reported") {
    val Seq(S.ClassDef("Box", fields, methods, _)) = parse(
      """class Box:
        |    count = 0
        |    def __init__(self, size: float):
        |        self.size = size
        |        if size > 1:
        |            self.big = True
        |    def grow(self):
        |        self.size = self.size + 1
        |    @staticmethod
        |    def unit():
        |        return 1
        |""".stripMargin): @unchecked
    assertEquals(fields.map(_.name), Seq("count", "size", "big", "size"))
    assertEquals(fields.head.initial, Some(E.Literal("0")))
    assertEquals(fields(1).assigned, Some(E.Name("size") -> Seq(ParsedParameter("size", Some(ParsedType.Numeric)))))
    assertEquals(methods.map(m => m.name -> m.kind), Seq("__init__" -> ParsedFunctionKind.Constructor,
      "grow" -> ParsedFunctionKind.Method, "unit" -> ParsedFunctionKind.StaticMethod))
    assertEquals(methods(1).parameters, Seq.empty)
    parse("class Child(Base):\n    pass\n") match {
      case Seq(S.Unsupported(_, "Inheritance", false)) => ()
      case other => fail(s"Unexpected $other")
    }
    parse("class Box:\n    print(1)\n") match {
      case Seq(S.Unsupported(_, "Class-body statements or decorators", false)) => ()
      case other => fail(s"Unexpected $other")
    }
    intercept[IllegalArgumentException](parse("class Box:\n    def grow(size):\n        pass\n"))
  }
}
