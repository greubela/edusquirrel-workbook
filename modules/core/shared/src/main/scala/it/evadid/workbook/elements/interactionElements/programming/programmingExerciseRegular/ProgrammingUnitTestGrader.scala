package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.SerializedException
import it.evadid.vm.test.{BeTestResult, ProgrammingTestRunner}
import it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport
import it.evadid.vm.parsing.java.turtle.JavaTurtleVmPrograms
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.programming.{AdditionalProgrammingTestInfo, ProgrammingTestGrader}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.{ProgrammingStateBeExpression, ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.JavaTurtleEditingBridge
import upickle.default.*

import scala.concurrent.Future

case class ProgrammingUnitTestGrader(testId: String, pythonTestCode: String,
    importance: GradingImportance = GradingImportance.REQUIRED_TEST)
    extends ProgrammingTestGrader {
  require(testId != null && testId.nonEmpty && testId == testId.trim, "A programming test needs a nonempty ID.")
  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity()

  private def evaluateJava(source: => Either[String, ProgrammingStateJavaString], clients: List[ExecutionClient]): Future[BeTestResult] =
    Future(source.flatMap(_.toJavaVmProgram.left.map(_.message))).flatMap {
      case Right(program) => evaluateProgram(program, clients)
      case Left(_) => Future.successful(BeTestResult.Error("This source is not supported by the checked Java Turtle runner."))
    }

  private def evaluateProgram(program: JavaTurtleVmPrograms.Program, clients: List[ExecutionClient]): Future[BeTestResult] = {
    val exported = JavaTurtlePythonExport.render(program)
    ProgrammingTestRunner.execute(exported.source, pythonTestCode,
      Some(exported.methods.view.mapValues(_.index).toMap), clients)
  }

  override def gradeState(state: ProgrammingState, clients: List[ExecutionClient]): Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] = {
    val evaluation = state match {
      case ProgrammingStatePythonString(source) => Future(JavaTurtleEditingBridge.hasPythonMetadata(source)).flatMap {
        case true => evaluateJava(JavaTurtleEditingBridge.fromPython(source), clients)
        case false => ProgrammingTestRunner.execute(source, pythonTestCode, None, clients)
      }
      case snap: ProgrammingStateSnapXml => Future(JavaTurtleEditingBridge.hasSnapMetadata(snap.snapXml)).flatMap {
        case true => evaluateJava(JavaTurtleEditingBridge.fromSnap(snap), clients)
        case false => Future.successful(BeTestResult.Error("Unit tests cannot infer a Java program from an untyped Snap project."))
      }
      case ProgrammingStateBeExpression(root: JavaTurtleVmPrograms.Root) =>
        evaluateProgram(JavaTurtleVmPrograms.fromRoot(root), clients)
      case java: ProgrammingStateJavaString => evaluateJava(Right(java), clients)
      case _ => Future.successful(BeTestResult.Error("Unit tests require original Python source or a supported Java Turtle class."))
    }
    evaluation.map { result =>
      val status = result match {
        case BeTestResult.Passed => GradingStatus.Correct()
        case BeTestResult.Failed => GradingStatus.Incorrect()
        case BeTestResult.Error(message) => GradingStatus.TestError(SerializedException(message))
      }
      GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](this, state, status)
    }
  }
}

object ProgrammingUnitTestGrader {
  private[evadid] val LegacyTestId = "legacy-python-assertions"

  private case class Payload(testId: String, pythonTestCode: String,
      importance: GradingImportance = GradingImportance.REQUIRED_TEST) derives ReadWriter

  given ReadWriter[ProgrammingUnitTestGrader] = readwriter[ujson.Value].bimap(
    grader => writeJs(Payload(grader.testId, grader.pythonTestCode, grader.importance)),
    value => {
      val fields = value.obj.toMap
      require(fields.get("serializedType").forall(_ == ujson.Str("ProgrammingUnitTestGrader")),
        "Unexpected programming grader type.")
      val payload = if fields.contains("testSuite") then {
        require(fields.keySet.subsetOf(Set("testSuite", "importance", "serializedType")),
          "Ambiguous legacy programming test.")
        val test = fields("testSuite").obj
        require(test.keySet.toSet == Set("$type", "pythonTestCode") && test("$type") == ujson.Str("SampleBeTest"),
          "The legacy grader must contain one SampleBeTest definition.")
        Payload(LegacyTestId, test("pythonTestCode").str,
          fields.get("importance").map(read[GradingImportance](_)).getOrElse(GradingImportance.REQUIRED_TEST))
      } else {
        require(fields.keySet.subsetOf(Set("testId", "pythonTestCode", "importance", "serializedType")),
          "Unexpected programming test fields.")
        read[Payload](ujson.Obj.from(fields - "serializedType"))
      }
      ProgrammingUnitTestGrader(payload.testId, payload.pythonTestCode, payload.importance)
    })
}
