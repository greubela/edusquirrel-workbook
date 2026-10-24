package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.SerializedException
import it.evadid.vm.test.{BeTestResult, BeTestSuite}
import it.evadid.vm.parsing.java.turtle.JavaTurtleVmPrograms
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.{ProgrammingStateBeExpression, ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.JavaTurtleEditingBridge
import upickle.default.ReadWriter

import scala.concurrent.Future

case class ProgrammingUnitTestGrader(testSuite: BeTestSuite,
    importance: GradingImportance = GradingImportance.REQUIRED_TEST)
    extends WorkbookInteractionGrader[ProgrammingState, Any] derives ReadWriter {
  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity()

  private def evaluateJava(source: => Either[String, ProgrammingStateJavaString], clients: List[ExecutionClient]): Future[BeTestResult] =
    Future(source.flatMap(_.toJavaVmProgram.left.map(_.message))).flatMap {
      case Right(program) => testSuite.evaluateJavaTurtle(program, clients)
      case Left(_) => Future.successful(BeTestResult.Error("This source is not supported by the checked Java Turtle runner."))
    }

  override def gradeState(state: ProgrammingState, clients: List[ExecutionClient]): Future[GradingResult[ProgrammingState, Any]] = {
    val evaluation = state match {
      case ProgrammingStatePythonString(source) => Future(JavaTurtleEditingBridge.hasPythonMetadata(source)).flatMap {
        case true => evaluateJava(JavaTurtleEditingBridge.fromPython(source), clients)
        case false => testSuite.evaluateOn(source, clients)
      }
      case snap: ProgrammingStateSnapXml => Future(JavaTurtleEditingBridge.hasSnapMetadata(snap.snapXml)).flatMap {
        case true => evaluateJava(JavaTurtleEditingBridge.fromSnap(snap), clients)
        case false => Future.successful(BeTestResult.Error("Unit tests cannot infer a Java program from an untyped Snap project."))
      }
      case ProgrammingStateBeExpression(root: JavaTurtleVmPrograms.Root) =>
        testSuite.evaluateJavaTurtle(JavaTurtleVmPrograms.fromRoot(root), clients)
      case java: ProgrammingStateJavaString => evaluateJava(Right(java), clients)
      case _ => Future.successful(BeTestResult.Error("Unit tests require original Python source or a supported Java Turtle class."))
    }
    evaluation.map { result =>
      val status = result match {
        case BeTestResult.Passed => GradingStatus.Correct()
        case BeTestResult.Failed => GradingStatus.Incorrect()
        case BeTestResult.Error(message) => GradingStatus.TestError(SerializedException(message))
      }
      GradingResult[ProgrammingState, Any](this, state, status)
    }
  }
}
