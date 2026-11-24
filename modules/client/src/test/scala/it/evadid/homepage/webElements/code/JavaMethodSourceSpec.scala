package it.evadid.homepage.webElements.code

import munit.FunSuite
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.java.clean.model.JavaAST.{JavaClassDef, JavaMethodDef}

class JavaMethodSourceSpec extends FunSuite {
  import JavaMethodSource.*

  test("discovers classes and direct member functions without matching comments or strings") {
    val source =
      """package example;
        |class First {
        |  String note = "class Fake { void nope() {} }";
        |  // public void commentedOut() {}
        |  public int add(int left, int right) {
        |    return left + right;
        |  }
        |}
        |class Second {
        |  public void run() {}
        |}
        |""".stripMargin

    val classes = JavaMethodSource.discover(source)

    assertEquals(classes.map(_.name), List("First", "Second"))
    assertEquals(classes.head.methods.map(_.name), List("add"))
    assertEquals(classes.head.methods.head.returnType, "int")
    assertEquals(classes.head.methods.head.parameters, "int left, int right")
    assertEquals(classes(1).methods.map(_.name), List("run"))
  }

  test("the generated Turtle library is not listed as a student class, while an edited copy is") {
    val source = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(
      it.evadid.core.datastructures.language.AppLanguage.Python, "forward(10)").toJava.code
    assert(source.contains("class Turtle {"), source)
    assertEquals(JavaMethodSource.discover(source).map(_.name), List("Main"))
    val edited = source.replace("move(distance);", "move(2 * distance);")
    assertEquals(JavaMethodSource.discover(edited).map(_.name), List("Main", "Turtle"))
  }

  test("classes list attributes and constructors and format them in UML notation") {
    val source =
      """class Counter {
        |  private int count = 0;
        |  public static final String UNIT = "steps";
        |  Counter(int start) { count = start; }
        |  public int next(int step, String... labels) { return count += step; }
        |  public static void reset() { }
        |}
        |""".stripMargin
    val Vector(counter) = discover(source).toVector: @unchecked
    assertEquals(counter.fields.map(umlAttribute), List("- count: int", "+ UNIT: String"))
    assertEquals(counter.fields.map(_.isStatic), List(false, true))
    assertEquals(counter.methods.map(umlOperation), List("~ Counter(start: int)", "+ next(step: int, labels: String...): int", "+ reset()"))
    assertEquals(counter.methods.map(_.isConstructor), List(true, false, false))
    assert(counter.methods(2).isStatic)
  }

  test("relationships follow extends, implements, attribute types and other references") {
    val source =
      """interface Shape { double area(); }
        |abstract class Animal { }
        |class Dog extends Animal implements Shape {
        |  private Animal friend;
        |  private java.util.List<Dog> puppies;
        |  public double area() { return new Helper().size(); }
        |}
        |class Helper { int size() { return 1; } }
        |""".stripMargin
    val analysis = JavaMethodSource(source)
    assertEquals(analysis.relations.map(relation => (relation.from, relation.kind, relation.to, relation.role, relation.many)), List(
      ("Dog", RelationKind.Inheritance, "Animal", None, false),
      ("Dog", RelationKind.Realization, "Shape", None, false),
      // An attribute of the supertype's type is an association as well; the list of its own type is not drawn.
      ("Dog", RelationKind.Association, "Animal", Some("friend"), false),
      ("Dog", RelationKind.Dependency, "Helper", None, false)
    ))
    val withFriend = JavaMethodSource(source.replace("abstract class Animal { }", "abstract class Animal { }\nclass Owner { Dog dog; Dog[] more; }"))
    assertEquals(withFriend.relations.filter(_.from == "Owner").map(relation => (relation.kind, relation.to, relation.role, relation.many)), List(
      (RelationKind.Association, "Dog", Some("dog"), false),
      (RelationKind.Association, "Dog", Some("more"), true)
    ))
    assert(analysis.classes.find(_.name == "Shape").exists(clazz => clazz.isInterface && clazz.methods.head.isAbstract))
  }

