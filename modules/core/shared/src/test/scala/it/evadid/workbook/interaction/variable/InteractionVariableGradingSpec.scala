package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage, Java, Python}
import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.state.State
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.SerializedException
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.workbook.abstractions.{FeedbackEntity, WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.abstractions.scaffolding.{ProgrammingHintScaffolder, ScaffoldingResult}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowGrader
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

  private def variable(grader: RecordingGrader): InteractionVariable[String, String, String] =
    new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "grading-test"
      override val defaultValue = ""
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val gradingElements = List(grader)
    }.interactionVariable

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
    val variableInitializations = State(0)
    class HintInteraction extends WorkbookInteractionElementFull[ProgrammingState, String, String] {
      override val elementId = "programming-hint-test"
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TurtleRecreateShapeInteraction.factory
      override val gradingElements = checks
      override val scaffoldingElement: Option[ProgrammingHintScaffolder[String]] = Some(
        new ProgrammingHintScaffolder[String](this, task, () => language.now(), () => {
          lookups.update(_ + 1)
          executor.now()
        }))
      override val defaultValue = source
      override lazy val interactionVariable: InteractionVariable[ProgrammingState, String, String] = {
        variableInitializations.update(_ + 1)
        InteractionVariable[ProgrammingState, String, String](this)
      }
    }
    val element = new HintInteraction
    val scaffolder = element.scaffoldingElement.get
    lazy val variable = element.interactionVariable
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
    val scaffolder = new ProgrammingHintScaffolder[String](f.element, LanguageMap.universalMap("Public task"), () => German, () => {
      calls.update(_ + 1)
      throw failure
    })
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

  test("a scaffolder stores its owner without initializing the owner's answer during construction") {
    val f = new HintFixture
    assert(f.scaffolder.interaction eq f.element)
    assertEquals(f.variableInitializations.now(), 0)
    assertEquals(f.lookups.now(), 0)
    assertEquals(f.requests.now(), Vector.empty)
    val pending = f.scaffolder.generateFeedback(f.source)
    assertEquals(f.variableInitializations.now(), 1)
    pending.map { result =>
      assertEquals(f.variable.currentValue, f.source)
      assertEquals(result.forState, f.source)
      assertEquals(f.variableInitializations.now(), 1)
      assertEquals(f.variable.history.scaffoldings, Map.empty)
    }
  }

  test("hint context keeps separate histories for matching interaction IDs and equal check definitions") {
    val firstCheck = HintCheck("same check", GradingImportance.REQUIRED_TEST)
    val secondCheck = firstCheck.copy()
    val first = new HintFixture(checks = List(firstCheck))
    val second = new HintFixture(checks = List(secondCheck))
    assertEquals(first.element.elementId, second.element.elementId)
    assertEquals(firstCheck, secondCheck)
    assert(!(firstCheck eq secondCheck))
    assert(first.scaffolder.interaction eq first.element)
    assert(second.scaffolder.interaction eq second.element)
    val correct = GradingResult[ProgrammingState, String](firstCheck, first.source, GradingStatus.Correct())
    val incorrect = GradingResult[ProgrammingState, String](secondCheck, second.source, GradingStatus.Incorrect())
    first.variable.updateHistory(_.copy(gradings = Map(first.source -> GradedState(first.source, Set(correct)))))
    second.variable.updateHistory(_.copy(gradings = Map(second.source -> GradedState(second.source, Set(incorrect)))))
    first.scaffolder.generateFeedback(first.source).zip(second.scaffolder.generateFeedback(second.source)).map { _ =>
      assertEquals(first.context("checks")(0)("status").str, "correct")
      assertEquals(second.context("checks")(0)("status").str, "incorrect")
      assertEquals(firstCheck.runs.now(), 0)
      assertEquals(secondCheck.runs.now(), 0)
    }
  }

  test("a grader supports standalone execution without an interaction") {
    val grader = new RecordingGrader
    grader.gradeStateSafe("standalone", true).map { result =>
      assert(result.grader eq grader)
      assertEquals(result.gradedState, "standalone")
      assertEquals(result.gradingStatus, GradingStatus.Correct())
      assertEquals(grader.requests, List("standalone" -> true))
    }(using ExecutionContext.global)
  }

  test("standalone graders retain JSON and MessagePack codecs") {
    val grader = SlideshowGrader(true)
    val json = write(grader)
    val binary = writeBinary(grader).toVector
    val hash = grader.hashCode()
    List(grader.copy(), read[SlideshowGrader](json), readBinary[SlideshowGrader](binary.toArray))
      .foreach { restored =>
        assertEquals(restored, grader)
        assertEquals(restored.hashCode(), hash)
        assert(!(restored eq grader))
        assertEquals(write(restored), json)
        assertEquals(writeBinary(restored).toVector, binary)
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

  import it.evadid.core.datastructures.language.LanguageMap
  import it.evadid.core.datastructures.state.State
  import it.evadid.core.datastructures.state.observable.ObservableValue
  import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
  import it.evadid.util.logging.Logger
  import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
  import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
  import it.evadid.workbook.interaction.sync.*
  import it.evadid.workbook.interaction.sync.SyncControl.InteractionVariableSyncReport
  import it.evadid.workbook.interaction.sync.destination.{SyncDestination, SyncDestinationHistory}
  import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, FetchResponse, SyncDirection, SyncStatus}

  private def restoreVariable(graders: List[RecordingGrader]): InteractionVariable[String, String, String] = {
    val interaction = new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "grading-test"
      override val defaultValue = ""
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val gradingElements = graders
    }
    interaction.interactionVariable
  }

  private type History = InteractionVariableHistory[String, String, String]
  private val restoreTime = LocalDateTime.of(2026, 10, 17, 12, 0)
  private def event(value: String, seconds: Long, importance: UpdateImportance = MAJOR) =
    InteractionVariableState(value, importance, restoreTime.plusSeconds(seconds))
  private def hint(state: String, text: String) = ScaffoldingResult[String, String](state,
    LanguageMap.universalMap(text), FeedbackEntity.TestEntity(), None)
  private def grade(grader: RecordingGrader, state: String, seconds: Long, status: GradingStatus) =
    GradingResult(grader, state, status, gradingTime = restoreTime.plusSeconds(seconds))
  private def history(events: Set[InteractionVariableState[String]] = Set.empty,
      grades: Map[String, GradedState[String, String]] = Map.empty,
      hints: Map[String, ScaffoldingResult[String, String]] = Map.empty): History =
    InteractionVariableHistory(events, grades, hints)

  private def restoreLocation(): SyncInformationWithContext = {
    val destination = new SyncDestinationHistory {
      override def shouldBePersistant(): Boolean = true
      override def isLocal: Boolean = true
      override def fetchAll(logger: SyncLogger, context: UsageContext): Future[FetchResponse[SyncContext, String]] =
        throw AssertionError("Restore must use its existing report")
      override def storeTo(logger: SyncLogger, context: SyncContext, payload: String): Future[SyncSuccess] =
        throw AssertionError("Restore must not write")
      override def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess] =
        throw AssertionError("Restore must not clear storage")
      override def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess] =
        throw AssertionError("Restore must not clear storage")
      override protected def deserializeFromConstructorLikeString(from: ConstructorLikeReadResult): Option[SyncDestination] = None
      override protected def serializeToConstructorLikeString(): ConstructorLikeReadResult = ConstructorLikeReadResult("RestoreTest", Nil)
    }
    SyncInformation(destination, SyncStrategy.SYNC_EVERYTHING).forContext(UsageContext("test", "workbook", "user"))
  }

  private class RestoreSync(value: InteractionVariable[String, String, String], histories: List[History]) extends SyncControl {
    val stores = State(0)
    override val controlLogger = Logger.withNameAndPrefixes(printMap = PrintToStdLogger.printNothing)
    override val syncLogger = SyncLogger(controlLogger)
    override def createCurrentReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): InteractionVariableSyncReport[T, GA, SA] = {
      assert(forVariable eq value)
      val remote = histories.map { current =>
        val location = restoreLocation()
        val key = location.usageContext.toSyncContext(value.keyForSerialization)
        location -> SyncStatus(Some(restoreTime), key, Some(DataEntryToSync(key, current, restoreTime, SyncDirection.SERVER_TO_CLIENT)))
      }.toMap
      InteractionVariableSyncReport(value.history, remote, remote.keys.toList).asInstanceOf[InteractionVariableSyncReport[T, GA, SA]]
    }
    override def createObservableReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): ObservableValue[InteractionVariableSyncReport[T, GA, SA]] =
      throw AssertionError("Restore must not subscribe")
    override def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[?] = throw AssertionError("Restore must not fetch")
    override def ensureCachesContainLastElementsToWrite(values: List[InteractionVariableUntyped]): Future[?] =
      throw AssertionError("Restore must not prepare writes")
    override def requestStore(values: List[InteractionVariableUntyped]): Future[?] = { stores.update(_ + 1); Future.successful(()) }
  }

  test("history restore keeps full feedback and the newest result of each grader in any source order") {
    val first = new RecordingGrader
    val second = new RecordingGrader
    val third = new RecordingGrader
    val oldFirst = grade(first, "current", 3, GradingStatus.Incorrect())
    val localSecond = grade(second, "current", 9, GradingStatus.Correct())
    val newFirst = grade(first, "current", 7, GradingStatus.Correct())
    val remoteThird = grade(third, "current", 8, GradingStatus.Incorrect())
    val local = history(Set(event("current", 10)), Map("current" -> GradedState("current", Set(oldFirst, localSecond))),
      Map("current" -> hint("current", "Local hint")))
    val sources = List(
      history(Set(event("older", 1)), Map("current" -> GradedState("current", Set(newFirst,
        grade(second, "current", 6, GradingStatus.Incorrect())))), Map("older" -> hint("older", "Older hint"))),
      history(Set(event("other", 2)), Map("current" -> GradedState("current", Set(remoteThird,
        grade(first, "current", 5, GradingStatus.PartiallyCorrect())))), Map("other" -> hint("other", "Other hint"))))
    sources.permutations.foreach { ordered =>
      val restored = local.mergeWith(ordered)
      assertEquals(restored.events, local.events ++ sources.flatMap(_.events))
      assertEquals(restored.gradings("current").testsWithResults, Set(newFirst, localSecond, remoteThird))
      assertEquals(restored.scaffoldings, local.scaffoldings ++ sources.flatMap(_.scaffoldings))
      assertEquals(restored.mergeWith(ordered), restored)
    }
    assertEquals(local.gradings("current").testsWithResults, Set(oldFirst, localSecond))
    assertEquals(sources.map(_.events), List(Set(event("older", 1)), Set(event("other", 2))))
  }

  test("history restore retains conflicting results tied at the latest grading time") {
    val grader = new RecordingGrader
    val correct = grade(grader, "answer", 5, GradingStatus.Correct())
    val incorrect = grade(grader, "answer", 5, GradingStatus.Incorrect())
    val older = grade(grader, "answer", 4, GradingStatus.PartiallyCorrect())
    val local = history(grades = Map("answer" -> GradedState("answer", Set(older))))
    val sources = List(correct, incorrect).map(result => history(grades = Map("answer" -> GradedState("answer", Set(result)))))
    sources.permutations.foreach(ordered =>
      assertEquals(local.mergeWith(ordered).gradings("answer").testsWithResults, Set(correct, incorrect)))
  }

  test("history restore rejects feedback whose state does not match its map key") {
    val grader = new RecordingGrader
    val valid = grade(grader, "answer", 2, GradingStatus.Correct())
    val wrong = grade(grader, "elsewhere", 9, GradingStatus.Incorrect())
    val local = history(grades = Map("answer" -> GradedState("elsewhere", Set(wrong))),
      hints = Map("answer" -> hint("elsewhere", "Invalid local hint")))
    val remote = history(Set(event("answer", 1)), Map(
      "answer" -> GradedState("answer", Set(valid, wrong)), "wrong-key" -> GradedState("elsewhere", Set(wrong))),
      Map("answer" -> hint("answer", "Valid remote hint"), "wrong-key" -> hint("elsewhere", "Invalid remote hint")))
    val restored = local.mergeWith(List(remote))
    assertEquals(restored.gradings, Map("answer" -> GradedState("answer", Set(valid))))
    assertEquals(restored.scaffoldings, Map("answer" -> hint("answer", "Valid remote hint")))
  }

  test("history restore keeps valid local hints and restores only unambiguous remote hints as one batch") {
    val local = history(hints = Map("local" -> hint("local", "Accepted locally")))
    val shared = hint("shared", "Same saved hint")
    val sources = List(
      history(hints = Map("local" -> hint("local", "Remote replacement"), "shared" -> shared,
        "conflict" -> hint("conflict", "First candidate"))),
      history(hints = Map("shared" -> shared, "conflict" -> hint("conflict", "Second candidate"))))
    sources.permutations.foreach { ordered =>
      val restored = local.mergeWith(ordered)
      assertEquals(restored.scaffoldings, local.scaffoldings + ("shared" -> shared))
      assertEquals(restored.mergeWith(ordered), restored)
    }
  }

  test("history restore retains the existing default cleanup without inventing answer events") {
    val first = event("default", 0, UpdateImportance.DEFAULT)
    val last = event("default", 1, UpdateImportance.DEFAULT)
    val local = history(Set(first), hints = Map("default" -> hint("default", "Hint only")))
    val defaults = local.mergeWith(List(history(Set(last))))
    assertEquals(defaults.events, Set(last))
    assertEquals(defaults.scaffoldings, local.scaffoldings)
    val edited = defaults.mergeWith(List(history(Set(event("edited", 2)))))
    assertEquals(edited.events, Set(event("edited", 2)))
    assertEquals(edited.mergeWith(Nil), edited)
    assertEquals(local.events, Set(first))
  }

  test("executeLoad restores hints and grading results from the current typed cache report") {
    val grader = new RecordingGrader
    val value = variable(grader)
    val result = grade(grader, "saved", 3, GradingStatus.Correct())
    val saved = history(Set(event("saved", 2)), Map("saved" -> GradedState("saved", Set(result))),
      Map("saved" -> hint("saved", "Saved hint")))
    val sync = new RestoreSync(value, List(saved))
    value.executeLoad(sync)
    assertEquals(value.history, saved)
    assertEquals(value.currentValue, "saved")
    value.executeLoad(sync)
    assertEquals(value.history, saved)
    assertEquals(sync.stores.now(), 0)
    assertEquals(grader.requests, Nil)
  }

  test("executeLoad batches conflicting remote hints without replacing local feedback or the answer") {
    val grader = new RecordingGrader
    val value = variable(grader)
    val local = history(Set(event("current", 10)), hints = Map("current" -> hint("current", "Local hint")))
    value.updateHistory(_ => local)
    val sources = List(
      history(Set(event("older", 1)), hints = Map("current" -> hint("current", "Remote hint"), "older" -> hint("older", "First"))),
      history(Set(event("older", 1)), hints = Map("older" -> hint("older", "Second"))))
    val sync = new RestoreSync(value, sources)
    value.executeLoad(sync)
    assertEquals(value.history.events, local.events ++ sources.flatMap(_.events))
    assertEquals(value.history.scaffoldings, local.scaffoldings)
    assertEquals(value.currentValue, "current")
    assertEquals(sync.stores.now(), 0)
    assertEquals(grader.requests, Nil)
    assertEquals(local.events, Set(event("current", 10)))
  }

  test("a cached foreign grader does not suppress the currently configured grader") {
    val current = new RecordingGrader
    val foreign = new RecordingGrader
    val value = variable(current)
    val cached = grade(foreign, "", 1, GradingStatus.Correct())
    value.updateHistory(_.copy(gradings = Map("" -> GradedState("", Set(cached)))))
    value.startGrading(null)
    assertEquals(current.requests, List("" -> false))
    assertEquals(foreign.requests, Nil)
  }

  test("a partially covered grading cache reruns every configured grader") {
    val first = new RecordingGrader
    val second = new RecordingGrader
    val value = restoreVariable(List(first, second))
    val cached = grade(first, "", 1, GradingStatus.Correct())
    value.updateHistory(_.copy(gradings = Map("" -> GradedState("", Set(cached)))))
    value.startGrading(null)
    assertEquals(first.requests, List("" -> false))
    assertEquals(second.requests, List("" -> false))
  }

  test("cached grading groups and individual results must both match the current answer") {
    List("group", "result").foreach { mismatch =>
      val current = new RecordingGrader
      val value = variable(current)
      val cached = grade(current, if mismatch == "result" then "another answer" else "", 1, GradingStatus.Correct())
      val grouped = GradedState(if mismatch == "group" then "another answer" else "", Set(cached))
      value.updateHistory(_.copy(gradings = Map("" -> grouped)))
      value.startGrading(null)
      assertEquals(current.requests, List("" -> false), mismatch)
    }
  }

  test("complete current grader coverage reuses the cache even when foreign results remain") {
    val first = new RecordingGrader
    val second = new RecordingGrader
    val foreign = new RecordingGrader
    val value = restoreVariable(List(first, second))
    val results = List(first, second, foreign).map(grade(_, "", 1, GradingStatus.Correct())).toSet
    value.updateHistory(_.copy(gradings = Map("" -> GradedState("", results))))
    value.startGrading(null)
    assertEquals(first.requests, Nil)
    assertEquals(second.requests, Nil)
    assertEquals(foreign.requests, Nil)
    value.startGrading(null, forceGrading = true)
    assertEquals(first.requests, List("" -> true))
    assertEquals(second.requests, List("" -> true))
    assertEquals(foreign.requests, Nil)
  }

}
