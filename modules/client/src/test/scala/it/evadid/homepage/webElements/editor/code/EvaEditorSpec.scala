package it.evadid.homepage.webElements.editor.code

import it.evadid.core.datastructures.language.AppLanguage
import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
import it.evadid.workbook.elements.interactionElements.programming.*
import munit.FunSuite
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleSource, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleRuntime as T}
import java.util.concurrent.CancellationException
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class EvaEditorSpec extends FunSuite {
  test("state representation chooses its matching initial tab") {
    assertEquals(EvaEditor.tabFor(ProgrammingStatePythonString("pass")), EvaEditor.Tab.Python)
    assertEquals(EvaEditor.tabFor(ProgrammingStateJavaString("class A {}")), EvaEditor.Tab.Java)
    assertEquals(EvaEditor.tabFor(ProgrammingExerciseState.mini), EvaEditor.Tab.Snap)
  }

  test("ProgrammingState converts itself at the Snap editor boundary") {
    val snap = ProgrammingStatePythonString("forward(10)").toSnapXml
    assert(snap.snapXml.contains("<project"), clue = snap.snapXml.take(120))
    assert(snap.snapXml.contains("forward"), clue = snap.snapXml)
  }

  test("CodeMirror maps Java and Python independently") {
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Java), "java")
    assertEquals(CodeMirrorEditor.languageToJs(AppLanguage.Python), "python")
  }

  private def editorFor(source: ProgrammingState): EvaEditor =
    new EvaEditor(Var(source), SnapCodeEditorConfig.Testing)

  test("unfinished sources open without deriving other representations") {
    val sources: List[ProgrammingState] = List(
      ProgrammingExerciseState.empty,
      ProgrammingStateJavaString("public class Drawing {\n  public static void main(String[] args) {\n"),
      ProgrammingStatePythonString("def draw(\n"),
      ProgrammingStateSnapXml("<project><scripts><script><block s=\"wait\"><l>1</l></block></script></scripts></project>")
    )
    sources.foreach { source =>
      val editor = editorFor(source)
      assertEquals(editor.currentState(), source)
      assertEquals(editor.activeTab.now(), EvaEditor.tabFor(source))
    }
  }

  test("selecting the current tab preserves an unfinished source exactly") {
    val source = ProgrammingStateJavaString("\n public class Drawing {\n\t\n")
    val editor = editorFor(source)
    editor.select(EvaEditor.Tab.Java)
    assertEquals(editor.currentState(), source)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
    assertEquals(editor.conversionError.now(), None)
  }

  test("failed switches retain the original tab and source") {
    val source = ProgrammingStateJavaString("public class Drawing {\n")
    val editor = editorFor(source)
    List(EvaEditor.Tab.Python, EvaEditor.Tab.Snap).foreach { tab =>
      editor.select(tab)
      assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
      assertEquals(editor.currentState(), source)
      assert(editor.conversionError.now().nonEmpty)
    }
  }

  test("viewing converted source does not publish or normalize the stored draft") {
    val source = ProgrammingStatePythonString("\nforward(12)\n\n  ")
    var published = List.empty[ProgrammingState]
    val editor = new EvaEditor(Var[ProgrammingState](source), SnapCodeEditorConfig.Testing,
      next => published = published :+ next)
    List(EvaEditor.Tab.Java, EvaEditor.Tab.Python).foreach { tab =>
      editor.select(tab)
      assertEquals(editor.activeTab.now(), tab)
      assertEquals(editor.currentState(), source)
      assertEquals(editor.conversionError.now(), None)
    }
    assertEquals(published, Nil)
  }

  test("late edits from an inactive editor cannot replace the current draft") {
    val source = ProgrammingStateJavaString("class Drawing {\n")
    val editor = editorFor(source)
    editor.publish(EvaEditor.Tab.Snap, ProgrammingExerciseState.mini)
    editor.publish(EvaEditor.Tab.Python, ProgrammingStatePythonString("forward(99)"))
    assertEquals(editor.currentState(), source)
    assertEquals(editor.activeTab.now(), EvaEditor.Tab.Java)
  }

  test("Snap edits retain additional floating objects") {
    val source = ProgrammingStateSnapXMLWithAdditionalFloatingObjects(
      "<project/>", List("\nwatcher\n", "comment"))
    val editor = editorFor(source)
    editor.publish(EvaEditor.Tab.Snap, ProgrammingStateSnapXml("<project name=\"changed\"/>"))
    assertEquals(editor.currentState(), source.copy(snapXml = "<project name=\"changed\"/>"))
  }

  test("Java run cannot return commands from a previous Snap view") {
    val editor = editorFor(ProgrammingStateJavaString("forward(12);"))
    editor.getCurrentTurtleCommands().map(_ => fail("Java fragment must not execute")).recover {
      case _: JavaEditorSession.ValidationFailure => ()
    }
  }

  test("run uses a source restored while the editor is closed") {
    val editor = editorFor(ProgrammingStatePythonString("forward(12)"))
    editor.state.set(ProgrammingStateJavaString("public class Drawing {}\n"))
    editor.getCurrentTurtleCommands().map(_ => fail("A class without main must not execute")).recover {
      case _: JavaEditorSession.ValidationFailure => assertEquals(editor.currentState(), editor.state.now())
    }
  }

  private val javaSource = ProgrammingStateJavaString(
    "public class Drawing { public static void main(String[] args) { Turtle.forward(40); Turtle.turnRight(90); } }\n")

  private class ControlledRunner extends JavaEditorSession.Runner {
    val reply = Promise[T.Execution]()
    var sources = List.empty[String]
    var closes = 0
    override def run(program: P.Program, limits: T.Limits): Future[T.Execution] = {
      sources = sources :+ program.bindings.source.source
      reply.future
    }
    override def cancel(): Boolean = true
    override def close(): Unit = closes += 1
  }

  private val drawing = T.Execution(T.Status.Completed, Vector(
    T.Command(R.TurtleCommand.Forward, 40), T.Command(R.TurtleCommand.TurnRight, 90)
  ), 8)

  test("standalone Java editor executes the exact full source and releases a closed view") {
    val runner = new ControlledRunner
    val source = Var(javaSource)
    val editor = new JavaEditor(source, runnerFactory = () => runner)
    val result = editor.getCurrentTurtleCommands()
    assertEquals(runner.sources, List(javaSource.code))
    runner.reply.success(drawing)
    result.map { commands =>
      assertEquals(commands.map(command => command.name -> command.args), List("forward" -> List(40.0), "right" -> List(90.0)))
      assertEquals(source.now(), javaSource)
      assertEquals(runner.closes, 1)
    }
  }

  test("Eva executes Java restored while its previous view is closed") {
    val runner = new ControlledRunner
    val editor = new EvaEditor(Var[ProgrammingState](ProgrammingStatePythonString("forward(99)")),
      SnapCodeEditorConfig.Testing, javaRunnerFactory = () => runner)
    editor.state.set(javaSource)
    val result = editor.getCurrentTurtleCommands()
    runner.reply.success(drawing)
    result.map { commands =>
      assertEquals(runner.sources, List(javaSource.code))
      assertEquals(commands.head.args, List(40.0))
      assertEquals(editor.currentState(), javaSource)
    }
  }

  test("a restored source cannot receive commands from an older Java execution") {
    val runner = new ControlledRunner
    val editor = new EvaEditor(Var[ProgrammingState](javaSource), SnapCodeEditorConfig.Testing,
      javaRunnerFactory = () => runner)
    val result = editor.getCurrentTurtleCommands()
    val restored = javaSource.copy(code = javaSource.code.replace("40", "25"))
    editor.state.set(restored)
    runner.reply.success(drawing)
    result.map(_ => fail("Old Java commands must be rejected")).recover {
      case _: CancellationException => assertEquals(editor.currentState(), restored)
    }
  }

  test("only completed Java execution can be used as complete turtle output") {
    assert(JavaEditor.commands(drawing).isRight)
    List(
      drawing.copy(status = T.Status.LimitExceeded),
      drawing.copy(status = T.Status.Cancelled),
      drawing.copy(status = T.Status.Failed(T.Failure.Evaluation(E.Failure.DivisionByZero)))
    ).foreach(result => assert(JavaEditor.commands(result).isLeft))
    assertEquals(JavaEditor.turtleCommands(drawing).head.args, List(40.0))
  }

  test("unmounted standalone source A to B to A invalidates the old execution") {
    val runner = new ControlledRunner
    val state = Var(javaSource)
    val editor = new JavaEditor(state, runnerFactory = () => runner)
    val result = editor.getCurrentTurtleCommands()
    state.set(javaSource.copy(code = javaSource.code.replace("40", "25")))
    state.set(javaSource)
    runner.reply.success(drawing)
    result.map(_ => fail("A to B to A must not revive the old execution")).recover {
      case _: CancellationException => assertEquals(state.now(), javaSource)
    }
  }

  test("unmounted Eva source A to B to A invalidates the old execution") {
    val runner = new ControlledRunner
    val state = Var[ProgrammingState](javaSource)
    val editor = new EvaEditor(state, SnapCodeEditorConfig.Testing, javaRunnerFactory = () => runner)
    val result = editor.getCurrentTurtleCommands()
    state.set(javaSource.copy(code = javaSource.code.replace("40", "25")))
    state.set(javaSource)
    runner.reply.success(drawing)
    result.map(_ => fail("Eva restore must not revive old Java commands")).recover {
      case _: CancellationException => assertEquals(editor.currentState(), javaSource)
    }
  }

  test("Java diagnostics map UTF16 offsets across CRLF and EOF without inventing semantic locations") {
    def diagnostic(range: Option[JavaTurtleSource.SourceRange]) =
      JavaTurtleSource.Diagnostic(JavaTurtleSource.Problem.ParseFailure, "Check this expression.", range)
    val source = "ab\r\n\t😀x\n"
    val mark = JavaEditor.diagnostics(source, diagnostic(Some(JavaTurtleSource.SourceRange(5, 7)))).head
    assertEquals((mark.line, mark.endLine, mark.fromCh, mark.toCh), (2, Some(2), Some(1), Some(3)))
    val eof = JavaEditor.diagnostics(source, diagnostic(Some(JavaTurtleSource.SourceRange(source.length, source.length)))).head
    assertEquals((eof.line, eof.fromCh, eof.toCh), (3, Some(0), Some(0)))
    assertEquals(JavaEditor.diagnostics(source, diagnostic(None)), Nil)
    val empty = JavaEditor.diagnostics("", diagnostic(Some(JavaTurtleSource.SourceRange(-1, 20)))).head
    assertEquals((empty.line, empty.fromCh, empty.toCh), (1, Some(0), Some(0)))
  }

  test("Java drawing starts east and right turns clockwise") {
    val path = it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder[Double](
      it.evadid.core.datastructures.geometry.Point(0, 0), JavaEditor.turtleCommands(drawing) :+
        it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand[Double]("forward", List(10.0)), 0.0)
    assertEquals(path.turtleState.x, 40.0)
    assertEquals(path.turtleState.y, 10.0)
  }
}