  test("library classes show only public members and the classes that use them") {
    val source = it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw(
      it.evadid.core.datastructures.language.AppLanguage.Python, "forward(10)").toJava.code
    val analysis = JavaMethodSource(source)
    assertEquals(analysis.libraries.map(_.name), List("Turtle"))
    val turtle = analysis.libraries.head
    assert(turtle.library && turtle.fields.isEmpty, turtle.fields)
    assert(turtle.methods.exists(_.name == "forward") && turtle.methods.forall(_.visibility == SourceStructure.Visibility.Public))
    assertEquals(analysis.relations, List(Relation("Main", "Turtle", RelationKind.Dependency)))
  }

  test("attributes and constructors are inserted at their usual place, and members are removed with their line") {
    val source = "class Counter {\n  public void increment() {\n    \n  }\n}\n"
    val field = fieldDeclaration(SourceStructure.Visibility.Private, isStatic = false, "int", "count", "")
    assertEquals(field, "private int count;")
    val withField = insertField(source, discover(source).head, field)
    assertEquals(withField, "class Counter {\n  private int count;\n\n  public void increment() {\n    \n  }\n}\n")
    val withSecondField = insertField(withField, discover(withField).head,
      fieldDeclaration(SourceStructure.Visibility.Package, isStatic = true, "String", "name", " \"x\" "))
    assert(withSecondField.contains("  private int count;\n  static String name = \"x\";\n\n  public void increment()"), withSecondField)
    val constructor = constructorDeclaration("Counter", SourceStructure.Visibility.Public,
      List(SourceStructure.Param("int", "count"), SourceStructure.Param("int", "step")), List("count"))
    assertEquals(constructor, "public Counter(int count, int step) {\n    this.count = count;\n  }")
    val withConstructor = insertConstructor(withField, discover(withField).head, constructor)
    assertEquals(withConstructor, "class Counter {\n  private int count;\n\n  public Counter(int count, int step) {\n    this.count = count;\n  }\n\n  public void increment() {\n    \n  }\n}\n")
    val second = insertConstructor(withConstructor, discover(withConstructor).head, constructorDeclaration("Counter", SourceStructure.Visibility.Package, Nil, Nil))
    assert(second.contains("  }\n\n  Counter() {\n    \n  }\n\n  public void increment()"), second)
    assertEquals(discover(second).head.methods.map(umlOperation), List("+ Counter(count: int, step: int)", "~ Counter()", "+ increment()"))
    val Some(increment) = discover(withConstructor).head.methods.find(_.name == "increment"): @unchecked
    assertEquals(removeMember(withConstructor, increment.start, increment.end),
      "class Counter {\n  private int count;\n\n  public Counter(int count, int step) {\n    this.count = count;\n  }\n\n}\n")
    val inline = "class A { int x; void f() {} }"
    val Some(x) = discover(inline).head.fields.headOption: @unchecked
    assertEquals(removeMember(inline, x.start, x.end), "class A { void f() {} }")
  }

  test("names and types are validated before a declaration is generated") {
    assert(validName("count") && validName("_x1") && !validName("1x") && !validName("class") && !validName(""))
    assert(validType("int") && validType("List<Map<String, Integer>>") && validType("int[][]") && validType("String..."))
    assert(!validType("") && !validType("List<String") && !validType("int[") && !validType("void") && !validType("a;b"))
  }

  test("replacing and adding a function preserves a parseable full Java program") {
    val source = "class Example {\n  public int add(int left, int right) {\n    return left + right;\n  }\n  public void keep() {}\n}\n"
    val clazz = JavaMethodSource.discover(source).head
    val original = clazz.methods.head
    val editedDraft = "\n  public int sum(int left, int right) {\n    return left + right;\n  }"
    val edited = JavaMethodSource.replaceMethod(
      source,
      original,
      editedDraft
    )
    val afterEditClasses = JavaMethodSource.discover(edited)
    val afterEdit = afterEditClasses.head
    val selection = JavaMethodSource.findEditedMethod(
      edited,
      afterEditClasses,
      original,
      MethodRange(original.start, original.start + editedDraft.length)
    )
    assertEquals(selection.map(_.name), Some("sum"))
    val withNewFunction = JavaMethodSource.insertMethod(
      edited,
      afterEdit,
      "public void reset() {\n    \n  }"
    )

    assert(JavaMethodSource.parses(edited))
    assert(JavaMethodSource.parses(withNewFunction))
    assert(!JavaMethodSource.parses("class Example {"))
    assertEquals(
      JavaMethodSource.discover(withNewFunction).head.methods.map(_.name),
      List("sum", "keep", "reset")
    )
  }

