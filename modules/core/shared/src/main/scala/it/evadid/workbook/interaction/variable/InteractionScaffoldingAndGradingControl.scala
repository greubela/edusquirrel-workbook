package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.state.State
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.grading.{GradedState, GradingStatus}
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import it.evadid.workbook.meta.ExecutionContextInformation

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

case class InteractionScaffoldingAndGradingControl[T, GA, SA](interactionVariable: InteractionVariable[T, GA, SA]) {
  private case class GradingRequest(state: T, language: HumanLanguage, forced: Boolean)
  private val gradingRequests = State(Map.empty[GradingRequest, Future[GradedState[T, GA]]])
  private val scaffoldingRequests = State(Map.empty[(T, HumanLanguage), Future[ScaffoldingResult[T, SA]]])

  def getCurrentStateOfGrading(): Option[GradedState[T, GA]] = interactionVariable.synchronized {
    interactionVariable.currentHistory.historyStates.lastStateOption.map { current =>
      val saved = interactionVariable.currentHistory.historyGrading.gradingsMap.getOrElse(current.value, GradedState[T, GA](current.value))
      val status = if gradingRequests.now().keys.exists(_.state == current.value) then GradingStatus.NotFinished() else GradingStatus.NotRequested()
      saved.fillMapForUnavailable(interactionVariable.underlyingInteraction, status)
    }
  }

  def getCurrentStateOfScaffolding(): Option[ScaffoldingResult[T, SA]] = interactionVariable.synchronized {
    interactionVariable.currentHistory.historyStates.lastStateOption.flatMap { current =>
      interactionVariable.currentHistory.historyScaffolding.scaffoldingMap.get(current.value)
    }
  }

  def getOrCalculateGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean = false): Future[GradedState[T, GA]] =
    interactionVariable.currentHistory.historyStates.lastStateOption match {
      case Some(current) => getOrCalculateGrading(contextInformation, forceGrading, current.value)
      case None => Future.failed(SerializedException("Cannot grade an interaction without a current state."))
    }

  def getOrCalculateGrading(contextInformation: ExecutionContextInformation, forceGrading: Boolean, forState: T): Future[GradedState[T, GA]] = interactionVariable.synchronized {
    val key = GradingRequest(forState, contextInformation.getCurrentLanguage, forceGrading)
    gradingRequests.now().getOrElse(key, {
      val saved = interactionVariable.currentHistory.historyGrading.gradingsMap.getOrElse(forState, GradedState[T, GA](forState))
      val graders = interactionVariable.underlyingInteraction.gradingElements.distinct
      val reusable = saved.graderResultMap.filter { (grader, result) =>
        graders.contains(grader) && result.gradedState == forState && grader.canReuseResult(result, contextInformation)
      }
      val pending = graders.filterNot(reusable.contains)
      if pending.isEmpty then Future.successful(GradedState(forState, reusable))
      else {
        val completed = Promise[GradedState[T, GA]]()
        gradingRequests.update(_ + (key -> completed.future))
        GradedState.executeGradingSafe(pending, forState, forceGrading, contextInformation).onComplete { outcome =>
          interactionVariable.synchronized {
            gradingRequests.update(_ - key)
            outcome match {
              case Success(result) =>
                val combined = GradedState(forState, reusable ++ result.graderResultMap)
                Try(interactionVariable.updateHistory(_.addGrading(combined), contextInformation)) match {
                  case Success(_) => completed.trySuccess(combined)
                  case Failure(error) => completed.tryFailure(error)
                }
              case Failure(error) => completed.tryFailure(error)
            }
          }
        }(using contextInformation.futureExecutionContext)
        completed.future
      }
    })
  }

  def getOrCalculateScaffolding(contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[T, SA]] =
    interactionVariable.currentHistory.historyStates.lastStateOption match {
      case Some(current) => getOrCalculateScaffolding(contextInformation, current.value)
      case None => Future.failed(SerializedException("Cannot request scaffolding without a current state."))
    }

  def getOrCalculateScaffolding(contextInformation: ExecutionContextInformation, forState: T): Future[ScaffoldingResult[T, SA]] = interactionVariable.synchronized {
    val key = forState -> contextInformation.getCurrentLanguage
    scaffoldingRequests.now().getOrElse(key, {
      val saved = interactionVariable.currentHistory.historyScaffolding.scaffoldingMap.get(forState).filter { result =>
        result.forState == forState && (result.scaffoldingStatus match {
          case ScaffoldingStatus.ScaffoldingFeedback(feedback) => feedback.tryGetInLanguage(key._2).nonEmpty
          case _ => false
        })
      }
      saved.map(Future.successful).getOrElse {
        interactionVariable.underlyingInteraction.scaffoldingElement match {
          case None => Future.failed(SerializedException("This interaction has no scaffolder."))
          case Some(scaffolder) =>
            val completed = Promise[ScaffoldingResult[T, SA]]()
            scaffoldingRequests.update(_ + (key -> completed.future))
            scaffolder.generateFeedbackSafe(forState, contextInformation).onComplete { outcome =>
              interactionVariable.synchronized {
                scaffoldingRequests.update(_ - key)
                outcome match {
                  case Success(result) =>
                    Try(interactionVariable.updateHistory(_.addScaffolding(result), contextInformation)) match {
                      case Success(_) => completed.trySuccess(result)
                      case Failure(error) => completed.tryFailure(error)
                    }
                  case Failure(error) => completed.tryFailure(error)
                }
              }
            }(using contextInformation.futureExecutionContext)
            completed.future
        }
      }
    })
  }
}
