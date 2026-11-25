package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import munit.FunSuite

/** Step 1 for Java: declarations of any text, with spans, lines and masked library definitions. */
class JavaStructuredSpec extends FunSuite {
  private def structured(source: String): JavaSourceStructured = SourceParsing.parse(source, SourceLanguage.Java).structured
  private def only(source: String): JavaClassStructured = structured(source).classes match {
    case Vector(owner) => owner
    case other => fail(s"Expected one class, found ${other.map(_.name)}")
  }

  test("classes carry their modifiers, header, body, spans and lines; nested classes name their outer class") {
    val source = "public final class Outer {\n  static class Inner { }\n}\nclass Open { void f() {"
    val Vector(outer, inner, open) = structured(source).classes: @unchecked
    assertEquals((outer.name, outer.modifiers, outer.outer), ("Outer", Vector("public", "final"), None))
    assertEquals(outer.header, "public final class Outer ")
    assertEquals((outer.span.startLine, outer.span.endLine), (1, 3))
    assertEquals(outer.code, "public final class Outer {\n  static class Inner { }\n}")
    assertEquals(outer.bodySpan.map(_.text(source)), Some("\n  static class Inner { }\n"))
    assertEquals((inner.name, inner.modifiers, inner.outer, inner.span.startLine), ("Inner", Vector("static"), Some("Outer"), 2))
    assertEquals(inner.bodySpan.map(_.text(source)), Some(" "))
    assert(outer.closed && inner.closed)
    assertEquals((open.name, open.closed, open.span.end), ("Open", false, source.length))
    assertEquals(structured("class Forward;").classes.map(owner => (owner.name, owner.bodySpan, owner.members)), Vector(("Forward", None, Vector.empty)))
  }

  test("functions carry modifiers, annotations, return type, typed parameters, body and their exact code") {
    val source = "class A {\n  @Override\n  public static <T> java.util.List<T> names(final int a, Map<String, Integer> plan, String... rest) {\n" +
      "    return null;\n  }\n  A(int size) { }\n  int[] values() { return xs; }\n  abstract void later();\n  void open() {\n"
    val owner = only(source)
    val Vector(names, constructor, values, later, open) = owner.functions: @unchecked
    assertEquals((names.name, names.modifiers, names.annotations, names.returnType), ("names", Vector("public", "static"), Vector("@Override"), Some("<T> java.util.List<T>")))
    assertEquals(names.parameters, Vector(JavaParameterStructured("a", "int", Vector("final")),
      JavaParameterStructured("plan", "Map<String, Integer>", Vector.empty), JavaParameterStructured("rest", "String...", Vector.empty)))
    assertEquals(names.parameterList, "final int a, Map<String, Integer> plan, String... rest")
    assertEquals(names.body, Some("return null;"))
    assert(names.code.startsWith("@Override\n  public static <T>") && names.code.endsWith("}"), names.code)
    assertEquals((names.span.startLine, names.span.endLine), (2, 5))
    assertEquals(names.bodySpan.map(_.text(source).trim), Some("return null;"))
    assert(names.isStatic && names.closed)
    assertEquals((constructor.name, constructor.returnType, constructor.isConstructor), ("A", None, true))
    assertEquals(constructor.parameters.map(_.javaType), Vector("int"))
    assertEquals(values.returnType, Some("int[]"))
    assertEquals((later.body, later.closed, later.isAbstract), (None, false, true))
    assertEquals((open.name, open.closed, open.body), ("open", false, None))
    assertEquals(open.span.end, source.length)
  }

  test("fields carry type, name and initializer; other members stay as their code") {
    val owner = structured("class A { private static final java.util.List<String> names = new ArrayList<>(); int[] xs = {1, 2}; // c\n" +
      " a.b c; static { init(); } x = 1; return value; class Inner { } int z }").classes.head
    assertEquals(owner.fields.map(field => (field.modifiers, field.javaType, field.name, field.initializer)), Vector(
      (Vector("private", "static", "final"), "java.util.List<String>", "names", Some("new ArrayList<>()")),
      (Vector.empty, "int[]", "xs", Some("{1, 2}")),
      (Vector.empty, "a.b", "c", None),
      (Vector.empty, "int", "z", None)))
    assertEquals(owner.members.collect { case other: JavaOtherMemberStructured => other.code },
      Vector("static { init(); }", "x = 1;", "return value;", "class Inner { }"))
    assert(owner.fields.head.isStatic)
  }