  test("function declarations include static only when selected and remain parseable") {
    List(true, false).foreach { isStatic =>
      val declaration = methodDeclaration(returnType = "void", name = "draw",
        parameters = "int distance", isStatic = isStatic)
      val modifiers = if isStatic then "public static" else "public"
      assertEquals(declaration, s"$modifiers void draw(int distance) {\n    \n  }")
      val program = JavaParser.parse(s"class Drawing { $declaration }")
        .fold(error => fail(error.getMessage), identity)
      assertEquals(program.statements.size, 1)
      val clazz = program.statements.head.statement match {
        case value: JavaClassDef => value
        case other => fail(s"Expected a class definition, got $other")
      }
      assertEquals(clazz.body.statements.size, 1)
      val method = clazz.body.statements.head match {
        case value: JavaMethodDef => value
        case other => fail(s"Expected a method definition, got $other")
      }
      assertEquals(method.name, "draw")
      assertEquals(method.parameters.map(_.name), Seq("distance"))
      assertEquals(method.modifiers, if isStatic then Seq("public", "static") else Seq("public"))
    }
  }

  test("inserting static and instance declarations preserves surrounding source and separators") {
    val original = "// \ud83d\ude42 unchanged\nclass Drawing {\n\tpublic static void main(String[] args) {}\n\tprivate void keep() {}\n\t \n}  \t\n\nclass Other { public void keepOther() {} }\n\t "
    for {
      separator <- List("\n", "\r\n", "\r")
      isStatic <- List(true, false)
    } {
      val source = original.replace("\n", separator)
      val clazz = discover(source).head
      val declaration = methodDeclaration(returnType = "void", name = "draw",
        parameters = "double distance", isStatic = isStatic)
      val inserted = insertMethod(source, clazz, declaration)
      val normalized = declaration.replace("\n", separator)
      assertEquals(inserted, source.substring(0, clazz.bodyEnd) + separator + "  " + normalized + separator + source.substring(clazz.bodyEnd))
      assert(parses(inserted))
      assertEquals(discover(inserted).head.methods.map(_.name), List("main", "keep", "draw"))
      assertEquals(discover(inserted).map(_.name), List("Drawing", "Other"))
    }
  }

  test("owned ranges preserve surrounding source across invalid edits and recovery") {
    val source = "class Example {\r\n  public void edit() {}\r\n  public void keep() { int value = 9; }\r\n}\r\n\t  "
    val original = discover(source).head.methods.head
    val prefix = source.substring(0, original.start)
    val suffix = source.substring(original.end)
    var currentSource = source
    var range = MethodRange(original.start, original.end)
    val drafts = List(
      "public void edit(",
      "\n  public void edit() {\n    int value = ",
      "\n  public void repaired() {\n    int value = 8;\n  }\n  "
    )

    drafts.foreach { draft =>
      val (replaced, nextRange) = range.replace(currentSource, draft).get
      assertEquals(replaced, prefix + draft + suffix)
      assertEquals(replaced.substring(nextRange.start, nextRange.end), draft)
      assertEquals(nextRange, MethodRange(original.start, original.start + draft.length))
      currentSource = replaced
      range = nextRange
    }

    assertEquals(findEditedMethod(currentSource, discover(currentSource), original, range).map(_.name), Some("repaired"))
    assertEquals(discover(currentSource).head.methods.map(_.name), List("repaired", "keep"))
  }

