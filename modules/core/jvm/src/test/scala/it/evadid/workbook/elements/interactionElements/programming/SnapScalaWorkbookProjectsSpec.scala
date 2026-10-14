package it.evadid.workbook.elements.interactionElements.programming

import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import java.lang.reflect.Modifier
import java.util.concurrent.{Callable, CyclicBarrier, Executors, TimeUnit}
import scala.jdk.CollectionConverters.*
import munit.FunSuite
import upickle.default.*
import it.evadid.vm.parsing.java.turtle.JavaTurtleResolution as R
import it.evadid.vm.simulation.java.JavaTurtleRuntime as T
import it.evadid.workbook.elements.interactionElements.programming.state.*

class SnapScalaWorkbookProjectsSpec extends FunSuite {
  private def javaSource(distance: Int): String =
    s"class Concurrent$distance { public static void main(String[] args) { int steps = $distance; Turtle.forward(steps++); Turtle.forward(steps); } }"

  private def commands(distance: Int): Vector[T.Command] = Vector(
    T.Command(R.TurtleCommand.Forward, distance.toDouble),
    T.Command(R.TurtleCommand.Forward, distance.toDouble + 1))

  private def concurrently[A](workers: Int)(operation: Int => A): Vector[A] = {
    val executor = Executors.newFixedThreadPool(workers)
    val barrier = new CyclicBarrier(workers)
    try {
      val results = (0 until workers).map { index =>
        executor.submit(new Callable[A] {
          override def call(): A = {
            barrier.await(10, TimeUnit.SECONDS)
            operation(index)
          }
        })
      }
      results.map(_.get(20, TimeUnit.SECONDS)).toVector
    } finally {
      executor.shutdownNow()
      assert(executor.awaitTermination(10, TimeUnit.SECONDS))
    }
  }

  test("every published embroidery XML project retains scripts and full image payloads") {
    val directory = Path.of(sys.props("user.dir"), "resources", "workbookresources", "embroidery", "existingProjects")
    val files = Files.list(directory)
    val projects = try files.iterator().asScala.filter(_.toString.endsWith(".xml")).toVector finally files.close()
    assert(projects.nonEmpty, "The published Snap project fixtures must be present.")
    projects.foreach { path =>
      val xml = Files.readString(path, StandardCharsets.UTF_8)
      val state = ProgrammingStateSnapXml(xml)
      val typed = state.toSnapScalaModel.fold(message => fail(s"$path: $message"), identity)
      assertEquals(typed.toSnapXml, state, path.toString)
      assertEquals(read[ProgrammingStateSnapScalaModel](write(typed)).toSnapXml, state, path.toString)
      assert(typed.model.project.get.scenes.nonEmpty, path.toString)
    }
  }

  test("concurrent readers share one Java analysis and keep execution state separate") {
    val entry = ProgrammingState.JavaAnalysis.forSource(javaSource(17))
    val results = concurrently(6) { _ =>
      assert(entry.classification.get._1)
      val checked = entry.program.get
      val program = checked.fold(diagnostic => fail(diagnostic.message), identity)
      val python = entry.python(checked)
      val snap = entry.snap(checked)
      val execution = T.runVm(program)
      assertEquals(execution.status, T.Status.Completed)
      assertEquals(execution.commands, commands(17))
      assertEquals(T.runVm(program), execution)
      (program, python, snap)
    }
    results.foreach { (program, python, snap) =>
      assert(program eq results.head._1)
      assertEquals(python, results.head._2)
      assertEquals(snap, results.head._3)
    }
  }

  test("concurrent Java sources retain their own bindings and validated exports") {
    concurrently(6) { index =>
      val distance = if index % 2 == 0 then 23 else 41
      val state = ProgrammingStateJavaString(javaSource(distance))
      assert(state.isClassProgram)
      val program = state.toJavaVmProgram.fold(diagnostic => fail(diagnostic.message), identity)
      assertEquals(program.bindings.source.source, state.code)
      val pythonProgram = state.toPython.toJava.toJavaVmProgram.fold(diagnostic => fail(diagnostic.message), identity)
      val snapProgram = state.toSnapXml.toJava.toJavaVmProgram.fold(diagnostic => fail(diagnostic.message), identity)
      List(program, pythonProgram, snapProgram).foreach { compiled =>
        val execution = T.runVm(compiled)
        assertEquals(execution.status, T.Status.Completed)
        assertEquals(execution.commands, commands(distance))
      }
      assertEquals(state.code, javaSource(distance))
    }
  }

  test("retained Java answer history has no analysis or export fields") {
    val history = (50 until 58).map { distance =>
      val state = ProgrammingStateJavaString(javaSource(distance))
      assert(state.isClassProgram)
      assert(state.toJavaVmProgram.isRight)
      state.toBeExpressionState.toPython
      state.toSnapXml
      state
    }
    history.foreach { state =>
      val fields = state.getClass.getDeclaredFields.filterNot(field => Modifier.isStatic(field.getModifiers))
      assert(fields.nonEmpty)
      fields.foreach { field =>
        assert(field.getType == classOf[String] || field.getType.isPrimitive,
          s"Saved Java answer retains ${field.getName}: ${field.getType.getName}")
      }
    }
  }
}