  test("calls and statements of loose code are not functions") {
    val owner = only("class Main { forward(10); System.out.println(1); int y = Helper.size(3); if (x) { go(); } }")
    assertEquals(owner.functions, Vector.empty)
    assertEquals(owner.fields.map(_.name), Vector("y"))
    assertEquals(owner.members.size, 4)
  }

  test("package and import declarations outside classes are the preamble") {
    val source = "package demo;\nimport java.util.List;\nclass A { }\nint x = 1;"
    assertEquals(structured(source).preamble.map(declaration => (declaration.keyword, declaration.text, declaration.span.startLine)),
      Vector(("package", "package demo;", 1), ("import", "import java.util.List;", 2)))
  }

  test("verbatim library definitions are library classes and blanked in the masked source; edited copies are not") {
    val source = ProgrammingStateRaw(Python, "forward(10)").toJava.code
    val result = structured(source)
    val turtle = result.classes.find(_.name == "Turtle").get
    assert(turtle.library)
    assertEquals(result.programClasses.map(_.name), Vector("Main"))
    assertEquals(result.masked.length, source.length)
    assertEquals(result.masked.count(_ == '\n'), source.count(_ == '\n'))
    assert(!result.masked.contains("class Turtle"))
    assert(result.masked.contains("class Main"))
    val edited = structured(source.replace("move(distance);", "move(2 * distance);"))
    assert(!edited.classes.find(_.name == "Turtle").get.library)
    assert(edited.masked.contains("class Turtle"))
  }

  test("codeMasked also blanks comments and string literals, keeping offsets and line breaks") {
    val source = "int a = 1; // note \"x\"\nString b = \"int c = 2;\";"
    val masked = structured(source).codeMasked
    assertEquals(masked.length, source.length)
    assertEquals(masked.linesIterator.toVector.map(_.replaceAll("\\s+", " ").trim), Vector("int a = 1;", "String b = ;"))
  }

  test("balance and trivia: brackets, strings and comments must close; trivia has no code") {
    assert(structured("class A { void f() { g(\"}\"); /* ) */ } }").balanced)
    assert(!structured("class A { void f() {").balanced)
    assert(!structured("f(\"open)").balanced)
    assert(!structured("/* open").balanced)
    assert(!structured("f())").balanced)
    assert(structured("  // only a comment\n/* and another */ ").trivia)
    assert(!structured("x;").trivia)
    assert(!structured("/* open").trivia)
  }

  test("any text is accepted: unfinished and invalid code yields what can be recognised") {
    List("", "}}}", "class", "class {", "class A { void f(", "class A { int x = ", "@@@ ### \"unterminated", "class A { void f() { } } }")
      .foreach { source =>
        val result = structured(source)
        assertEquals(result.source, source)
        assertEquals(result.masked.length, source.length)
      }
    assertEquals(structured("class A { void f(").classes.flatMap(_.members).collect { case function: JavaFunctionStructured => function.closed },
      Vector(false))
  }

  test("loose members are the declarations and statements outside classes and the preamble") {
    val source = "import a.B;\nint count = 2;\nvoid draw(int size) { forward(size); }\nclass Kept { void inside() {} }\ndraw(count);\n// note\n"
    val loose = structured(source).looseMembers
    assertEquals(loose.map(_.code), Vector("int count = 2;", "void draw(int size) { forward(size); }", "draw(count);"))
    loose(0) match {
      case field: JavaFieldStructured => assertEquals((field.className, field.javaType, field.name, field.initializer), ("", "int", "count", Some("2")))
      case other => fail(s"expected a field, got $other")
    }
    loose(1) match {
      case function: JavaFunctionStructured => assertEquals((function.name, function.parameterList, function.span.startLine), ("draw", "int size", 3))
      case other => fail(s"expected a function, got $other")
    }
    assertEquals(structured("forward(10").looseMembers.map(_.code), Vector("forward(10"), "unfinished loose code is still a member")
  }

  test("trivia ranges hold only whitespace and closed comments") {
    val source = "  // one\n/* two */ x /* open"
    val value = structured(source)
    assertEquals(value.comments.map(_.text(source)), Vector("// one", "/* two */"))
    assert(value.onlyTrivia(0, 18))
    assert(!value.onlyTrivia(0, 20), "code is not trivia")
    assert(!value.onlyTrivia(5, 18), "a range must contain whole comments")
    assert(!value.onlyTrivia(21, source.length), "an unclosed comment is not trivia")
    assert(!structured("\"//\"").onlyTrivia(0, 4), "comment markers inside strings are code")
  }
}