  test("owned ranges validate UTF-16 bounds and support empty insertions") {
    val source = "\ud83d\ude42 prefix method tail"
    val start = source.indexOf("method")
    val range = MethodRange(start, start + "method".length)
    assertEquals(range.replace(source, "replacement"), Some(
      (source.substring(0, start) + "replacement" + source.substring(range.end), MethodRange(start, start + 11))
    ))
    assertEquals(MethodRange(0, 0).replace(source, "x").map(_._1), Some("x" + source))
    assertEquals(MethodRange(source.length, source.length).replace(source, "x").map(_._1), Some(source + "x"))
    assertEquals(MethodRange(0, source.length).replace(source, ""), Some(("", MethodRange(0, 0))))
    List(
      MethodRange(-1, 0),
      MethodRange(0, -1),
      MethodRange(3, 2),
      MethodRange(0, source.length + 1),
      MethodRange(source.length + 1, source.length + 1)
    ).foreach(invalid => assertEquals(invalid.replace(source, "x"), None))
  }

  test("method gutter lines use the original range across line endings and leading draft trivia") {
    val original = "// Grüße 🙂\nclass Example {\n  public void helper() {}\n\n  public static void main(String[] args) {}\n}\n"
    List("\n", "\r\n", "\r").foreach { separator =>
      val source = original.replace("\n", separator)
      val selected = initialMethod(source).get
      val range = MethodRange(selected.start, selected.end)
      assertEquals(firstLineNumber(source, Some(range)), 5)
      val draft = separator + "/* retained */" + separator + source.substring(range.start, range.end)
      val (edited, nextRange) = range.replace(source, draft).get
      assertEquals(firstLineNumber(edited, Some(nextRange)), 5)
      assertEquals(edited.substring(nextRange.start, nextRange.end), draft)
      val moved = "// inserted" + separator + source
      val movedMain = initialMethod(moved).get
      assertEquals(firstLineNumber(moved, Some(MethodRange(movedMain.start, movedMain.end))), 6)
      assertEquals(firstLineNumber(source, None), 1)
      assertEquals(firstLineNumber(source, Some(MethodRange(0, source.length + 1))), 1)
    }
    assertEquals(firstLineNumber("class Example { public void helper() {} }", Some(MethodRange(16, 39))), 1)
  }

  test("gutter offsets handle mixed separators, UTF-16 positions and EOF") {
    List(
      "" -> Vector(1),
      "x\nz" -> Vector(1, 1, 2, 2),
      "x\r\nz" -> Vector(1, 1, 1, 2, 2),
      "x\rz" -> Vector(1, 1, 2, 2),
      "🐢\r\nx\rz\n" -> Vector(1, 1, 1, 1, 2, 2, 3, 3, 4)
    ).foreach { (source, expectedLines) =>
      assertEquals(expectedLines.length, source.length + 1)
      expectedLines.zipWithIndex.foreach { (expectedLine, offset) =>
        assertEquals(firstLineNumber(source, Some(MethodRange(offset, source.length))), expectedLine,
          clue = (source, offset))
      }
    }
  }

  private val singleMethodSource = "class Example {\n  public void edit() {}\n  public void keep() {}\n}\n"

  private def candidateFor(draft: String): Option[JavaMethod] = {
    val original = discover(singleMethodSource).head.methods.head
    val (source, range) = MethodRange(original.start, original.end).replace(singleMethodSource, draft).get
    findEditedMethod(source, discover(source), original, range)
  }

  test("method projection accepts surrounding whitespace and comments without trimming the range") {
    val draft = "\r\n /* \ud83d\ude42 void fake() {} */\r\n public void renamed() {} // retained tail\r\n\t "
    assertEquals(candidateFor(draft).map(_.name), Some("renamed"))
    val original = discover(singleMethodSource).head.methods.head
    val (source, range) = MethodRange(original.start, original.end).replace(singleMethodSource, draft).get
    assertEquals(source.substring(range.start, range.end), draft)
    assertEquals(candidateFor("/* incomplete"), None)
  }

  test("method projection rejects extra declarations and non-comment text") {
    List(
      "public void renamed() {} public void pasted() {}",
      "public void renamed() {} int field = 1;",
      "public void renamed() {} static { int value = 1; }",
      "public void renamed() {} \"not whitespace\"",
      "public void renamed() {} ???;",
      "} class Other { public void pasted() {}"
    ).foreach(draft => assertEquals(candidateFor(draft), None, clue = draft))
  }

