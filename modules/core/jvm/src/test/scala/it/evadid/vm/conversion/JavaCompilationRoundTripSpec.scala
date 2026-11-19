package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.elements.interactionElements.programming.state.*
import java.nio.file.Files
import java.net.URLClassLoader
import java.io.ByteArrayOutputStream
import javax.tools.ToolProvider
import scala.jdk.CollectionConverters.*
import munit.FunSuite

class JavaCompilationRoundTripSpec extends FunSuite {
  test("the object scenario emits compilable Java and preserves statement order") {
    val source = ProgrammingStateRaw(Python, "class Counter:\n    def __init__(self, value: float):\n        self.value = value\n    def read(self) -> float:\n        return self.value\ncounter = Counter(3)\ncounter.value = 7\nresult = counter.read()\n")
    val converted = source.convertToLanguage(Java)
    val code = converted.finalState.get.asInstanceOf[ProgrammingStateJavaStructured].toString
    val directory = Files.createTempDirectory("edusquirrel-java-conversion-")
    try {
      val file = directory.resolve("Main.java")
      Files.writeString(file, code)
      val errors = new ByteArrayOutputStream()
      val compiler = Option(ToolProvider.getSystemJavaCompiler).getOrElse(fail("The JVM tests require a JDK compiler."))
      assertEquals(compiler.run(null, null, errors, "-d", directory.toString, file.toString), 0,
        errors.toString + "\n" + code)
      val loader = new URLClassLoader(Array(directory.toUri.toURL), null)
      try {
        val main = loader.loadClass("Main")
        val entry = main.getDeclaredMethod("main", classOf[Array[String]])
        entry.setAccessible(true)
        entry.invoke(null, Array.empty[String].asInstanceOf[Object])
        val result = main.getDeclaredField("result")
        result.setAccessible(true)
        assertEquals(result.getDouble(null), 7.0)
      } finally loader.close()
    } finally {
      val files = Files.walk(directory)
      try files.iterator().asScala.toVector.reverse.foreach(Files.delete) finally files.close()
    }
  }

  /** Compiles `code` as Main.java, runs `Main.main` headless and returns the loader for inspection. */
  private def compileAndRun[A](code: String)(inspect: ClassLoader => A): A = {
    System.setProperty("java.awt.headless", "true")
    val directory = Files.createTempDirectory("edusquirrel-java-dependency-")
    try {
      val file = directory.resolve("Main.java")
      Files.writeString(file, code)
      val errors = new ByteArrayOutputStream()
      val compiler = Option(ToolProvider.getSystemJavaCompiler).getOrElse(fail("The JVM tests require a JDK compiler."))
      assertEquals(compiler.run(null, null, errors, "-d", directory.toString, file.toString), 0, errors.toString + "\n" + code)
      val loader = new URLClassLoader(Array(directory.toUri.toURL), getClass.getClassLoader)
      try {
        val main = loader.loadClass("Main").getDeclaredMethod("main", classOf[Array[String]])
        main.setAccessible(true) // the generated classes are package-private
        main.invoke(null, Array.empty[String].asInstanceOf[Object])
        inspect(loader)
      } finally loader.close()
    } finally {
      val files = Files.walk(directory)
      try files.iterator().asScala.toVector.reverse.foreach(Files.delete) finally files.close()
    }
  }

  test("turtle programs converted to Java compile and draw with the generated Turtle class on a plain JVM") {
    val python = ProgrammingStateRaw(Python, "for _ in range(4):\n    forward(100)\n    left(90)\ncircle(10)\n")
    val code = python.convertToLanguage(Java).finalState.get.asInstanceOf[ProgrammingStateJavaStructured].toString
    compileAndRun(code) { loader =>
      val accessor = loader.loadClass("Turtle").getDeclaredMethod("segments")
      accessor.setAccessible(true)
      val segments = accessor.invoke(null).asInstanceOf[java.util.List[Array[Double]]].asScala.toVector
      assertEquals(segments.take(4).map(_.map(value => math.round(value).toDouble).toVector), Vector(
        Vector(0.0, 0.0, 100.0, 0.0), Vector(100.0, 0.0, 100.0, 100.0), Vector(100.0, 100.0, 0.0, 100.0), Vector(0.0, 100.0, 0.0, 0.0)))
      assertEquals(segments.size, 4 + 36, "the circle is drawn in 36 segments")
    }
  }

  test("programs that read a URL compile with the generated Web class") {
    val python = ProgrammingStateRaw(Python, "page = fetch_text(\"http://localhost:1/unused\")\nprint(page)\n")
    val code = python.convertToLanguage(Java).finalState.get.toString
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
}
