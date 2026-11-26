package it.evadid.vm.parsing.source

import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import munit.FunSuite

class JavaDeclarationShapeSpec extends FunSuite {
  private val student =
    """public class Student extends Person implements Comparable<Student>, java.io.Serializable {
      |  private String name;
      |  protected static int count = 0;
      |  int a, b = 2;
      |  public static final List<Map<String, Integer>> grades = new ArrayList<>();
      |  int[] marks = {1, 2};
      |
      |  public Student(String name, int age) { super(age); this.name = name; }
      |  Student() { this("?", 0); }
      |
      |  // a comment before an annotated method
      |  @Override
      |  public String toString() { return name; }
      |  private static <T> T first(List<T> values, String... rest) { return values.get(0); }
      |  static { count = 1; }
      |  class Inner { void f() { } }
      |}
      |""".stripMargin


  private def classes(source: String) = SourceParsing.parse(source, SourceLanguage.Java).structured.classes

  test("class headers and members retain main's UML declaration information") {
    val Vector(outer, inner) = classes(student): @unchecked
    assertEquals(outer.extendsNames, Vector("Person"))
    assertEquals(outer.implementsNames, Vector("Comparable", "Serializable"))
    assertEquals(inner.name, "Inner")
    assertEquals(outer.fields.map(_.name), Vector("name", "count", "a", "b", "grades", "marks"))
    assertEquals(outer.fields(3).initializer, Some("2"))
    assert(outer.fields(4).modifiers.contains("final"))
    assertEquals(outer.fields(4).javaType, "List<Map<String, Integer>>")
    val constructors = outer.functions.filter(_.isConstructor)
    assertEquals(constructors.size, 2)
    assertEquals(constructors.head.parameters.map(p => p.javaType -> p.name), Vector("String" -> "name", "int" -> "age"))
    val first = outer.functions.find(_.name == "first").get
    assertEquals(first.returnType, Some("<T> T"))
    assertEquals(first.parameters.map(p => p.javaType -> p.name), Vector("List<T>" -> "values", "String..." -> "rest"))
  }

  test("interfaces have implicit public static final fields and abstract signatures") {
    val Vector(shape, base) = classes("interface Shape { double PI = 3.14; double area(); default String label() { return \"shape\"; } } abstract class Base { protected abstract void run(); }"): @unchecked
    assert(shape.isInterface && shape.isAbstract)
    assert(shape.fields.head.modifiers.toSet == Set("public", "static", "final"))
    assert(shape.functions.find(_.name == "area").get.isAbstract)
    assert(!shape.functions.find(_.name == "label").get.isAbstract)
    assert(base.isAbstract && !base.isInterface)
  }

  test("unfinished declarations remain inspectable while class literals do not introduce classes") {
    assert(!classes("class Open { void f() {").head.closed)
    assertEquals(classes("class A { Object type = String.class; }").map(_.fields.map(_.name)), Vector(Vector("type")))
  }
}
