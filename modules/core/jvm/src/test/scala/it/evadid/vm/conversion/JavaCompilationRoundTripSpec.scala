package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.interactions.programming.state.*
import java.nio.file.Files
import java.io.ByteArrayOutputStream
import javax.tools.ToolProvider
import scala.jdk.CollectionConverters.*
import munit.FunSuite

class JavaCompilationRoundTripSpec extends FunSuite {
  import JavaTestRuntime.compileAndRun

  /** Compiles `code` as Main.java without running it. */
  private def assertCompiles(code: String): Unit = {
    val compiler = Option(ToolProvider.getSystemJavaCompiler).getOrElse(fail("The JVM tests require a JDK compiler."))
    val directory = Files.createTempDirectory("edusquirrel-java-web-")
    try {
      val file = directory.resolve("Main.java")
      Files.writeString(file, code)
      val errors = new ByteArrayOutputStream()
      assertEquals(compiler.run(null, null, errors, "-d", directory.toString, file.toString), 0, errors.toString + "\n" + code)
    } finally {
      val files = Files.walk(directory)
      try files.iterator().asScala.toVector.reverse.foreach(Files.delete) finally files.close()
    }
  }

  test("the object scenario emits compilable Java and preserves statement order") {
    val source = ProgrammingStateRaw(Python, "class Counter:\n    def __init__(self, value: float):\n        self.value = value\n    def read(self) -> float:\n        return self.value\ncounter = Counter(3)\ncounter.value = 7\nresult = counter.read()\n")
    val converted = source.convertToLanguage(Java)
    val code = converted.finalState.get.asInstanceOf[ProgrammingStateJavaStructured].toString
    compileAndRun(code) { loader =>
      val result = loader.loadClass("Main").getDeclaredField("result")
      result.setAccessible(true)
      assertEquals(result.getDouble(null), 7.0)
    }
  }

  test("programs that read a URL compile with the generated Web class") {
    val python = ProgrammingStateRaw(Python, "page = fetch_text(\"http://localhost:1/unused\")\nprint(page)\n")
    assertCompiles(python.convertToLanguage(Java).finalState.get.toString)
  }

  test("Python and block programs keep fractional steps and negative floor division when run as Java") {
    val python = ProgrammingStateRaw(Python, """def step():
      |    forward((2 + 3) / 2)
      |    forward(-7 // 2)
      |    forward(int(9.5) / int(2.1))
      |step()
      |""".stripMargin)
    val expected = Vector(2.5, -4.0, 4.5)
    for source <- List(python, python.toBeExpression) do {
      val result = source.convertToLanguage(Java)
      val java = result.finalState.getOrElse(fail(result.conversionInfo.toString))
      compileAndRun(java.toString) { loader =>
        val distances = JavaTestRuntime.turtleSegments(loader).map(segment => segment(2) - segment(0))
        assertEquals(distances, expected)
      }
      val restored = java.convertToLanguage(Python).finalState.getOrElse(fail(java.toString))
      assertEquals(restored.toBeExpression.turtleCommandExecutionInfo.completedCommands.flatMap(_.args).toVector, expected)
    }
  }

  test("Python conversion built-ins become Java casts and library calls that compile and compute the same values") {
    val python = ProgrammingStateRaw(Python,
      "x = int(-3.7)\nt = str(x)\nn = int(\"42\") + float(\"0.5\")\na = abs(x)\nb = bool(a)\nif b:\n    forward(a + n)\n")
    val code = python.convertToLanguage(Java).finalState.get.toString
    Seq("(int) (-3.7)", "String.valueOf(x)", "Integer.parseInt(\"42\")", "Double.parseDouble(\"0.5\")", "Math.abs(x)", "(a != 0)")
      .foreach(spelling => assert(code.contains(spelling), s"$spelling\n$code"))
    compileAndRun(code) { loader =>
      assertEquals(JavaTestRuntime.turtleSegments(loader), Vector(Vector(0.0, 0.0, 45.5, 0.0)), "int truncates towards zero: abs(-3) + 42.5")
    }
  }

  test("text comparison, numeric conditions, module updates, reserved names and multi-value print compile and draw") {
    val python = ProgrammingStateRaw(Python, """name = "ab"
      |new = 2
      |count = 3
      |def step():
      |    global count
      |    count = count - 1
      |while count:
      |    if name == "ab" and 0 < count < 5:
      |        forward(count * new)
      |    step()
      |print("done", count)
      |""".stripMargin)
    val code = python.convertToLanguage(Java).finalState.get.toString
    compileAndRun(code) { loader =>
      assertEquals(JavaTestRuntime.turtleSegments(loader).map(segment => segment(2) - segment(0)), Vector(6.0, 4.0, 2.0), code)
    }
  }
}
