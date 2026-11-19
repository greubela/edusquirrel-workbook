package it.evadid.vm.conversion

import java.io.ByteArrayOutputStream
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.ToolProvider
import scala.jdk.CollectionConverters.*

/** Compiles printed Java with the JDK compiler and runs it headless, for tests that check what Java programs do. */
object JavaTestRuntime {
  /** Compiles `code` as Main.java, runs `Main.main` and returns `inspect` of the loaded classes. */
  def compileAndRun[A](code: String)(inspect: ClassLoader => A): A = {
    System.setProperty("java.awt.headless", "true")
    val directory = Files.createTempDirectory("edusquirrel-java-dependency-")
    try {
      val file = directory.resolve("Main.java")
      Files.writeString(file, code)
      val errors = new ByteArrayOutputStream()
      val compiler = Option(ToolProvider.getSystemJavaCompiler).getOrElse(throw AssertionError("The JVM tests require a JDK compiler."))
      val status = compiler.run(null, null, errors, "-d", directory.toString, file.toString)
      if status != 0 then throw AssertionError(errors.toString + "\n" + code)
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

  /** The segments `{x1, y1, x2, y2}` drawn by the generated `Turtle` class. */
  def turtleSegments(loader: ClassLoader): Vector[Vector[Double]] = {
    val accessor = loader.loadClass("Turtle").getDeclaredMethod("segments")
    accessor.setAccessible(true)
    accessor.invoke(null).asInstanceOf[java.util.List[Array[Double]]].asScala.toVector.map(_.toVector)
  }
}
