package it.evadid.distribution.commandTypes

import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.core.datastructures.chat.{Message, MessengerModel}
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig, UserTokenInfo}
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.distribution.command.{ExecutionCommand, ExecutionCommandFactory}
import it.evadid.llm.LlmConfiguration
import it.evadid.sql.{BackendDatabase, DatabaseHistoryFormat}
import it.evadid.vm.analysis.ProgramAnalysis
import it.evadid.vm.conversion.dependencies.{ProgrammingEnvironment, ProgrammingLibrary}
import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.simulation.python.PythonTurtleCalls
import it.evadid.vm.simulation.java.JavaTurtleExecutionEvidence as Evidence
import it.evadid.workbook.abstractions.InteractionFormat.TextInteractionFormat
import it.evadid.workbook.interactions.programming.state.ProgrammingStateRaw
import it.evadid.workbook.interactions.sql.SqlDatabaseConfig
import it.evadid.workbook.sync.{EventSyncStrategy, SyncSuccess, UpdateImportance, UsageContext}
import it.evadid.workbook.sync.model.InteractionHistoryJson
import it.evadid.workbook.model.history.InteractionVariableHistory
import it.evadid.workbook.model.history.events.{AnswerChangedEvent, EventId}
import it.evadid.workbook.interactions.qr.{QrCode, QrErrorCorrection}
import it.evadid.workbook.elements.structure.Workbook
import it.evadid.workbook.elements.structure.Workbook.WorkbookMetadata
import it.evadid.workbook.session.{AllWorkbookInfo, SessionData, WorkbookConfig}
import it.evadid.core.datastructures.language.LanguageMapContentId
import java.time.LocalDateTime
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*

