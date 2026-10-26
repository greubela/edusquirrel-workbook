package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.SerializedException
import it.evadid.vm.test.{BeTestResult, ProgrammingTestRunner}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.programming.{AdditionalProgrammingTestInfo, ProgrammingTestGrader}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import upickle.default.*

import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

case class ProgrammingUnitTestGrader(testId: String, pythonTestCode: String,
    importance: GradingImportance = GradingImportance.REQUIRED_TEST,
    publicDescription: Option[LanguageMapContentId] = None)
    extends ProgrammingTestGrader {
  require(testId != null && testId.nonEmpty && testId == testId.trim, "A programming test needs a nonempty ID.")
  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity()

  override def gradeState(state: ProgrammingState, clients: List[ExecutionClient]): Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] = {
    val evaluation = Future(state.testProgramProjection).flatMap {
      case Right(program) => ProgrammingTestRunner.execute(program, pythonTestCode, clients)
      case Left(message) => Future.successful(BeTestResult.Error(message))
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

  override def gradeState(state: ProgrammingState, clients: List[ExecutionClient], resolver: LanguageMapIdResolver):
      Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] =
    gradeState(state, clients).flatMap { result =>
      val described = publicDescription.filter(_ => importance != GradingImportance.HIDDEN_TEST &&
        Set[GradingStatus](GradingStatus.Correct(), GradingStatus.Incorrect(), GradingStatus.PartiallyCorrect()).contains(result.gradingStatus))
      described.fold(Future.successful(result)) { id =>
        val resolution = Try(Option(resolver).flatMap(value => Option(value.resolveMap(id)))).toOption.flatten
        resolution.fold(Future.successful(result))(_.map { description =>
          result.copy(additionalData = AdditionalProgrammingTestInfo.fromPublicDescription(description))
        }.recover { case NonFatal(_) => result })
      }
    }
}

object ProgrammingUnitTestGrader {
  private[evadid] val LegacyTestId = "legacy-python-assertions"

  private case class Payload(testId: String, pythonTestCode: String,
      importance: GradingImportance = GradingImportance.REQUIRED_TEST,
      publicDescription: Option[LanguageMapContentId] = None) derives ReadWriter

  given ReadWriter[ProgrammingUnitTestGrader] = readwriter[ujson.Value].bimap(
    grader => writeJs(Payload(grader.testId, grader.pythonTestCode, grader.importance, grader.publicDescription)),
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
        require(fields.keySet.subsetOf(Set("testId", "pythonTestCode", "importance", "publicDescription", "serializedType")),
          "Unexpected programming test fields.")
        read[Payload](ujson.Obj.from(fields - "serializedType"))
      }
      ProgrammingUnitTestGrader(payload.testId, payload.pythonTestCode, payload.importance, payload.publicDescription)
    })
}
