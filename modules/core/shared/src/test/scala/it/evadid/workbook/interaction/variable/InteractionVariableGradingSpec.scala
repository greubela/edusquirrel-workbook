package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage, Java, Python}
import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.state.State
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.SerializedException
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.util.logging.BasicLogger
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionBoundComponent, WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.abstractions.scaffolding.{ProgrammingHintScaffolder, ScaffoldingResult, WorkbookInteractionScaffolder}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.{Slideshow, SlideshowGrader}
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import munit.FunSuite
import upickle.default.*

import java.time.LocalDateTime
import scala.collection.immutable.ListSet
import scala.concurrent.{ExecutionContext, Future, Promise}

class InteractionVariableGradingSpec extends FunSuite {
  private class RecordingGrader extends WorkbookInteractionGrader[String, String] {
    private val recorded = State(List.empty[(String, Boolean)])
    def requests: List[(String, Boolean)] = recorded.now()
    override val importance = GradingImportance.REQUIRED_TEST
    override val runningBehavior = GraderRunningBehavior.RUN_ALWAYS
    override val gradingEntity = FeedbackEntity.TestEntity()
    override def gradeState(state: String): Future[GradingResult[String, String]] =
      Future.successful(GradingResult(this, state, GradingStatus.Correct()))
    override def gradeStateSafe(state: String, forced: Boolean): Future[GradingResult[String, String]] = {
      recorded.update(_ :+ (state -> forced))
      gradeState(state)
    }
  }

  private class RecordingScaffolder extends WorkbookInteractionScaffolder[String, String] {
    private val recorded = State(List.empty[String])
    def requests: List[String] = recorded.now()
    override def generateFeedback(state: String): Future[ScaffoldingResult[String, String]] = {
      recorded.update(_ :+ state)
      Future.successful(ScaffoldingResult(state, LanguageMap.universalMap("Hint"), FeedbackEntity.TestEntity(), None))
    }
  }

  private case class BoundDefinition(override val elementId: String, override val defaultValue: String)
      extends WorkbookInteractionElementFull[String, String, String] derives ReadWriter {
    override lazy val childrenOfThisElement = List.empty[WorkbookElement]
    override val associatedFactory = TextInteraction.factory
    override val gradingElements: List[RecordingGrader] = List(new RecordingGrader)
    override val scaffoldingElement: Option[RecordingScaffolder] = Some(new RecordingScaffolder)
  }

