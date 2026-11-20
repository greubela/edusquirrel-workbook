package it.evadid.workbook.elements.interactionElements.gpt

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.state.State
import it.evadid.workbook.elements.interactionElements.basic.{MessagingInteraction, TextInteraction}
import it.evadid.workbook.interaction.sync.UpdateImportance
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

  private def messages(element: GptInteractionElement) =
    element.scaffoldingInteractionOp.get.interactionVariable.currentValue.messengerModel.orderedMessages

  test("the scaffolding chat is initialised once with the task, hints and current answer") {
    val input = TextInteraction("gpt-answer")
    input.interactionVariable.setStateFromUserInteraction("my answer", UpdateImportance.MAJOR, java.time.LocalDateTime.now().plusSeconds(1), context)
    val element = GptInteractionElement("gpt", input, task, Seq(hint), Nil)
    assertEquals(element.childrenOfThisElement.map(_.elementId), Seq("gpt_scaffoldingMessenger"))
    element.initScaffoldingIfEmpty(context, MessengerModel.pFallbackStudent, resolver()).flatMap { first =>
      assert(first, "the student's answer is part of the chat")
      val texts = messages(element).map(_.text)
      assert(texts.contains(s"text of ${task.fullId}"), texts)
      assert(texts.contains(s"@assistant: text of ${hint.fullId}"), texts)
      assert(texts.contains("my answer"), texts)
      element.initScaffoldingIfEmpty(context, MessengerModel.pFallbackStudent, resolver()).map { second =>
        assertEquals(second, messages(element).last.author.role == it.evadid.core.datastructures.chat.SenderRole.USER,
          "an existing chat is reported by its last author instead of being initialised again")
        assertEquals(messages(element).map(_.text), texts)
      }
    }
  }

  test("unresolved texts are marked and failed resolution leaves the chat empty") {
    val element = GptInteractionElement("gpt-unresolved", TextInteraction("gpt-empty"), task, Seq(hint), Nil)
    assert(!element.initScaffoldingIfEmpty(context, MessengerModel.pFallbackStudent, Map.empty, English))
    assert(messages(element).exists(_.text.contains(s"[unresolved: ${task.fullId}]")))
    assert(!element.initScaffoldingIfEmpty(context, MessengerModel.pFallbackStudent, Map.empty, English))
    val failing = GptInteractionElement("gpt-offline", TextInteraction("gpt-offline-answer"), task, Seq(hint), Nil)
    failing.initScaffoldingIfEmpty(context, MessengerModel.pFallbackStudent, resolver(fail = true)).map { initialised =>
      assert(!initialised)
      assert(messages(failing).isEmpty)
      assertEquals(GptInteractionElement("no-hints", TextInteraction("x"), task, Nil, Nil).scaffoldingInteractionOp, None)
    }
  }
}
