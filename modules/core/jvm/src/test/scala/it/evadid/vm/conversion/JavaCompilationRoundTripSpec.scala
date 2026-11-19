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
}