  private def interaction(graders: List[WorkbookInteractionGrader[String, String]],
      scaffolder: Option[WorkbookInteractionScaffolder[String, String]] = None,
      initial: String = ""): WorkbookInteractionElementFull[String, String, String] =
    new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "grading-test"
      override val defaultValue = initial
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val gradingElements = graders
      override val scaffoldingElement = scaffolder
    }

  private def variable(grader: RecordingGrader): InteractionVariable[String, String, String] =
    interaction(List(grader)).interactionVariable

  private def hintReply(question: String = "Welche Bedingung prüfst du zuerst?", ids: List[String] = List("task")): String =
    ujson.Obj("hints" -> ujson.Arr(ujson.Obj("question" -> question, "evidenceIds" -> ujson.Arr.from(ids)))).render()

  private case class HintCheck(name: String, override val importance: GradingImportance,
      override val gradingEntity: FeedbackEntity = FeedbackEntity.TestEntity())
      extends WorkbookInteractionGrader[ProgrammingState, String] {
    val runs = State(0)
    override val runningBehavior = GraderRunningBehavior.RUN_ALWAYS
    override def gradeState(state: ProgrammingState): Future[GradingResult[ProgrammingState, String]] = {
      runs.update(_ + 1)
      Future.failed(IllegalStateException("Hints must not run graders."))
    }
  }

  private class HintFixture(val source: ProgrammingState = ProgrammingStateJavaString("public class Drawing {"),
      val checks: List[HintCheck] = Nil,
      response: LLMCommands.FeedbackLlmRequest => Future[String] = _ => Future.successful(hintReply()),
      task: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(German -> "Zeichne eine Linie.", English -> "Draw a line."))) {
    val requests = State(Vector.empty[LLMCommands.FeedbackLlmRequest])
    val lookups = State(0)
    val started = Promise[Unit]()
    val language = State[HumanLanguage](German)
    def client(answer: LLMCommands.FeedbackLlmRequest => Future[String]): ExecutionClient =
      LLMCommands.feedbackLlmCommandFactory.toLocalExecutionClient { (request, _) =>
        requests.update(_ :+ request)
        started.trySuccess(())
        answer(request)
      }
    val executor = State(client(response))
    val scaffolder = new ProgrammingHintScaffolder[String](task, () => language.now(), () => {
      lookups.update(_ + 1)
      executor.now()
    })
    val element = new WorkbookInteractionElementFull[ProgrammingState, String, String] {
      override val elementId = "programming-hint-test"
      override val defaultValue = source
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TurtleRecreateShapeInteraction.factory
      override val gradingElements = checks
      override val scaffoldingElement = Some(scaffolder)
    }
    val variable = element.interactionVariable
    def context: ujson.Value = ujson.read(requests.now().last.prompt)
  }

  private given ExecutionContext = ExecutionContext.global

  test("programming hints are manual and preserve unfinished Java and Python source exactly") {
    val java = ProgrammingStateJavaString("\r\n// Grüße 🐢\r\npublic class Drawing {\t")
    val python = ProgrammingStatePythonString("\r\n# Grüße 🐢\r\n\tdef draw(\r\n ")
    Future.sequence(List(java -> Java, python -> Python).map { (source, language) =>
      val f = new HintFixture(source)
      val before = f.variable.history
      assertEquals(f.requests.now(), Vector.empty)
      assertEquals(f.lookups.now(), 0)
      f.scaffolder.generateFeedback(source).map { result =>
        assertEquals(f.context("source")("language").str, language.name)
        assertEquals(f.context("source")("code").str, if language == Java then java.code else python.code)
        assertEquals(f.context("source")("lineIds").arr.toVector.map(_.str), Vector("source-2", "source-3"))
        assertEquals(f.context("checks").arr.toVector, Vector.empty)
        assertEquals(result.forState, source)
        assertEquals(result.additionalInformation, None)
        assertEquals(f.variable.history, before)
        assertEquals(f.lookups.now(), 1)
      }
    }).map(_ => ())
  }

  test("programming hints retain checked Java source and number accepted questions in the requested language") {
    val source = ProgrammingStateJavaString("class Drawing { public static void main(String[] args) { Turtle.forward(12); } }")
    val questions = Vector("Wo beginnt die Ausführung?", "Welche Strecke zeichnet dein Aufruf?")
    val raw = ujson.Obj("hints" -> ujson.Arr.from(questions.map(question =>
      ujson.Obj("question" -> s"  $question  ", "evidenceIds" -> ujson.Arr("task", "source-1"))))).render()
    val f = new HintFixture(source.toBeExpressionState, response = _ => Future.successful(" \n" + raw + "\n "))
    f.scaffolder.generateFeedback(f.source).map { result =>
      assertEquals(f.context("source")("language").str, Java.name)
      assertEquals(f.context("source")("code").str, source.code)
      assertEquals(result.scaffoldingResult.getInLanguage(German), questions.zipWithIndex.map((q, i) => s"${i + 1}. $q").mkString("\n"))
      assertEquals(result.scaffoldingResult.tryGetInLanguage(English), None)
      val conversation = result.scaffoldingEntity match {
        case FeedbackEntity.LlmEntity(chat) => chat
        case other => fail(s"Expected the accepted model conversation, got $other")
      }
      assertEquals(conversation.messages.map(_.author), List(MessengerModel.pAgent))
      assertEquals(ujson.read(conversation.messages.last.text)("hints").arr.map(_("question").str).toVector, questions)
      val retained = conversation.messages.map(_.text).mkString("\n")
      assert(!retained.contains(source.code))
      assert(!retained.contains(f.requests.now().last.prompt))
      assert(!retained.contains(f.requests.now().last.systemPrompt))
      List(read[ScaffoldingResult[ProgrammingState, String]](write(result)),
        readBinary[ScaffoldingResult[ProgrammingState, String]](writeBinary(result))).foreach { restored =>
        assertEquals(restored.forState.toJava, source)
        assertEquals(restored.scaffoldingResult, result.scaffoldingResult)
        assertEquals(restored.scaffoldingEntity, result.scaffoldingEntity)
        assertEquals(restored.additionalInformation, None)
      }
    }
  }

  test("every explicit programming hint resolves the current provider and feedback language without caching") {
    val f = new HintFixture
    val before = f.variable.history
    f.scaffolder.generateFeedback(f.source).flatMap { first =>
      assertEquals(first.scaffoldingResult.availableLanguages, Set[HumanLanguage](German))
      f.language.set(English)
      f.executor.set(f.client(_ => Future.successful(hintReply("Which condition will you inspect first?"))))
      f.scaffolder.generateFeedback(f.source).map { second =>
        assertEquals(f.requests.now().size, 2)
        assertEquals(f.lookups.now(), 2)
        assertEquals(f.context("feedbackLanguage").str, English.name)
        assertEquals(f.context("task")("text").str, "Draw a line.")
        assertEquals(second.scaffoldingResult.getInLanguage(English), "1. Which condition will you inspect first?")
        assertEquals(f.variable.history, before)
      }
    }
  }

  test("a hint selects its client before returning control and keeps that request's source and locale") {
    val reply = Promise[String]()
    val f = new HintFixture(response = _ => reply.future)
    val first = f.scaffolder.generateFeedback(f.source)
    assertEquals(f.lookups.now(), 1)
    assertEquals(f.requests.now().size, 1)
    assert(!first.isCompleted)
    f.language.set(English)
    f.executor.set(f.client(_ => Future.successful(hintReply("Which condition will you inspect first?"))))
    val restored = ProgrammingStatePythonString("# Different answer\r\n\tdef draw(")
    f.variable.updateHistory(_.withAddedEvent(InteractionVariableState[ProgrammingState](
      restored, MAJOR, LocalDateTime.now().plusSeconds(1))))
    val second = f.scaffolder.generateFeedback(restored)
    assertEquals(f.lookups.now(), 2)
    assertEquals(f.requests.now().map(request => ujson.read(request.prompt)("feedbackLanguage").str), Vector(German.name, English.name))
    val beforeReply = f.variable.history
    reply.success(hintReply())
    first.zip(second).map { (original, current) =>
      assertEquals(original.forState, f.source)
      assertEquals(original.scaffoldingResult.availableLanguages, Set[HumanLanguage](German))
      assertEquals(current.forState, restored)
      assertEquals(current.scaffoldingResult.availableLanguages, Set[HumanLanguage](English))
      assertEquals(current.scaffoldingResult.getInLanguage(English), "1. Which condition will you inspect first?")
      assertEquals(f.context("source")("code").str, restored.code)
      assertEquals(f.variable.history, beforeReply)
    }
  }

  test("programming hint context exposes only matching public deterministic check statuses") {
    val required = HintCheck("PRIVATE_REQUIRED_NAME", GradingImportance.REQUIRED_TEST)
    val optional = HintCheck("PRIVATE_OPTIONAL_NAME", GradingImportance.OPTIONAL_TEST)
    val hidden = HintCheck("PRIVATE_REFERENCE_SOLUTION", GradingImportance.HIDDEN_TEST)
    val missing = HintCheck("PRIVATE_MISSING_NAME", GradingImportance.REQUIRED_TEST)
    val notRun = HintCheck("PRIVATE_NOT_RUN_NAME", GradingImportance.REQUIRED_TEST)
    val error = HintCheck("PRIVATE_ERROR_NAME", GradingImportance.REQUIRED_TEST)
    val partial = HintCheck("PRIVATE_PARTIAL_NAME", GradingImportance.REQUIRED_TEST)
    val model = HintCheck("PRIVATE_MODEL_NAME", GradingImportance.REQUIRED_TEST, FeedbackEntity.LlmEntity(MessengerModel.empty))
    val removed = HintCheck("PRIVATE_REMOVED_NAME", GradingImportance.REQUIRED_TEST)
    val f = new HintFixture(checks = List(required, optional, hidden, missing, notRun, error, partial, model))
    val other = ProgrammingStateJavaString("other source")
    val time = LocalDateTime.of(2026, 10, 17, 12, 0)
    def grade(check: HintCheck, status: GradingStatus, state: ProgrammingState = f.source, seconds: Int = 0) =
      GradingResult[ProgrammingState, String](check, state, status,
        Some(LanguageMap.universalMap("PRIVATE_FEEDBACK_WITH_ANSWER")), Some("PRIVATE_EXTRA_RESULT"), time.plusSeconds(seconds))
    val results = Set(grade(required, GradingStatus.Correct()), grade(required.copy(), GradingStatus.Incorrect(), seconds = 1),
      grade(optional, GradingStatus.Incorrect()), grade(hidden, GradingStatus.Incorrect()),
      grade(missing, GradingStatus.Incorrect(), other), grade(notRun, GradingStatus.NotExecuted()),
      grade(error, GradingStatus.TestError(SerializedException("PRIVATE_EXCEPTION_DETAILS"))),
      grade(partial, GradingStatus.PartiallyCorrect()), grade(model, GradingStatus.Incorrect()), grade(removed, GradingStatus.Incorrect()))
    f.variable.updateHistory(_.copy(gradings = Map(f.source -> GradedState(f.source, results))))
    val before = f.variable.history
    f.scaffolder.generateFeedback(f.source).map { _ =>
      val checks = f.context("checks").arr.toVector.map(value => (value("id").str, value("required").bool, value("status").str))
      assertEquals(checks, Vector(("check-1", true, "incorrect"), ("check-2", false, "incorrect"),
        ("check-3", true, "unavailable"), ("check-4", true, "not-run"), ("check-5", true, "error"),
        ("check-6", true, "partially-correct")))
      assert(!f.requests.now().last.prompt.contains("PRIVATE_"))
      assert(f.checks.forall(_.runs.now() == 0))
      assertEquals(f.variable.history, before)
    }
  }

  test("wrong grading snapshots and conflicting latest results remain unavailable") {
    val check = HintCheck("public check", GradingImportance.REQUIRED_TEST)
    val f = new HintFixture(checks = List(check))
    val time = LocalDateTime.of(2026, 10, 17, 12, 0)
    val first = GradingResult[ProgrammingState, String](check, f.source, GradingStatus.Correct(), gradingTime = time)
    val conflicting = first.copy(gradingStatus = GradingStatus.Incorrect())
    val other = ProgrammingStatePythonString("other")
    f.variable.updateHistory(_.copy(gradings = Map(f.source -> GradedState(other, Set(first)))))
    f.scaffolder.generateFeedback(f.source).flatMap { _ =>
      assertEquals(f.context("checks")(0)("status").str, "unavailable")
      f.variable.updateHistory(_.copy(gradings = Map(f.source -> GradedState(f.source, Set(first, conflicting)))))
      f.scaffolder.generateFeedback(f.source).flatMap { _ =>
        assertEquals(f.context("checks")(0)("status").str, "unavailable")
        val newest = conflicting.copy(gradingStatus = GradingStatus.PartiallyCorrect(), gradingTime = time.plusSeconds(1))
        f.variable.updateHistory(_.copy(gradings = Map(f.source -> GradedState(f.source, Set(first, conflicting, newest)))))
        f.scaffolder.generateFeedback(f.source).map(_ => assertEquals(f.context("checks")(0)("status").str, "partially-correct"))
      }
    }
  }

  test("unsupported or oversized hint inputs fail before resolving an execution client") {
    val required = GradingImportance.REQUIRED_TEST
    val cases = List(new HintFixture(ProgrammingStateSnapXml.empty),
      new HintFixture(ProgrammingStateBeExpression(it.evadid.vm.code.abstractions.BeExpression.pass)),
      new HintFixture(ProgrammingStateJavaString("x" * 24001)), new HintFixture(ProgrammingStatePythonString("\n" * 400)),
      new HintFixture(task = LanguageMap.empty[HumanLanguage]),
      new HintFixture(task = LanguageMap.universalMap("x" * 8001)),
      new HintFixture(checks = (1 to 33).map(index => HintCheck(index.toString, required)).toList))
    Future.sequence(cases.map(f => f.scaffolder.generateFeedback(f.source).failed.map { _ =>
      assertEquals(f.lookups.now(), 0)
      assertEquals(f.requests.now(), Vector.empty)
    })).map(_ => ())
  }

  test("bounded source and question limits accept their exact boundaries and quoted JSON strings") {
    val quoted = "Wie prüfst du \"x[y]\" und den Pfad C:\\tmp?"
    val cases = List(ProgrammingStateJavaString("x" * 24000) -> ("q" * 319 + "?"),
      ProgrammingStatePythonString(List.fill(400)("x").mkString("\n")) -> quoted)
    Future.sequence(cases.map { (source, question) =>
      val f = new HintFixture(source, response = _ => Future.successful(hintReply(question)))
      f.scaffolder.generateFeedback(source).map(result =>
        assertEquals(result.scaffoldingResult.getInLanguage(German), "1. " + question))
    }).map(_ => ())
  }

  test("three distinct questions can cite three known evidence IDs") {
    val questions = (1 to 3).map(index => s"Welche Beobachtung hilft dir bei Schritt $index?").toVector
    val ids = Vector("task", "source-1", "check-1")
    val raw = ujson.Obj("hints" -> ujson.Arr.from(questions.map(question =>
      ujson.Obj("question" -> question, "evidenceIds" -> ujson.Arr.from(ids))))).render()
    val f = new HintFixture(checks = List(HintCheck("visible", GradingImportance.REQUIRED_TEST)),
      response = _ => Future.successful(raw))
    f.scaffolder.generateFeedback(f.source).map(result =>
      assertEquals(result.scaffoldingResult.getInLanguage(German), questions.zipWithIndex.map((q, i) => s"${i + 1}. $q").mkString("\n")))
  }

  test("malformed structured hints are rejected once without changing the interaction history") {
    def entry(question: ujson.Value = ujson.Str("Welche Bedingung prüfst du?"), ids: ujson.Value = ujson.Arr("task")) =
      ujson.Obj("question" -> question, "evidenceIds" -> ids)
    def document(entries: ujson.Value*) = ujson.Obj("hints" -> ujson.Arr(entries*)).render()
    val invalid = List("not JSON", "null", "[]", document(), document(ujson.Null),
      document((1 to 4).map(index => entry(ujson.Str(s"Frage $index?")))*), document(entry(), entry()),
      document(entry(ujson.Num(2))), document(entry(ujson.Str("A statement."))), document(entry(ujson.Str("x" * 320 + "?"))),
      document(entry(ujson.Str("Wie prüfst du `x`?"))), document(entry(ujson.Str("Eine\nzweite Zeile?"))),
      document(entry(ujson.Str("Ein\tTab?"))), document(entry(ujson.Str("Eine\u2028weitere Zeile?"))).replace("\u2028", "\\u2028"),
      document(entry(ujson.Str("Eine\u2029weitere Zeile?"))).replace("\u2029", "\\u2029"), document(entry(ids = ujson.Arr())),
      document(entry(ids = ujson.Arr("task", "task"))), document(entry(ids = ujson.Arr("unknown"))),
      document(entry(ids = ujson.Arr("source-2"))), document(entry(ids = ujson.Arr(1))), document(entry(ids = ujson.Str("task"))),
      document(entry(ids = ujson.Arr("task", "source-1", "check-1", "check-2"))),
      document(entry(ids = ujson.Arr(ujson.Arr("task")))),
      document(ujson.Obj("question" -> "Welche Bedingung?", "evidenceIds" -> ujson.Arr("task"), "solution" -> "hidden")),
      ujson.Obj("hints" -> ujson.Arr(entry()), "grade" -> "correct").render(), " " * 4097 + hintReply())
    Future.sequence(invalid.map { raw =>
      val f = new HintFixture(checks = List(HintCheck("first", GradingImportance.REQUIRED_TEST),
        HintCheck("second", GradingImportance.OPTIONAL_TEST)), response = _ => Future.successful(raw))
      val before = f.variable.history
      f.scaffolder.generateFeedback(f.source).failed.map { error =>
        assert(error.isInstanceOf[IllegalArgumentException], clue = raw)
        assertEquals(f.requests.now().size, 1)
        assertEquals(f.lookups.now(), 1)
        assertEquals(f.variable.history, before)
      }
    }).map(_ => ())
  }

  test("hint transport failures do not retry or publish a replacement answer") {
    val f = new HintFixture(response = _ => Future.failed(IllegalStateException("offline test endpoint")))
    val before = f.variable.history
    f.scaffolder.generateFeedback(f.source).failed.map { error =>
      assert(error.getMessage.contains("offline test endpoint"))
      assertEquals(f.lookups.now(), 1)
      assertEquals(f.requests.now().size, 1)
      assertEquals(f.variable.history, before)
    }
  }

  test("a throwing hint client provider fails its future without sending or retrying") {
    val f = new HintFixture
    val calls = State(0)
    val failure = IllegalStateException("client provider unavailable")
    val scaffolder = new ProgrammingHintScaffolder[String](LanguageMap.universalMap("Public task"), () => German, () => {
      calls.update(_ + 1)
      throw failure
    })
    InteractionBoundComponent.bindAll(f.element, List(scaffolder))
    val before = f.variable.history
    scaffolder.generateFeedback(f.source).failed.map { error =>
      assert(error eq failure)
      assertEquals(calls.now(), 1)
      assertEquals(f.requests.now(), Vector.empty)
      assertEquals(f.lookups.now(), 0)
      assertEquals(f.variable.history, before)
    }
  }

  test("late programming hints retain the original source and locale without overwriting a new answer") {
    val response = Promise[String]()
    val f = new HintFixture(response = _ => response.future)
    val pending = f.scaffolder.generateFeedback(f.source)
    f.started.future.flatMap { _ =>
      val restored = ProgrammingStatePythonString("def different(\n\t")
      val next = InteractionVariableState[ProgrammingState](restored, MAJOR, LocalDateTime.now().plusSeconds(1))
      f.variable.updateHistory(_.withAddedEvent(next))
      f.language.set(English)
      val beforeReply = f.variable.history
      response.success(hintReply())
      pending.map { result =>
        assertEquals(result.forState, f.source)
        assertEquals(result.scaffoldingResult.availableLanguages, Set[HumanLanguage](German))
        assertEquals(f.variable.currentValue, restored)
        assertEquals(f.variable.history, beforeReply)
        assertEquals(f.requests.now().size, 1)
      }
    }
  }

  test("lazy interaction creation binds its grader and scaffolder without executing them") {
    val grader = new RecordingGrader
    val scaffolder = new RecordingScaffolder
    val element = interaction(List(grader), Some(scaffolder), "initialized default")
    intercept[IllegalStateException](grader.interactionVariable)
    intercept[IllegalStateException](scaffolder.interaction)
    val value = element.interactionVariable
    assert(grader.interactionVariable eq value)
    assert(scaffolder.interactionVariable eq value)
    assert(grader.interaction eq element)
    assert(scaffolder.interaction eq element)
    assert(element.interactionVariable eq value)
    assertEquals(grader.interactionVariable.currentValue, "initialized default")
    assertEquals(scaffolder.interactionVariable.history.lastStateOption.map(_.value), Some("initialized default"))
    assertEquals(grader.requests, Nil)
    assertEquals(scaffolder.requests, Nil)
    assertEquals(value.history.gradings, Map.empty)
    assertEquals(value.history.scaffoldings, Map.empty)
  }

  test("an unbound grader still supports standalone execution") {
    val grader = new RecordingGrader
    intercept[IllegalStateException](grader.interactionVariable)
    grader.gradeStateSafe("standalone", true).map { result =>
      assert(result.grader eq grader)
      assertEquals(result.gradedState, "standalone")
      assertEquals(result.gradingStatus, GradingStatus.Correct())
      assertEquals(grader.requests, List("standalone" -> true))
      intercept[IllegalStateException](grader.interaction)
    }(using ExecutionContext.global)
  }

  test("binding is idempotent only for the exact interaction, not equal owners or matching ids") {
    val first = TextInteraction("same-id")
    val second = TextInteraction("same-id")
    assertEquals(first, second)
    assert(!(first eq second))
    val grader = new RecordingGrader
    val scaffolder = new RecordingScaffolder
    InteractionBoundComponent.bindAll(first, List(grader, scaffolder))
    InteractionBoundComponent.bindAll(first, List(grader, scaffolder, grader))
    intercept[IllegalArgumentException](InteractionBoundComponent.bindAll(second, List(grader, scaffolder)))
    assert(grader.interaction eq first)
    assert(scaffolder.interaction eq first)
    assert(grader.interactionVariable eq first.interactionVariable)
    assert(scaffolder.interactionVariable eq first.interactionVariable)
    assertEquals(grader.requests, Nil)
    assertEquals(scaffolder.requests, Nil)
  }

  test("additional variables for one owner do not rebind its components") {
    val grader = new RecordingGrader
    val scaffolder = new RecordingScaffolder
    val value = interaction(List(grader), Some(scaffolder)).interactionVariable
    val duplicate = InteractionVariable(value.underlyingInteraction)
    val copied = value.copy()
    assert(!(duplicate eq value))
    assert(!(copied eq value))
    assert(grader.interaction eq value.underlyingInteraction)
    assert(grader.interactionVariable eq value)
    assert(scaffolder.interactionVariable eq value)
    duplicate.updateHistory(_.withAddedEvent(InteractionVariableState("duplicate", MAJOR, LocalDateTime.now().plusSeconds(1))))
    assertEquals(duplicate.currentValue, "duplicate")
    assertEquals(value.currentValue, "")
    val standaloneScaffolder = new RecordingScaffolder
    val first = interaction(Nil, Some(standaloneScaffolder)).interactionVariable
    intercept[IllegalArgumentException](interaction(Nil, Some(standaloneScaffolder)).interactionVariable)
    assert(standaloneScaffolder.interactionVariable eq first)
  }

  test("an owner binding can initialize its lazy variable without recursive construction") {
    val element = BoundDefinition("lazy-owner", "initialized default")
    val grader = element.gradingElements.head
    val scaffolder = element.scaffoldingElement.get
    InteractionBoundComponent.bindAll(element, List(grader, scaffolder))
    assert(grader.interaction eq element)
    assert(scaffolder.interaction eq element)
    val value = grader.interactionVariable
    assert(value eq element.interactionVariable)
    assert(value eq scaffolder.interactionVariable)
    assertEquals(value.currentValue, "initialized default")
    assertEquals(grader.requests, Nil)
    assertEquals(scaffolder.requests, Nil)
  }

  test("a conflicting component leaves every new component unbound") {
    val shared = new RecordingGrader
    val original = variable(shared)
    val fresh = new RecordingGrader
    val scaffolder = new RecordingScaffolder
    val conflicting = interaction(List(fresh, shared), Some(scaffolder))
    intercept[IllegalArgumentException](conflicting.interactionVariable)
    intercept[IllegalStateException](fresh.interactionVariable)
    intercept[IllegalStateException](scaffolder.interactionVariable)
    assert(shared.interactionVariable eq original)
    val valid = interaction(List(fresh), Some(scaffolder)).interactionVariable
    assert(fresh.interactionVariable eq valid)
    assert(scaffolder.interactionVariable eq valid)
    assert(shared.interactionVariable eq original)
  }

  test("components read current answers and historical grades from their exact variable") {
    val grader = new RecordingGrader
    val scaffolder = new RecordingScaffolder
    val value = interaction(List(grader), Some(scaffolder)).interactionVariable
    val time = LocalDateTime.of(2026, 10, 17, 10, 0)
    val grade = GradingResult[String, String](grader, "older", GradingStatus.Incorrect())
    val graded = GradedState("older", Set(grade))
    value.updateHistory(_.copy(events = Set(InteractionVariableState("older", MAJOR, time)),
      gradings = Map("older" -> graded)))
    value.updateHistory(_.withAddedEvent(InteractionVariableState("current", MAJOR, time.plusSeconds(1))))
    assertEquals(grader.interactionVariable.currentValue, "current")
    assertEquals(scaffolder.interactionVariable.currentValue, "current")
    assertEquals(grader.interactionVariable.history.gradings("older").state, "older")
    assertEquals(scaffolder.interactionVariable.history.gradings("older").testsWithResults.head.gradedState, "older")
    assertEquals(value.history.gradings, Map("older" -> graded))
    assertEquals(grader.requests, Nil)
    assertEquals(scaffolder.requests, Nil)
  }

  test("binding preserves concrete grader codecs and value identity without copying its owner") {
    val grader = SlideshowGrader(true)
    val json = write(grader)
    val binary = writeBinary(grader).toVector
    val hash = grader.hashCode()
    val owner = Slideshow("codec", Nil)
    InteractionBoundComponent.bindAll(owner, List(grader))
    assertEquals(write(grader), json)
    assertEquals(writeBinary(grader).toVector, binary)
    assertEquals(grader.hashCode(), hash)
    List(grader.copy(), read[SlideshowGrader](json), readBinary[SlideshowGrader](binary.toArray))
      .zipWithIndex.foreach { (restored, index) =>
        assertEquals(restored, grader)
        assertEquals(restored.hashCode(), hash)
        assert(!(restored eq grader))
        intercept[IllegalStateException](restored.interactionVariable)
        val second = Slideshow(s"codec-copy-$index", Nil)
        InteractionBoundComponent.bindAll(second, List(restored))
        assert(restored.interaction eq second)
        assert(restored.interactionVariable eq second.interactionVariable)
        assert(grader.interaction eq owner)
      }
  }

  test("definition reconstruction gives equal interactions their own feedback components") {
    val original = BoundDefinition("definition-owner", "answer")
    val originalValue = original.interactionVariable
    val json = write(original)
    val binary = writeBinary(original)
    List(read[BoundDefinition](json), readBinary[BoundDefinition](binary)).foreach { restored =>
      assertEquals(restored, original)
      assert(!(restored eq original))
      val grader = restored.gradingElements.head
      val scaffolder = restored.scaffoldingElement.get
      assert(!(grader eq original.gradingElements.head))
      assert(!(scaffolder eq original.scaffoldingElement.get))
      intercept[IllegalStateException](grader.interaction)
      val restoredValue = restored.interactionVariable
      assert(grader.interaction eq restored)
      assert(scaffolder.interaction eq restored)
      assert(grader.interactionVariable eq restoredValue)
      assert(!(restoredValue eq originalValue))
      assertEquals(restoredValue.currentValue, "answer")
      assertEquals(write(restored), json)
      assertEquals(writeBinary(restored).toVector, binary.toVector)
    }
    assert(original.gradingElements.head.interaction eq original)
  }

  test("a concrete scaffolder resolves its current client at dispatch after asynchronous preparation") {
    given ExecutionContext = ExecutionContext.global
    val command = LLMCommands.feedbackLlmCommandFactory
    def client(label: String): ExecutionClient = command.toLocalExecutionClient((request, _) =>
      Future.successful(label + ":" + request.prompt))
    val selected = State(client("first"))
    val lookups = State(0)
    val prepared = Promise[Unit]()
    val scaffolder = new WorkbookInteractionScaffolder[String, String] {
      private val executionClient: () => ExecutionClient = () => {
        lookups.update(_ + 1)
        selected.now()
      }
      override def generateFeedback(state: String): Future[ScaffoldingResult[String, String]] =
        prepared.future.flatMap(_ => command.sendCommandTo(executionClient(),
          LLMCommands.FeedbackLlmRequest(state, ""), Some(BasicLogger())))
          .map(response => ScaffoldingResult(state, LanguageMap.universalMap(response.result),
            FeedbackEntity.TestEntity(), Some(response.result)))
    }
    val value = interaction(Nil, Some(scaffolder)).interactionVariable
    assert(scaffolder.interactionVariable eq value)
    assertEquals(lookups.now(), 0)
    val pending = scaffolder.generateFeedback("one")
    assert(!pending.isCompleted)
    assertEquals(lookups.now(), 0)
    selected.set(client("second"))
    prepared.success(())
    pending.flatMap { first =>
      assertEquals(first.forState, "one")
      assertEquals(first.additionalInformation, Some("second:one"))
      selected.set(client("third"))
      scaffolder.generateFeedback("two").map { second =>
        assertEquals(second.forState, "two")
        assertEquals(second.additionalInformation, Some("third:two"))
        assertEquals(lookups.now(), 2)
        assertEquals(value.history.scaffoldings, Map.empty)
      }
    }
  }

  test("grading uses the newest timestamp when history iteration is out of order") {
    val grader = new RecordingGrader
    val value = variable(grader)
    val time = LocalDateTime.of(2026, 10, 12, 0, 0)
    value.updateHistory(_.copy(events = ListSet(
      InteractionVariableState("older", MAJOR, time),
      InteractionVariableState("newest", MAJOR, time.plusSeconds(1))
    )))
    value.startGrading(null)
    assertEquals(grader.requests, List("newest" -> false))
  }

  test("cached grading is reused unless grading is explicitly forced") {
    val grader = new RecordingGrader
    val value = variable(grader)
    val cached = GradingResult[String, String](grader, "", GradingStatus.Correct())
    value.updateHistory(_.copy(gradings = Map("" -> GradedState("", Set(cached)))))
    value.startGrading(null)
    assertEquals(grader.requests, Nil)
    value.startGrading(null, forceGrading = true)
    assertEquals(grader.requests, List("" -> true))
  }

  test("empty histories do not request grading") {
    val grader = new RecordingGrader
    val value = variable(grader)
    value.updateHistory(_.copy(events = Set.empty))
    value.startGrading(null, forceGrading = true)
    assertEquals(grader.requests, Nil)
  }
}
