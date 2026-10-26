package it.evadid.workbook.elements.interactionElements.gpt

import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.util.logging.BasicLogger

import scala.concurrent.{ExecutionContext, Future}

private[gpt] object TextInputLlmSupport {
  private given ExecutionContext = ExecutionContext.global

  // Capture the language once; all content and the result use that same locale.
  def resolve(ids: List[LanguageMapContentId], resolver: LanguageMapIdResolver): Future[(HumanLanguage, Map[LanguageMapContentId, String])] =
    resolver.languageFuture().flatMap { language =>
      Future.traverse(ids.distinct) { id =>
        resolver.resolveMap(id).map { translations =>
          val text = translations.tryGetInLanguage(language).orElse(translations.tryGetInLanguage(English))
            .filter(value => value != null && value.trim.nonEmpty)
            .getOrElse(throw IllegalArgumentException(s"Missing LLM exercise content: ${id.fullId}"))
          id -> text
        }
      }.map(values => language -> values.toMap)
    }

  def send(request: LLMCommands.FeedbackLlmRequest, clients: List[ExecutionClient]): Future[String] = {
    val factory = LLMCommands.feedbackLlmCommandFactory
    val command = ExecutionCommand(factory.name, upickle.default.writeJs(request))
    clients.find(_.canExecuteCommand(command)) match {
      case Some(client) => factory.sendCommandTo(client, request, Some(BasicLogger())).map(_.result)
      case None => Future.failed(IllegalStateException("No execution client supports LLM feedback."))
    }
  }
}
