package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.vm.conversion.unittest.ProgramForUnitTestExecution

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.SerializedException
import it.evadid.vm.test.{BeTestResult, ProgrammingTestRunner}
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState}
import it.evadid.workbook.elements.interactionElements.programming.{AdditionalProgrammingTestInfo, ProgrammingTestGrader}
import it.evadid.workbook.meta.ExecutionContextInformation
import upickle.default.*

import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

case class ProgrammingUnitTestGrader(testId: String, pythonTestCode: String,
                                     importance: GradingImportance = GradingImportance.REQUIRED_TEST,
                                     publicDescription: Option[LanguageMapContentId] = None,
                                     override val publishProgramObservation: Boolean = false,
                                     publishInvocationArguments: Boolean = false,
                                     publishCommandComparison: Boolean = false)
  extends ProgrammingTestGrader {

  require(testId != null && testId.nonEmpty && testId == testId.trim, "A programming test needs a nonempty ID.")
  require(!publishCommandComparison || publishInvocationArguments, "A public command comparison requires its invocation arguments.")

  override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
  override val gradingEntity = FeedbackEntity.TestEntity()

  override protected def handleGrading(state: ProgrammingState, contextInformation: ExecutionContextInformation): Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] = {

    if (contextInformation.languageMapIdResolver.isEmpty) {
      gradeState(state, contextInformation.availableExecutionClients)
    } else {
      gradeState(state, contextInformation.availableExecutionClients, contextInformation.languageMapIdResolver.get)
    }

  }

  def gradeState(state: ProgrammingState, clients: List[ExecutionClient]): Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] = {
    val evaluation = Future(ProgramForUnitTestExecution.fromState(state)).flatMap {
      case Right(program) => ProgrammingTestRunner.execute(program, pythonTestCode, clients,
        observeProgram = publishProgramObservation && importance != GradingImportance.HIDDEN_TEST,
        observeInvocation = publishInvocationArguments && importance != GradingImportance.HIDDEN_TEST,
        observeComparison = publishCommandComparison && importance != GradingImportance.HIDDEN_TEST)
      case Left(error) => Future.successful(ProgrammingTestRunner.Result(BeTestResult.Error(error.message)))
    }
    evaluation.map { result =>
      val status = result.result match {
        case BeTestResult.Passed => GradingStatus.Correct()
        case BeTestResult.Failed => GradingStatus.Incorrect()
        case BeTestResult.Error(message) => GradingStatus.TestError(SerializedException(message))
      }
      val invocation = result.testInvocation.filter(_ => publishInvocationArguments && importance != GradingImportance.HIDDEN_TEST)
      val info = Option.when(result.programObservation.nonEmpty || invocation.nonEmpty)(
        AdditionalProgrammingTestInfo(LanguageMap.empty[HumanLanguage], result.programObservation, invocation))
      GradingResult[ProgrammingState, AdditionalProgrammingTestInfo](this, state, status, additionalData = info)
    }
  }

  def gradeState(state: ProgrammingState, clients: List[ExecutionClient], resolver: LanguageMapIdResolver):
  Future[GradingResult[ProgrammingState, AdditionalProgrammingTestInfo]] =
    gradeState(state, clients).flatMap { result =>
      val described = publicDescription.filter(_ => importance != GradingImportance.HIDDEN_TEST &&
        Set[GradingStatus](GradingStatus.Correct(), GradingStatus.Incorrect(), GradingStatus.PartiallyCorrect()).contains(result.gradingStatus))
      described.fold(Future.successful(result)) { id =>
        val resolution = Try(Option(resolver).flatMap(value => Option(value.resolveMap(id)))).toOption.flatten
        resolution.fold(Future.successful(result))(_.map { description =>
          val info = AdditionalProgrammingTestInfo.fromPublicDescription(description).map { described =>
            described.copy(programObservation = result.additionalData.flatMap(_.programObservation),
              testInvocation = result.additionalData.flatMap(_.testInvocation))
          }.orElse(result.additionalData)
          result.copy(additionalData = info)
        }.recover { case NonFatal(_) => result })
      }
    }

}

object ProgrammingUnitTestGrader {
  private[evadid] val SampleTestId = "python-assertions"

  private case class Payload(testId: String, pythonTestCode: String,
                             importance: GradingImportance = GradingImportance.REQUIRED_TEST,
                             publicDescription: Option[LanguageMapContentId] = None,
                             publishProgramObservation: Boolean = false,
                             publishInvocationArguments: Boolean = false,
                             publishCommandComparison: Boolean = false) derives ReadWriter

  given ReadWriter[ProgrammingUnitTestGrader] = readwriter[ujson.Value].bimap(
    grader => writeJs(Payload(grader.testId, grader.pythonTestCode, grader.importance, grader.publicDescription,
      grader.publishProgramObservation, grader.publishInvocationArguments, grader.publishCommandComparison)),
    value => {
      val fields = value.obj.toMap
      require(fields.get("serializedType").forall(_ == ujson.Str("ProgrammingUnitTestGrader")),
        "Unexpected programming grader type.")
      val payload = {
        require(fields.keySet.subsetOf(Set("testId", "pythonTestCode", "importance", "publicDescription",
          "publishProgramObservation", "publishInvocationArguments", "publishCommandComparison", "serializedType")),
          "Unexpected programming test fields.")
        require(fields.get("publishProgramObservation").forall(_.boolOpt.isDefined),
          "Publishing a program observation requires a boolean setting.")
        require(fields.get("publishInvocationArguments").forall(_.boolOpt.isDefined),
          "Publishing invocation arguments requires a boolean setting.")
        require(fields.get("publishCommandComparison").forall(_.boolOpt.isDefined),
          "Publishing command comparisons requires a boolean setting.")
        read[Payload](ujson.Obj.from(fields - "serializedType"))
      }
      ProgrammingUnitTestGrader(payload.testId, payload.pythonTestCode, payload.importance, payload.publicDescription,
        payload.publishProgramObservation, payload.publishInvocationArguments, payload.publishCommandComparison)
    })
}
