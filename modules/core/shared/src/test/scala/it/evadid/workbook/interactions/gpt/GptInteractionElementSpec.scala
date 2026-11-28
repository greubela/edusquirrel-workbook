package it.evadid.workbook.interactions.gpt

import it.evadid.core.util.UtcTime

import it.evadid.workbook.model.InteractionInfo

import it.evadid.workbook.model.InteractionScope

import it.evadid.workbook.sync.{SyncConnection, UsageContext}

import it.evadid.workbook.control.InteractionController

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.state.State
import it.evadid.workbook.interactions.basic.{MessagingInteraction, TextInteraction}
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future}

class GptInteractionElementSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val task = LanguageMapContentId("test/gptTask")
  private val hint = LanguageMapContentId("test/gptHint")
  private val context = TestExecutionContext()

  private def resolver(fail: Boolean = false) = new LanguageMapIdResolver(State[HumanLanguage](English).observable) {
    override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] =
      Future.successful(LanguageMap.universalMap(s"text of ${id.fullId}"))
    override def languageFuture(): Future[HumanLanguage] =
      if fail then Future.failed(IllegalStateException("offline")) else super.languageFuture()
  }

  private val usage = UsageContext("test", "gpt", "student")

  /** Answers of the chat element and its input, as in a workbook that contains both. */
  private def answersOf(element: GptInteractionElement): InteractionController = {
    val answers = InteractionController(State(InteractionInfo.empty(usage)))
    answers.bind(InteractionScope(usage, element.allInteractionsInThisInstance :+ element.underlyingTextInteraction, SyncConnection.offline))
    answers
  }

  private def messages(answers: InteractionController, element: GptInteractionElement) =
    answers.currentValue(element.scaffoldingInteractionOp.get).messengerModel.orderedMessages

  test("the scaffolding chat is initialised once with the task, hints and current answer") {
    val input = TextInteraction("gpt-answer")
    val element = GptInteractionElement("gpt", input, task, Seq(hint), Nil)
    val answers = answersOf(element)
    answers.setStateFromUserInteraction(input, "my answer", UpdateImportance.MAJOR, UtcTime.now().plusSeconds(1))
    assertEquals(element.childrenOfThisElement.map(_.elementId), Seq("gpt_scaffoldingMessenger"))
    element.initScaffoldingIfEmpty(answers, MessengerModel.pFallbackStudent, resolver()).flatMap { first =>
      assert(first, "the student's answer is part of the chat")
      val texts = messages(answers, element).map(_.text)
      assert(texts.contains(s"text of ${task.fullId}"), texts)
      assert(texts.contains(s"@assistant: text of ${hint.fullId}"), texts)
      assert(texts.contains("my answer"), texts)
      element.initScaffoldingIfEmpty(answers, MessengerModel.pFallbackStudent, resolver()).map { second =>
        assertEquals(second, messages(answers, element).last.author.role == it.evadid.core.datastructures.chat.SenderRole.USER,
          "an existing chat is reported by its last author instead of being initialised again")
        assertEquals(messages(answers, element).map(_.text), texts)
      }
    }
  }

  test("unresolved texts are marked and failed resolution leaves the chat empty") {
    val element = GptInteractionElement("gpt-unresolved", TextInteraction("gpt-empty"), task, Seq(hint), Nil)
    val answers = answersOf(element)
    assert(!element.initScaffoldingIfEmpty(answers, MessengerModel.pFallbackStudent, Map.empty, English))
    assert(messages(answers, element).exists(_.text.contains(s"[unresolved: ${task.fullId}]")))
    assert(!element.initScaffoldingIfEmpty(answers, MessengerModel.pFallbackStudent, Map.empty, English))
    val failing = GptInteractionElement("gpt-offline", TextInteraction("gpt-offline-answer"), task, Seq(hint), Nil)
    val failingAnswers = answersOf(failing)
    failing.initScaffoldingIfEmpty(failingAnswers, MessengerModel.pFallbackStudent, resolver(fail = true)).map { initialised =>
      assert(!initialised)
      assert(messages(failingAnswers, failing).isEmpty)
      assertEquals(GptInteractionElement("no-hints", TextInteraction("x"), task, Nil, Nil).scaffoldingInteractionOp, None)
    }
  }
}
