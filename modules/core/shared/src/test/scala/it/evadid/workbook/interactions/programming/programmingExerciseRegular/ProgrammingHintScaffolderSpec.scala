package it.evadid.workbook.interactions.programming.programmingExerciseRegular

import it.evadid.core.util.UtcTime

import it.evadid.workbook.support.OfflineController
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage, Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.state.State
import it.evadid.distribution.clients.LocalExecutionClient
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.ScaffoldingFeedback
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.util.logging.Logger
import it.evadid.workbook.interactions.programming.state.{ProgrammingState, ProgrammingStateRaw}
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
    val answers = OfflineController.of(exercise)
    def evidence(state: ProgrammingState) = answers.feedback(exercise).evidenceFor(state)
    def context(clients: List[LocalExecutionClient[?, ?]] = List(client)) =
      TestExecutionContext(clients, languageMapIdResolver = Some(resolver))
    def hint(state: ProgrammingState = exercise.defaultValue) =
      scaffolder.handleFeedbackGeneration(state, evidence(state), context())
  }

  private def reply(question: String, ids: String*): String =
    ujson.write(ujson.Obj("hints" -> ujson.Arr(ujson.Obj("text" -> question, "evidenceIds" -> ujson.Arr.from(ids)))))

  test("an unfinished Java draft sends the parser outcome to the tutor") {
    val fixture = new Fixture(reply("Read the unfinished call.", "source-1"), startingProgram = "class A { static void f() { System.out.println(")
    fixture.hint().map { _ => assert(fixture.requests.now().head.systemPrompt.contains("could not read this draft")) }
  }

  test("Python hints distinguish an unclosed group or missing suite from a complete body") {
    val drafts = List(
      "def gap(a, b):\n    return abs(a - b" -> true,
      "if x:\n    print(1)\nelse:\n    # unfinished" -> true,
      "if x:\nprint(1)" -> true,
      "if x:\n    # the body follows\n    print(1)" -> false,
      "x = (\n    1 + 2\n)\nprint(x)" -> false,
      "match x:\n    case 0:\n        print(0)\n    case _:\n        print(1)" -> false)
    Future.traverse(drafts) { (code, flagged) =>
      val fixture = new Fixture(reply("Look at the current statement.", "source-1"))
      val state = ProgrammingStateRaw(Python, code)
      fixture.answers.setStateFromUserInteraction(fixture.exercise, state, UpdateImportance.MAJOR, UtcTime.now().plusSeconds(1))
      fixture.hint(state).map(_ => assertEquals(fixture.requests.now().head.systemPrompt.contains("could not read this draft"), flagged, code))
    }
  }

  private def hints(items: (String, String)*): String = ujson.write(ujson.Obj("hints" -> ujson.Arr.from(items.map {
    case (text, id) => ujson.Obj("text" -> text, "evidenceIds" -> ujson.Arr(id))
  })))

  test("a reply with several grounded observations displays and saves only its first hint") {
    val raw = hints("Look at the first movement." -> "source-1", "Trace the turn." -> "source-3", "Read the task again." -> "task")
    new Fixture(raw).hint().map { result =>
      assertEquals(result.scaffoldingStatus, ScaffoldingFeedback(LanguageMap.mapBasedLanguageMap(Map(German -> "1. Look at the first movement."))))
      val saved = ujson.read(result.additionalInformation.get.completeHistory.messages.head.text)
      assertEquals(saved("hints").arr.map(_("text").str).toList, List("Look at the first movement."))
    }
  }

  for ((label, response) <- List(
    "too many hints" -> hints(Seq.fill(4)("Trace the turn." -> "task")*),
    "unknown evidence in another hint" -> hints("Trace the turn." -> "task", "Read the line." -> "source-99"),
    "unknown evidence" -> reply("Why?", "source-2"),
    "an internal ID in the hint" -> reply("Look at source-1", "source-1"),
    "extra fields" -> """{"hints":[],"note":1}""",
    "duplicate evidence" -> reply("Why?", "task", "task"),
    "deep nesting" -> """{"hints":[{"text":[[[["x"]]]],"evidenceIds":[]}]}""",
    "not JSON" -> "Here is a hint?")) {
    test(s"responses with $label are rejected") {
      new Fixture(response).hint().failed.map(error => assert(error.getMessage.contains("expected format"), error.getMessage))
    }
  }

  test("hints need a resolver, an LLM client, the current answer and a usable task") {
    val f = new Fixture(reply("Why?", "task"))
    for {
      noResolver <- f.scaffolder.handleFeedbackGeneration(f.exercise.defaultValue, f.evidence(f.exercise.defaultValue), TestExecutionContext(List(f.client))).failed
      noClient <- f.scaffolder.handleFeedbackGeneration(f.exercise.defaultValue, f.evidence(f.exercise.defaultValue), f.context(Nil)).failed
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
