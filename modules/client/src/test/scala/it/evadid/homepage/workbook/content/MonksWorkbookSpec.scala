package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.{Java, SnapLanguage}
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.compilation.java.turtle.JavaTurtleCompiler
import it.evadid.vm.simulation.java.JavaTurtleInterpreter
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateJavaStructured, ProgrammingStateSnapScalaModel}
import munit.FunSuite

class MonksWorkbookSpec extends FunSuite {
  private val editorIds = List("e1", "e2", "e3", "e4", "e5", "e6a", "e6b")
  private def checked(code: String): JavaTurtleInterpreter.Execution =
    JavaTurtleInterpreter.runVm(JavaTurtleCompiler.compile(code).fold(problem => fail(problem.message), identity))

  test("all drawing tasks retain the workbook's Snap and Java editor choices") {
    val tasks = CreateMonksWorkbook(null).createWorkbook.allChildrenFullSubtree.collect {
      case task: TurtleRecreateShapeInteraction => task
    }
    assertEquals(tasks.size, editorIds.size + 3)
    tasks.foreach { task =>
      assertEquals(task.exerciseConfig.allowedEditorLanguages, Seq(SnapLanguage, Java), task.elementId)
      assert(task.desiredResult.renderLines.nonEmpty, task.elementId)
      assert(task.initProgram.isInstanceOf[ProgrammingStateJavaStructured] || task.initProgram.isInstanceOf[ProgrammingStateSnapScalaModel], task.elementId)
    }
  }

  editorIds.foreach { id =>
    test(s"$id starting code compiles with the current Java model and converts to Snap") {
      val code = CreateMonksWorkbook.EditorCode(id)
      JavaTurtleCompiler.compile(code).fold(problem => fail(problem.message), _ => ())
      val source = ProgrammingStateJavaStructured(code)
      val snap = source.convertToLanguage(SnapLanguage).finalState.getOrElse(fail("No Snap form for " + id))
      assert(snap.isInstanceOf[ProgrammingStateSnapScalaModel])
      // Parsing the exported blocks must also work; producing XML alone is insufficient.
      snap.toBeExpression
    }
  }

  test("the deliberately missing base case terminates at the Java execution limit") {
    val code = CreateMonksWorkbook.EditorCode("e6a")
    assertEquals(checked(code).status, JavaTurtleInterpreter.Status.LimitExceeded)
    val shared = ProgrammingStateJavaStructured(code).toBeExpression.turtleCommandExecutionInfo
    assertEquals(shared.executionStatus, it.evadid.vm.simulation.turtle.TurtleExecutionStatus.LimitExceeded)
  }

  test("a solved recursive pyramid draws the same target after switching through Snap") {
    val body = """if (n == 0) { return; }
                 |stein(n * 20);
                 |hoch();
                 |springe(10);
                 |zeichnePyramide(n - 1);
                 |springe(-10);
                 |runter();""".stripMargin
    val source = ProgrammingStateJavaStructured(CreateMonksWorkbook.EditorCode("e5").replace("// TODO", body))
    val execution = checked(source.toString)
    assertEquals(execution.status, JavaTurtleInterpreter.Status.Completed)
    val expected = CreateMonksWorkbook.editorTarget("e5").toTurtleProgram
    assertEquals(execution.commands.map(_.toDrawingCommand).toSeq, expected)
    val snap = source.convertToLanguage(SnapLanguage).finalState.getOrElse(fail("No Snap form for the pyramid"))
    val fromSnap = snap.toBeExpression.turtleCommandExecutionInfo.completedCommands
    assertEquals(fromSnap.toSeq, expected)
    assert(fromSnap.contains(TurtleCommand.PenUp[Double]()))
    assert(fromSnap.contains(TurtleCommand.PenDown[Double]()))
  }
}
