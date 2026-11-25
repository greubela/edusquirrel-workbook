package it.evadid.vm.conversion.pythonexpression

import it.evadid.vm.conversion.parsed.{ParsedFunctionKind, ParsedParameter, ParsedStatement, ParsedType}
import it.evadid.vm.conversion.parsed.ParsedExpression as E
import it.evadid.vm.conversion.parsed.ParsedStatement as S
import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import munit.FunSuite

class PythonToParsedSpec extends FunSuite {
  private def parse(source: String): Seq[ParsedStatement] =
    SourceParsing.parse(source, SourceLanguage.Python).neutral.statements.map {
      case function: S.FunctionDef => function.copy(origin = None)
      case unsupported: S.Unsupported => unsupported.copy(origin = None)
      case other => other
    }

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

  private def drawn(code: String): List[Double] =
    it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(it.evadid.core.datastructures.language.AppLanguage.Python, code)
      .toBeExpression.turtleCommandExecutionInfo.completedCommands.map(_.args.head)

  test("range loops repeat, count with steps like Python or are reported") {
    assertEquals(parse("for _ in range(3):\n    pass\n"), Seq(S.Repeat(E.Literal("3"), Seq(S.Pass))))
    assertEquals(drawn("for i in range(4):\n    forward(i)\n"), List(0.0, 1.0, 2.0, 3.0))
    assertEquals(drawn("n = 3\nfor i in range(1, n + 1):\n    forward(i)\n"), List(1.0, 2.0, 3.0))
    assertEquals(drawn("for i in range(0, 7, 3):\n    forward(i)\n"), List(0.0, 3.0, 6.0))
    assertEquals(drawn("for i in range(5, 0, -2):\n    forward(i)\n"), List(5.0, 3.0, 1.0))
    assertEquals(drawn("for i in range(5, 1):\n    forward(i)\n"), Nil)
    assertEquals(parse("for item in items:\n    pass\n"), Seq(S.Unsupported("for ...", "For statements must use range(...)", unparsable = true)))
    parse("for i in range(0, 4, 0):\n    pass\n") match {
      case Seq(S.Unsupported(_, reason, false, _)) => assert(reason.contains("nonzero whole number"), reason)
      case other => fail(s"Unexpected $other")
    }
    parse("for i in range(3):\n    i = 1\n") match {
      case Seq(S.Unsupported(_, reason, false, _)) => assert(reason.contains("Assigning the loop counter"), reason)
      case other => fail(s"Unexpected $other")
    }
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
      case Seq(S.Unsupported(_, reason, false, _)) => assertEquals(reason, "Decorators are not supported outside classes")
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
    assert(evaluated.exists { case E.Unsupported(_, _) => true; case _ => false }, evaluated)
  }

  test("subscript assignments and try statements are unsupported") {
    parse("items[0] = 1\n") match {
      case Seq(S.Unsupported(_, reason, false, _)) => assertEquals(reason, "Subscript assignment is not supported")
      case other => fail(s"Unexpected $other")
    }
    parse("try:\n    x = 1\nexcept ValueError:\n    pass\n") match {
      case Seq(S.Unsupported(_, reason, _, _)) => assertEquals(reason, "try/except statements are currently unsupported")
      case other => fail(s"Unexpected $other")
    }
  }

  test("unreadable source keeps the statement and explains common mistakes") {
    val reasons = Seq("if x\n    pass\n", "while x\n    pass\n").map(source => parse(source).collectFirst {
      case S.Unsupported(_, reason, _, _) => reason
    })
    assertEquals(reasons.head, Some("If statements must end with ':'"))
    assertEquals(reasons(1), Some("While statements must end with ':'"))
    assertEquals(parse("global count, size\n"), Seq(S.Global(Seq("count", "size"))))
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
      case Seq(S.Unsupported(_, "Inheritance", false, _)) => ()
      case other => fail(s"Unexpected $other")
    }
    parse("class Box:\n    print(1)\n") match {
      case Seq(S.Unsupported(_, "Class-body statements or decorators", false, _)) => ()
      case other => fail(s"Unexpected $other")
    }
    intercept[IllegalArgumentException](parse("class Box:\n    def grow(size):\n        pass\n"))
  }

  test("subscripts, chained calls and walrus expressions stay explicit unsupported expressions") {
    val evaluated = parse("forward(items[0])\nmake().draw(1)\nprint((n := 3))\nwalker.legs[0].move()\n").collect { case S.Evaluate(e) => e }
    evaluated.head match {
      case E.Call(Seq("forward"), Seq(unsupported: E.Unsupported)) => assertEquals(unsupported.source, "[...]")
      case other => fail(s"Unexpected $other")
    }
    assert(evaluated.exists {
      case E.MethodCall(E.Call(Seq("make"), Nil), "draw", Seq(E.Literal("1"))) => true
      case E.MethodCall(_, "draw", _) => true
      case _ => false
    }, evaluated)
    assert(evaluated.exists(_.toString.contains("n := ...")), evaluated)
  }

  test("type hints accept common spellings from other languages") {
    val hints = parse("a: boolean\nb: string\nc: any\nd: double\ne: unit\nf: datetime\n").collect { case S.Declare(_, hint) => hint }
    assertEquals(hints, Seq(ParsedType.Boolean, ParsedType.Text, ParsedType.AnyValue, ParsedType.Numeric, ParsedType.Unit, ParsedType.Date))
  }

  test("instance fields assigned inside loops of a constructor are collected") {
    val Seq(S.ClassDef(_, fields, _, _)) = parse(
      "class Walker:\n    def __init__(self):\n        while False:\n            self.a = 1\n        for i in range(2):\n            self.b = i\n        self.pos.x = 3\n").take(1): @unchecked
    assertEquals(fields.map(_.name), Seq("a", "b"))
    assertEquals(parse("self.pos.x = 3\n"), Seq(S.AssignMember(E.Member(E.Self, "pos"), "x", E.Literal("3"))))
  }
}