  test("method projection requires full containment and an unambiguous class") {
    val classes = discover(singleMethodSource)
    val original = classes.head.methods.head
    assertEquals(findEditedMethod(singleMethodSource, classes, original,
      MethodRange(original.start, original.end - 1)), None)
    assertEquals(findEditedMethod(singleMethodSource, classes, original,
      MethodRange(original.start + 1, original.end)), None)
    assertEquals(findEditedMethod(singleMethodSource, classes :+ classes.head, original,
      MethodRange(original.start, original.end)), None)
    assertEquals(findEditedMethod(singleMethodSource, classes, original.copy(className = "Missing"),
      MethodRange(original.start, original.end)), None)

    val multipleClasses = singleMethodSource + "class Other { public void untouched() {} }"
    val draft = "public void renamed() {}"
    val (changed, range) = MethodRange(original.start, original.end).replace(multipleClasses, draft).get
    assertEquals(findEditedMethod(changed, discover(changed), original, range).map(_.name), Some("renamed"))
  }

  test("selection follows the complete signature across source reordering") {
    val source = "class Example { public int value(int n) { return n; } public int value(boolean flag) { return 1; } }"
    val classes = discover(source)
    val previous = classes.head.methods.head
    val reordered = discover("class Example { public int value(boolean flag) { return 1; } public int value(int n) { return n; } }")
    assertEquals(findSelection(reordered, previous).map(_.parameters), Some("int n"))
    assertEquals(findSelection(reordered, previous.copy(parameters = "String value")), None)
    assertEquals(findSelection(reordered, previous.copy(returnType = "void")), None)
    assertEquals(findSelection(reordered, previous.copy(className = "Other")), None)
  }

  test("selection rejects duplicate methods and duplicate class names") {
    val classes = discover("class Example { public void edit() {} }")
    val previous = classes.head.methods.head
    val duplicateMethod = classes.head.copy(methods = List(previous, previous.copy(start = previous.start + 1)))
    assertEquals(findSelection(List(duplicateMethod), previous), None)
    assertEquals(findSelection(classes :+ classes.head, previous), None)
    assertEquals(findSelection(Nil, previous), None)
  }

  test("method identity ignores offsets but distinguishes signatures") {
    val original = discover(singleMethodSource).head.methods.head
    assert(sameMethod(original, original.copy(start = 300, end = 400)))
    List(
      original.copy(className = "Other"),
      original.copy(name = "other"),
      original.copy(returnType = "int"),
      original.copy(parameters = "int value")
    ).foreach(other => assert(!sameMethod(original, other)))
  }

  test("comment discovery handles LF, CRLF and CR without changing source offsets") {
    val source = "class Example {\n  // public void fake() {}\n  public void edit() { String value = \"}\"; }\n}\n"
    List("\n", "\r\n", "\r").foreach { separator =>
      val code = source.replace("\n", separator)
      val classes = discover(code)
      assertEquals(classes.map(_.name), List("Example"), clue = separator)
      assertEquals(classes.head.methods.map(_.name), List("edit"), clue = separator)
      val method = classes.head.methods.head
      assertEquals(code.substring(method.start, method.end), "public void edit() { String value = \"}\"; }")
      assert(parses(code), clue = separator)
    }
  }

  test("adding a method preserves whitespace before and after the class closing brace") {
    val source = "// unchanged\nclass Example {\r\n\tpublic void keep() {}\r\n\t \r\n}  \t\r\n\r\n"
    val clazz = discover(source).head
    val declaration = "public void reset() {}"
    val inserted = insertMethod(source, clazz, declaration)
    assert(inserted.startsWith(source.substring(0, clazz.bodyEnd)))
    assert(inserted.endsWith(source.substring(clazz.bodyEnd)))
    assertEquals(discover(inserted).head.methods.map(_.name), List("keep", "reset"))
  }

