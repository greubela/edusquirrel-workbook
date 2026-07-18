package it.evadid.vm.parsing.python.clean

import munit.FunSuite

class Python313ParserTest extends FunSuite {

  test("parse simple assignment") {
    val code = "x = 1"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
    val program = result.right.get
    assert(program.statements.size == 1)
  }

  test("parse simple expression") {
    val code = "1 + 2"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse function call") {
    val code = "print('hello')"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse function call with multiple arguments") {
    val code = "print('hello', 'world')"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse function call with expression argument") {
    val code = "print(1 + 2)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse named expression (walrus operator)") {
    val code = "(x := 1)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse named expression in condition") {
    val code = "if (n := len(items)) > 0: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse function definition") {
    val code = "def foo(): pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse function definition with parameters") {
    val code = "def foo(a, b): pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse function definition with type hints") {
    val code = "def foo(a: int, b: str) -> int: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse class definition") {
    val code = "class Foo: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse if statement") {
    val code = "if x > 0: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse if-else statement") {
    val code = "if x > 0: pass\nelse: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse elif statement") {
    val code = "if x > 0: pass\nelif x < 0: pass\nelse: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse while loop") {
    val code = "while x > 0: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse for loop") {
    val code = "for i in range(10): pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse try-except") {
    val code = "try: pass\nexcept: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse try-except with specific exception") {
    val code = "try: pass\nexcept ValueError: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse try-except with as clause") {
    val code = "try: pass\nexcept ValueError as e: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse try-except-finally") {
    val code = "try: pass\nexcept: pass\nfinally: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse import statement") {
    val code = "import math"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse from import statement") {
    val code = "from math import pi"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse from import * statement") {
    val code = "from math import *"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse multiple imports") {
    val code = "from math import pi, e, tau"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse return statement") {
    val code = "def foo(): return 1"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse return with expression") {
    val code = "def foo(): return 1 + 2"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse raise statement") {
    val code = "raise ValueError"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse raise with from") {
    val code = "raise ValueError from None"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse complex expression with operators") {
    val code = "1 + 2 * 3 - 4 / 5"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse comparison operators") {
    val code = "x == y != z < a > b <= c >= d"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse boolean operators") {
    val code = "x and y or z"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse unary operators") {
    val code = "-x + ~y + not z"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse power operator") {
    val code = "x ** y ** z"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse bitwise operators") {
    val code = "x | y ^ z & a << b >> c"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse nested function calls") {
    val code = "foo(bar(baz(1)))"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse function call with named expression") {
    val code = "foo(x := 1)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in comprehension") {
    val code = "[y := x + 1 for x in range(10)]"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in if statement") {
    val code = "if (n := len(items)) > 0:\n    print(n)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in while loop") {
    val code = "while (line := input()) != 'quit':\n    print(line)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse complex walrus operator expression") {
    val code = "if (x := 1) + (y := 2) > 2:\n    pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse assignment with type hint") {
    val code = "x: int = 1"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse assignment with complex type hint") {
    val code = "x: int | str = 1"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse subscript assignment") {
    val code = "x[0] = 1"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse attribute assignment") {
    val code = "a.x = 1"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse multiple assignments") {
    val code = "x = y = 1"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse augmented assignment") {
    val code = "x += 1"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse pass statement") {
    val code = "def foo(): pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse empty line") {
    val code = "\n"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse multiple statements on one line") {
    val code = "x = 1; y = 2"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse statement with trailing semicolon") {
    val code = "x = 1;"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse complex expression with parentheses") {
    val code = "(1 + 2) * (3 + 4)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse list literal") {
    val code = "[1, 2, 3]"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse dict literal") {
    val code = "{1: 2, 3: 4}"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse tuple literal") {
    val code = "(1, 2, 3)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse set literal") {
    val code = "{1, 2, 3}"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse string literal") {
    val code = "'hello'"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse f-string") {
    val code = "f'hello {name}'"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse multiline code") {
    val code = """x = 1
y = 2
z = x + y"""
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse code with comments") {
    val code = """# This is a comment
x = 1  # inline comment
y = 2"""
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse async function definition") {
    val code = "async def foo(): pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse async for loop") {
    val code = "async for i in items: pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse async with statement") {
    val code = "async with foo(): pass"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse nested function calls with walrus operator") {
    val code = "foo(bar(x := 1), baz(y := 2))"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in function call argument") {
    val code = "print(x := 1 + 2)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in binary expression") {
    val code = "(x := 1) + (y := 2)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in comparison") {
    val code = "(x := 1) > 0"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in boolean expression") {
    val code = "(x := 1) and (y := 2)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator with unary operator") {
    val code = "not (x := 1)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in power expression") {
    val code = "(x := 2) ** 3"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in bitwise expression") {
    val code = "(x := 1) | (y := 2)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in shift expression") {
    val code = "(x := 1) << 2"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in term expression") {
    val code = "(x := 2) * 3"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in sum expression") {
    val code = "(x := 1) + 2"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in factor expression") {
    val code = "-(x := 1)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in disjunction") {
    val code = "(x := 1) or (y := 2)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in conjunction") {
    val code = "(x := 1) and (y := 2)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in inversion") {
    val code = "not (x := 1)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in comparison chain") {
    val code = "(x := 1) < y < (z := 3)"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator with subscript") {
    val code = "(x := [1, 2, 3])[0]"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator with attribute access") {
    val code = "(x := obj).attr"
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in nested if") {
    val code = """if (x := 1):
    if (y := 2):
        pass"""
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in nested while") {
    val code = """while (x := 1):
    while (y := 2):
        pass"""
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in nested for") {
    val code = """for i in range(10):
    if (j := i * 2) > 10:
        pass"""
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in try-except") {
    val code = """try:
    if (x := 1):
        pass
except:
    pass"""
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in function definition") {
    val code = """def foo():
    if (x := 1):
        return x"""
    val result = Python313Parser.parse(code)
    
    assert(result.isRight)
  }

  test("parse walrus operator in class definition") {