/** Every registered command carries a representative request to its handler and its response back unchanged. */
class ExecutionCommandRoundTripSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val at = LocalDateTime.of(2026, 4, 2, 10, 15, 30, 123456789)

  /** One registered command with a representative request and response. */
  private final case class Sample[I: ReadWriter, O: ReadWriter](factory: ExecutionCommandFactory[I, O], request: I, response: O) {
    def name: String = factory.name

    /** Sends the request through the command's local client and the real codecs, then checks both directions. */
    def roundTrip(): Future[Unit] = {
      val received = State(Vector.empty[I])
      val client = factory.toLocalExecutionClient((input, _) => { received.update(_ :+ input); Future.successful(response) })
      factory.sendCommandTo(client, request).map { reply =>
        assertEquals(received.now(), Vector(request), s"$name: the handler receives the exact request")
        assertEquals(reply.result, response, s"$name: the caller receives the exact response")
        assertEquals(reply.command.name, name)
        assertEquals(read[ExecutionCommand](write(reply.command)), reply.command, s"$name: the command survives its text form")
        assertEquals(read[I](reply.command.json), request)
      }
    }
    def command: ExecutionCommand = ExecutionCommand(name, writeJs(request))
    def localClient = factory.toLocalExecutionClient((_, _) => Future.successful(response))
  }

  private val user = AllUserInfo.createNewUser("Student", "student@example.invalid", UserConfig(Nil, false))
  private val signed = SignedToken(UserTokenInfo(user.user, at, at.plusDays(1)), "signature")
  private val usage = UsageContext("edusquirrel", "workbook", user.user.personId)
  private val key = usage.toSyncContext("answer_history")
  private val history = InteractionHistoryJson.fromHistory(TextInteractionFormat, InteractionVariableHistory.empty[TextInteractionFormat.type]
    .addEvent(AnswerChangedEvent[TextInteractionFormat.type]("answer \"quoted\" 学校", at, UpdateImportance.MAJOR)))
  private val synced = SyncSuccess[EventId](Set(EventId.random()), Set.empty, Set(EventId.random()), Set.empty, at)
  private val removed = SyncSuccess.removed(Set(key)).copy(timestampCommitted = at)
  private val qr = QrCode.fromText("https://example.invalid/?q=1", QrErrorCorrection.Q, None, None)
  private val snapElement = SourceParsing.parse("""<project name="demo"><scripts><script x="1"/></scripts></project>""", SourceLanguage.Snap).syntax
    .toOption.get.root.element
  private val pythonProgram = ProgrammingStateRaw(AppLanguage.Python, "helper(1)\nforward(10)\n")
  private val session = SessionData(at, Map(key -> history), user.user, Some(user.config), AllWorkbookInfo(
    Workbook("workbook", WorkbookMetadata(Set(user.user), Set.empty, LanguageMapContentId("workbook/title"), List(AppLanguage.English)), Seq.empty),
    WorkbookConfig(None), Map("section" -> 1.5)))

  private val samples: List[Sample[?, ?]] = List(
    Sample(UserCommands.loginCommand, UserCommands.LoginRequest(user.user.mail, Right(signed)),
      UserCommands.LoginResponse(userKnown = true, tokenValid = true, Some(user))),
    Sample(UserCommands.updateAccountCommand, UserCommands.UpdateAccountRequest(user), UserCommands.UpdateAccountResponse(true, signed)),
    Sample(UserCommands.createAccountCommand, UserCommands.CreateAccountRequest(user), UserCommands.CreateAccountResponse(true, Some(signed))),
    Sample(UserCommands.authMailCommand, UserCommands.AuthMailRequest(user.user.mail), MailCommands.SendMailResponse(Some(user.user), sent = true)),
    Sample(SQLCommands.StoreToDbCommand, SQLCommands.StoreToDbRequest(key, history, BackendDatabase, DatabaseHistoryFormat.RelationalFormat), synced),
    Sample(SQLCommands.bulkStoreToDbCommand, SQLCommands.BulkStoreToDbRequest(Vector(key, usage.toSyncContext("other")),
      Vector(history, history), BackendDatabase, DatabaseHistoryFormat.BlobFormat), synced),
    Sample(SQLCommands.fetchEventsFromDbCommand, SQLCommands.FetchEventsFromDbRequest(usage, Seq("answer_history"), LocalDateTime.MIN,
      LocalDateTime.MAX, BackendDatabase, DatabaseHistoryFormat.RelationalFormat, EventSyncStrategy.KEEP_LAST_AND_MAJOR),
      SQLCommands.DbFetchResponse(Map(key -> history))),
    Sample(SQLCommands.clearValuesDbCommand, SQLCommands.DeleteInDbRequest(usage, Some("answer_history"), BackendDatabase,
      DatabaseHistoryFormat.BlobFormat), removed),
    Sample(SQLCommands.resetUserHistoriesDbCommand, SQLCommands.DeleteInDbRequest(usage, None, BackendDatabase,
      DatabaseHistoryFormat.RelationalFormat, allWorkbooks = true), removed),
    Sample(MailCommands.sendMailCommand, MailCommands.SendMailRequest("teacher@example.invalid", "Subject: Grüße", "Line 1\nLine 2", Nil),
      MailCommands.SendMailResponse(None, sent = true)),
    Sample(SubmissionCommands.submitSessionCommand, SubmissionCommands.SubmitSessionEvent(session),
      SubmissionCommands.SubmitSessionResponse(submittedInteractions = 1, confirmationSent = true)),
    Sample(LLMCommands.completeLLMCommandFactory, LLMCommands.MessengerChatCompletionRequest("system",
      MessengerModel(Seq(Message("question?", MessengerModel.pAgent, at))), Some(LlmConfiguration("https://llm.example", "model", None))),
      Message("answer", MessengerModel.pAgent, at)),
    Sample(LLMCommands.feedbackLlmCommandFactory, LLMCommands.FeedbackLlmRequest("{\"task\":\"t\"}", "system",
      Some(LlmConfiguration("https://llm.example", "model", Some("key"), Some(256)))), "{\"hints\":[]}"),
    Sample(SqlEditorCommands.execute, SqlEditorCommands.Request(SqlDatabaseConfig("school_exercises"), Some("SELECT name FROM pupil;")),
      SqlEditorCommands.Response(SqlEditorCommands.Status.Ready, "", Seq(SqlEditorCommands.Result(Seq("name"), Seq(Seq(Some("Ada"), None)))),
        Seq(SqlEditorCommands.Table("pupil", Seq(SqlEditorCommands.Column("name", "VARCHAR", nullable = true, primaryKey = false)), Nil)))),
    Sample(WorkerCommands.health, WorkerCommands.HealthRequest(), WorkerCommands.HealthResult("edusquirrel-worker", Seq("worker.health"))),
    Sample(WorkerCommands.parseSnapXml, WorkerCommands.ParseSnapXmlRequest("<project name=\"demo\"/>", "project"), Seq(snapElement)),
    Sample(WorkerCommands.flowchart, WorkerCommands.FlowchartRequest(AppLanguage.Python, "if x:\n    y = 1\n"),
      PythonFlowchartGutter.fromPython("if x:\n    y = 1\nelse:\n    y = 2\n")),
    Sample(WorkerCommands.programAnalysis, WorkerCommands.ProgramAnalysisRequest(pythonProgram, ProgrammingEnvironment.of(ProgrammingLibrary.Turtle)),
      ProgramAnalysis.of(pythonProgram)),
    Sample(WorkerCommands.calculateQrCode, WorkerCommands.CalculateQrCodeRequest("https://example.invalid/?q=1", QrErrorCorrection.Q),
      WorkerCommands.CalculateQrCodeResult(qr, qr.modules)),
    Sample(WorkerCommands.runPython, WorkerCommands.RunPythonRequest("forward(n)", Map("n" -> ujson.Num(10)), Map("turtle" -> Seq("forward")),
      turtleCalls = PythonTurtleCalls.Logged), WorkerCommands.RunPythonResult(ujson.Obj("done" -> true), "ok\n", "", Seq(WorkerCommands.PythonCallback("turtle", "forward", Seq(ujson.Num(10)))))),
    Sample(WorkerCommands.javaExecutionEvidence, WorkerCommands.JavaExecutionEvidenceRequest("class Main { static int f(int a) { return a; } }",
      Some("f"), Vector(Evidence.Scalar.Int32(3))), Evidence.Snapshot(Evidence.Outcome.Completed, 2, 0, Vector.empty))
  )

  test("every registered command has a sample, and names resolve to their own factory") {
    assertEquals(samples.map(_.name).sorted, ExecutionCommandFactory.knownCommands.map(_.name).sorted)
    samples.foreach(sample => assertEquals(ExecutionCommandFactory.forName(sample.name).map(_.name), Some(sample.name)))
    assertEquals(ExecutionCommandFactory.forName("unknown-command"), None)
  }

  samples.foreach { sample =>
    test(s"${sample.name} carries its request to the handler and its response back unchanged") {
      sample.roundTrip()
    }
  }

  test("a command is only accepted by the client of its own factory") {
    val clients = samples.map(sample => sample.name -> sample.localClient)
    samples.foreach { sample =>
      assertEquals(clients.filter(_._2.canExecuteCommand(sample.command)).map(_._1), List(sample.name))
    }
  }

  test("log copies of every command are valid JSON, stable when stripped again, and leave the command unchanged") {
    samples.foreach { sample =>
      val command = sample.command
      val before = write(command)
      val logged = command.stripPrivacySensitiveInformation()
      assertEquals(logged.name, sample.name)
      assertEquals(read[ExecutionCommand](write(logged)), logged)
      assertEquals(logged.stripPrivacySensitiveInformation(), logged, sample.name)
      assertEquals(write(command), before, s"${sample.name}: stripping a log copy never changes the command itself")
    }
  }
}
