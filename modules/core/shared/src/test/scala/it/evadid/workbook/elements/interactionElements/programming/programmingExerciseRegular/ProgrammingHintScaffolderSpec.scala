package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage, Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.state.State
import it.evadid.distribution.clients.LocalExecutionClient
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.ScaffoldingFeedback
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future}

class ProgrammingHintScaffolderSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val task = LanguageMapContentId("test/hintTask")

  private class Fixture(reply: String, taskText: Option[String] = Some("Draw a square."), startingProgram: String = "Turtle.forward(10);\n\nTurtle.left(90);") {
    val requests = State(Vector.empty[LLMCommands.FeedbackLlmRequest])
    val language = State[HumanLanguage](German)
    val resolver = new LanguageMapIdResolver(language.observable) {
      override def resolveMap(id: LanguageMapContentId): Future[LanguageMap[HumanLanguage]] =
        Future.successful(LanguageMap.mapBasedLanguageMap(taskText.map(English -> _).toMap))
    }
    val client = new LocalExecutionClient[LLMCommands.FeedbackLlmRequest, String] {
      override def canExecuteCommand(command: ExecutionCommand): Boolean = command.name == LLMCommands.feedbackLlmCommandFactory.name
      override def rawExecution(logger: Logger, request: LLMCommands.FeedbackLlmRequest): Future[String] = {
        requests.update(_ :+ request)
        Future.successful(reply)
      }
    }
    val exercise = ProgrammingExerciseFullJava("hint-exercise", startingProgram = startingProgram, hintTask = Some(task))
    val scaffolder = exercise.scaffoldingElement.get
    def context(clients: List[LocalExecutionClient[?, ?]] = List(client)) =
      TestExecutionContext(clients, languageMapIdResolver = Some(resolver))
    def hint(state: ProgrammingState = exercise.defaultValue) = scaffolder.handleFeedbackGeneration(state, context())
  }

  private def reply(question: String, ids: String*): String =
    ujson.write(ujson.Obj("hints" -> ujson.Arr(ujson.Obj("question" -> question, "evidenceIds" -> ujson.Arr.from(ids)))))

  for ((label, response) <- List(
    "unknown evidence" -> reply("Why?", "source-2"),
    "missing question mark" -> reply("Look at line one", "source-1"),
    "extra fields" -> """{"hints":[],"note":1}""",
    "duplicate evidence" -> reply("Why?", "task", "task"),
    "deep nesting" -> """{"hints":[{"question":[[[["x"]]]],"evidenceIds":[]}]}""",
    "not JSON" -> "Here is a hint?")) {
    test(s"responses with $label are rejected") {
      new Fixture(response).hint().failed.map(error => assert(error.getMessage.contains("expected format"), error.getMessage))
    }
  }

  test("hints need a resolver, an LLM client, the current answer and a usable task") {
    val f = new Fixture(reply("Why?", "task"))
    for {
      noResolver <- f.scaffolder.handleFeedbackGeneration(f.exercise.defaultValue, TestExecutionContext(List(f.client))).failed
      noClient <- f.scaffolder.handleFeedbackGeneration(f.exercise.defaultValue, f.context(Nil)).failed
      changed <- f.hint(ProgrammingStateRaw(Java, "Turtle.forward(99);")).failed
      noTask <- new Fixture(reply("Why?", "task"), taskText = None).hint().failed
      tooLong <- new Fixture(reply("Why?", "task"), startingProgram = Seq.fill(401)("x();").mkString("\n")).hint().failed
    } yield {
      assert(noResolver.getMessage.contains("LanguageMapIdResolver"), noResolver.getMessage)
      assert(noClient.getMessage.contains("No execution client"), noClient.getMessage)
      assert(changed.getMessage.contains("answer changed"), changed.getMessage)
      assert(noTask.getMessage.contains("instructions are missing"), noTask.getMessage)
      assert(tooLong.getMessage.contains("too many lines"), tooLong.getMessage)
    }
  }
}