  test("initial function selects the unique main and preserves its exact source range") {
    val expectedMain = "public static void main(String[] args) {\n    helper();\n  }"
    val source =
      "class Program {\n" +
        "  public static void helper() { int value = 1; }\n" +
        s"  $expectedMain\n" +
        "  private int untouched() { return 7; }\n" +
        "}\n"

    val selected = initialMethod(source)

    assertEquals(
      selected.map(function => (function.className, function.name, function.returnType, function.parameters)),
      Some(("Program", "main", "void", "String[] args"))
    )
    assert(selected.exists(function => MethodRange(function.start, function.end).isWithin(source)))
    assertEquals(selected.map(function => source.substring(function.start, function.end)), Some(expectedMain))
    assertEquals(selected.map(function => replaceMethod(source, function, expectedMain)), Some(source))
  }

  test("initial function rejects main methods across multiple classes") {
    val source =
      "class First { public static void main(String[] args) {} }\n" +
        "class Second { public static void main(String[] args) {} }\n"

    assertEquals(initialMethod(source), None)
  }

  test("initial function rejects ambiguous class names and duplicate main signatures") {
    val duplicateClassName =
      "class Program { public static void main(String[] args) {} }\n" +
        "class Program { public void helper() {} }\n"
    val duplicateMainSignature =
      "class Program {\n" +
        "  public static void main(String[] args) {}\n" +
        "  public static void main(String[] args) {}\n" +
        "}\n"

    assertEquals(initialMethod(duplicateClassName), None)
    assertEquals(initialMethod(duplicateMainSignature), None)
  }

  test("initial function rejects missing main and structurally incomplete source") {
    List(
      "class Program { public void helper() {} }",
      "class Program { public static void main(String[] args) {}",
      "class Program { public static void main(String[] args) {} /* unclosed"
    ).foreach(source => assertEquals(initialMethod(source), None, clue = source))
  }

  test("initial function after a helper matches discovery from the complete fresh source") {
    val source =
      "class Program {\r\n" +
        "  public String helper() { return \"class Fake { void main() {} }\"; }\r\n" +
        "  public static void main(String[] args) {\r\n" +
        "    helper();\r\n" +
        "  }\r\n" +
        "}\r\n"
    val discoveredMain = discover(source).flatMap(_.methods).filter(_.name == "main")

    assertEquals(discoveredMain.length, 1)
    assertEquals(initialMethod(source), discoveredMain.headOption)
    assertEquals(
      initialMethod(source).map(function => source.substring(function.start, function.end)),
      Some("public static void main(String[] args) {\r\n    helper();\r\n  }")
    )
  }

  test("initial function rejects invalid entry point signatures and named main overloads") {
    List(
      "non-public" -> "class Program { static void main(String[] args) {} }",
      "non-static" -> "class Program { public void main(String[] args) {} }",
      "wrong return type" -> "class Program { public static int main(String[] args) { return 0; } }",
      "wrong parameters" -> "class Program { public static void main(int[] args) {} }",
      "named overload" ->
        "class Program { public static void main(String[] args) {} public void main(int value) {} }"
    ).foreach { case (label, source) =>
      assertEquals(initialMethod(source), None, clue = label)
    }
  }

  test("initial function accepts qualified String arrays, modifier order and parameter names") {
    List(
      (
        "class Program { public static void main(java.lang.String[] arguments) {} }",
        "public static void main(java.lang.String[] arguments) {}",
        "java.lang.String[] arguments"
      ),
      (
        "class Program { static final public void main(String [ ] $arguments) {} }",
        "static final public void main(String [ ] $arguments) {}",
        "String [ ] $arguments"
      ),
      (
        "class Program { static public void main(java.lang.String [ ] _args) {} }",
        "static public void main(java.lang.String [ ] _args) {}",
        "java.lang.String [ ] _args"
      )
    ).foreach { case (source, expectedMain, expectedParameters) =>
      val selected = initialMethod(source)
      assertEquals(
        selected.map(function => (function.className, function.name, function.returnType, function.parameters)),
        Some(("Program", "main", "void", expectedParameters))
      )
      assert(selected.exists(function => MethodRange(function.start, function.end).isWithin(source)))
      assertEquals(selected.map(function => source.substring(function.start, function.end)), Some(expectedMain))
    }
  }
}
