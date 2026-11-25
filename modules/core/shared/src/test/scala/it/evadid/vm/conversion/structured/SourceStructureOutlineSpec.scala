package it.evadid.vm.conversion.structured

import it.evadid.vm.conversion.structured.SourceStructure.*
import it.evadid.vm.conversion.structured.SourceStructure.MemberOutline.*
import munit.FunSuite

class SourceStructureOutlineSpec extends FunSuite {
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

  test("class outlines collect supertypes, fields, constructors and methods in source order") {
    val Vector(outer, inner) = javaClassOutlines(student): @unchecked
    assertEquals((outer.name, outer.isInterface, outer.isAbstract), ("Student", false, false))
    assertEquals(outer.extendsNames, Vector("Person"))
    assertEquals(outer.implementsNames, Vector("Comparable", "Serializable"))
    assertEquals(student.charAt(outer.bodyOpen), '{')
    assertEquals(student.charAt(outer.bodyClose), '}')
    assertEquals(inner.name, "Inner")
    assertEquals(inner.methods.map(_.name), Vector("f"))
  }

  test("fields keep visibility, static, type, initializer and shared declarations") {
    val fields = javaClassOutlines(student).head.fields
    assertEquals(fields.map(field => (field.visibility.symbol, field.isStatic, field.javaType, field.name)), Vector(
      ("-", false, "String", "name"),
      ("#", true, "int", "count"),
      ("~", false, "int", "a"),
      ("~", false, "int", "b"),
      ("+", true, "List<Map<String, Integer>>", "grades"),
      ("~", false, "int[]", "marks")
    ))
    assertEquals(fields(1).initializer, Some("0"))
    assertEquals(fields(3).initializer, Some("2"))
    assert(fields(4).isFinal)
  }

  test("constructors are recognised with and without modifiers and keep their source range") {
    val Vector(full, short) = javaClassOutlines(student).head.constructors: @unchecked
    assertEquals(full.visibility, Visibility.Public)
    assertEquals(full.params, Vector(Param("String", "name"), Param("int", "age")))
    assertEquals(student.substring(full.start, full.end), "public Student(String name, int age) { super(age); this.name = name; }")
    assertEquals(short.visibility, Visibility.Package)
    assert(short.params.isEmpty && short.hasBody)
  }

  test("methods skip annotations and comments and parse generic and varargs parameters") {
    val Vector(toText, first) = javaClassOutlines(student).head.methods: @unchecked
    assertEquals((toText.visibility, toText.isStatic, toText.returnType, toText.name), (Visibility.Public, false, "String", "toString"))
    assert(student.substring(toText.start, toText.end).startsWith("@Override"))
    assertEquals((first.visibility, first.isStatic, first.returnType), (Visibility.Private, true, "<T> T"))
    assertEquals(first.params, Vector(Param("List<T>", "values"), Param("String...", "rest")))
    assertEquals(first.parameters, "List<T> values, String... rest")
  }

  test("interfaces and abstract classes mark abstract methods and default visibility") {
    val source =
      """interface Shape { double PI = 3.14; double area(); default String label() { return "shape"; } }
        |abstract class Base { protected abstract void run(); }""".stripMargin
    val Vector(shape, base) = javaClassOutlines(source): @unchecked
    assert(shape.isInterface && shape.isAbstract)
    assertEquals(shape.fields.map(field => (field.visibility, field.isStatic, field.isFinal)), Vector((Visibility.Public, true, true)))
    assertEquals(shape.methods.map(method => (method.name, method.visibility, method.isAbstract, method.hasBody)),
      Vector(("area", Visibility.Public, true, false), ("label", Visibility.Public, false, true)))
    assert(base.isAbstract && !base.isInterface)
    assertEquals(base.methods.map(method => (method.visibility, method.isAbstract, method.hasBody)), Vector((Visibility.Protected, true, false)))
  }

  test("unfinished classes and class literals are not outlined") {
    assertEquals(javaClassOutlines("class Open { void f() {"), Vector.empty)
    assertEquals(javaClassOutlines("class A { Object type = String.class; }").map(_.fields.map(_.name)), Vector(Vector("type")))
    assertEquals(javaClassOutlines("class B;"), Vector.empty)
  }
}
