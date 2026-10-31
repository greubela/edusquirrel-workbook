import it.evadid.worker.{JavaTurtleEvidenceJob, WebWorkerBackendServer}
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.distribution.formats.WorkerExecutionProtocol.{Request, Response}
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.vm.simulation.java.{JavaTurtleExecutionEvidence as Evidence, JavaTurtleRuntime as T}
import munit.FunSuite
import upickle.default.*
import scala.concurrent.{ExecutionContext, Future}

class BackendWorkerTest extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  private val evidenceSource = "class Drawing { public static void main(String[] args) { Turtle.forward(1); } }"
  private val evidenceReport = """{"execution":{"status":"Completed","problem":null,"commands":[["forward",1]],"steps":5},"observations":[{"invocation":0,"method":0,"path":["body","0","argument","0"],"value":{"kind":"int","value":"1"}}],"truncated":false}"""
  private def pythonEvidence(raw: String = evidenceReport): RunPythonResult =
    RunPythonResult(ujson.Str(raw), "", "", Nil)

  private def assertEvidenceUnavailable(error: Throwable): Unit = {
    assert(error.isInstanceOf[IllegalArgumentException])
    assertEquals(error.getMessage, "Java execution observations are unavailable.")
    assertEquals(error.getCause, null)
  }

  private def send(id: String, name: String, data: ujson.Value) =
    WebWorkerBackendServer.handleMessage(write(Request(id, ExecutionCommand(name, data)))).map(read[Response](_))(using scala.concurrent.ExecutionContext.global)

  test("health uses the framework response and preserves correlation and timestamps") {
    send("health", health.name, writeJs(HealthRequest())).map { reply =>
      assertEquals(reply.requestId, "health")
      assertEquals(reply.response.result.toOption.get.getAs[HealthResult].commands.toSet, supportedCommands)
      assert(!reply.response.timestamps.timestampFinished.isBefore(reply.response.timestamps.timestampReceived))
    }(using scala.concurrent.ExecutionContext.global)
  }
  test("Snap XML parsing preserves nested elements, Unicode and attributes") {
    val xml = "<project><block s=\"外\"><block s=\"inner\"/></block></project>"
    send("xml", parseSnapXml.name, writeJs(ParseSnapXmlRequest(xml, "block"))).map { reply =>
      val result = reply.response.result.toOption.get.getAs[List[it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapXmlParser.Element]]
      assertEquals(result.map(_.attrOrEmpty("s")), List("外", "inner"))
    }(using scala.concurrent.ExecutionContext.global)
  }
  test("QR worker computes a full symbol with the same result as the shared model") {
    send("qr", calculateQrCode.name, writeJs(CalculateQrCodeRequest("Grüße\nQR", version = Some(2), mask = Some(3)))).map { reply =>
      val result = reply.response.result.toOption.get.getAs[CalculateQrCodeResult]
      assertEquals(result.code.text, "Grüße\nQR")
      assertEquals(result.code.config.version, 2)
      assertEquals(result.modules, result.code.modules)
    }(using scala.concurrent.ExecutionContext.global)
  }
  test("Python flowchart requests and results preserve source and the shared chart") {
    val source = "# Grüße 外\r\nx = 1\r\nif x > 0:\r\n\tprint(x)\r\nprint(2)\r\n"
    val request = PythonFlowchartRequest(source)
    assertEquals(read[PythonFlowchartRequest](write(request)), request)
    assertEquals(readBinary[PythonFlowchartRequest](writeBinary(request)), request)
    val expected = PythonFlowchartGutter.fromPython(source)
    assertEquals(expected.nodes.map(_.line), List(2, 3, 4, 5))
    send("flowchart", pythonFlowchart.name, writeJs(request)).map { reply =>
      assertEquals(reply.requestId, "flowchart")
      val result = reply.response.result.toOption.get.getAs[PythonFlowchartGutter.Chart]
      assertEquals(result, expected)
      assertEquals(read[PythonFlowchartGutter.Chart](write(result)), result)
      assertEquals(readBinary[PythonFlowchartGutter.Chart](writeBinary(result)), result)
    }(using scala.concurrent.ExecutionContext.global)
  }
  test("unsupported Python clears the chart and the next request can succeed") {
    send("incomplete-flowchart", pythonFlowchart.name, writeJs(PythonFlowchartRequest("break"))).flatMap { reply =>
      assertEquals(reply.response.result.toOption.get.getAs[PythonFlowchartGutter.Chart], PythonFlowchartGutter.empty)
      send("valid-flowchart", pythonFlowchart.name, writeJs(PythonFlowchartRequest("print(1)")))
    }(using scala.concurrent.ExecutionContext.global).map { reply =>
      assertEquals(reply.response.result.toOption.get.getAs[PythonFlowchartGutter.Chart].nodes.map(_.line), List(1))
    }(using scala.concurrent.ExecutionContext.global)
  }
  test("invalid flowchart requests return an error without preventing later requests") {
    send("bad-flowchart", pythonFlowchart.name, ujson.Obj("source" -> ujson.Arr())).flatMap { reply =>
      assertEquals(reply.requestId, "bad-flowchart")
      assert(reply.response.result.isLeft)
      send("recovered-flowchart", pythonFlowchart.name, writeJs(PythonFlowchartRequest("print(2)")))
    }(using scala.concurrent.ExecutionContext.global).map { reply =>
      assertEquals(reply.response.result.toOption.get.getAs[PythonFlowchartGutter.Chart].nodes.map(_.line), List(1))
    }(using scala.concurrent.ExecutionContext.global)
  }
  test("unsupported commands and invalid inputs produce serialized errors rather than hanging") {
    send("unsupported", "server-only", ujson.Obj()).flatMap { reply =>
      assert(reply.response.result.isLeft)
      send("bad-qr", calculateQrCode.name, writeJs(CalculateQrCodeRequest("x", version = Some(0))))
    }(using scala.concurrent.ExecutionContext.global).map(reply => assert(reply.response.result.isLeft))(using scala.concurrent.ExecutionContext.global)
  }
  test("malformed commands retain a recoverable request ID and return an error") {
    WebWorkerBackendServer.handleMessage("{\"requestId\":\"broken\",\"command\":12}").map { raw =>
      val reply = read[Response](raw)
      assertEquals(reply.requestId, "broken")
      assert(reply.response.result.isLeft)
    }(using scala.concurrent.ExecutionContext.global)
  }

  test("Java evidence jobs verify Python observations before exposing source locations") {
    val calls = State(0)
    JavaTurtleEvidenceJob.run(JavaExecutionEvidenceRequest(evidenceSource), request => {
      calls.update(_ + 1)
      assert(request.code.contains("method=None, arguments=[]"))
      Future.successful(pythonEvidence())
    }).map { snapshot =>
      assertEquals(calls.now(), 1)
      assertEquals(snapshot.outcome, Evidence.Outcome.Completed)
      assertEquals(snapshot.steps, 5)
      assertEquals(snapshot.commandCount, 1)
      assertEquals(snapshot.expressions.size, 1)
      val expression = snapshot.expressions.head
      assertEquals(expression.method, "main")
      assertEquals(expression.invocation, 0)
      assertEquals(expression.value, Evidence.Scalar.Int32(1))
      assertEquals(evidenceSource.substring(expression.start, expression.end), "1")
    }
  }

  test("Java evidence jobs bind named methods, signed arguments and execution limits") {
    val source = "class Drawing { static void draw(double length) { Turtle.forward(length); } public static void main(String[] args) { Turtle.forward(99); } }"
    val request = JavaExecutionEvidenceRequest(source, Some("draw"),
      Vector(Evidence.Scalar.Float64("8000000000000000")),
      T.Limits(maxSteps = 31, maxCommands = 2, maxCallDepth = 3, maxBlockDepth = 4))
    val raw = evidenceReport.replace("[\"forward\",1]", "[\"forward\",-0.0]")
      .replace("\"kind\":\"int\",\"value\":\"1\"", "\"kind\":\"double\",\"value\":\"8000000000000000\"")
    JavaTurtleEvidenceJob.run(request, python => {
      assert(python.code.contains("method=0, arguments=[-0.0]"))
      assert(python.code.contains("max_steps=31, max_commands=2, max_call_depth=3, max_block_depth=4"))
      Future.successful(pythonEvidence(raw))
    }).map { snapshot =>
      assertEquals(snapshot.outcome, Evidence.Outcome.Completed)
      assertEquals(snapshot.commandCount, 1)
      assertEquals(snapshot.expressions.map(_.method), Vector("draw"))
      assertEquals(snapshot.expressions.map(_.value), Vector(Evidence.Scalar.Float64("8000000000000000")))
      val expression = snapshot.expressions.head
      assertEquals(source.substring(expression.start, expression.end), "length")
    }
  }

  test("Java evidence jobs return command focus only when requested") {
    JavaTurtleEvidenceJob.run(JavaExecutionEvidenceRequest(evidenceSource, commandIndex = Some(0)),
      _ => Future.successful(pythonEvidence())).map { snapshot =>
      assertEquals(snapshot.focus.get.index, 0)
      assertEquals(snapshot.focus.get.command, "forward")
      assertEquals(snapshot.focus.get.facts, Vector.empty)
      assertEquals(snapshot.expressions.head.value, Evidence.Scalar.Int32(1))
    }
  }

  test("Java evidence jobs reject altered reports and unexpected Python output without leaking failures") {
    val valid = pythonEvidence()
    val invalid = Vector(
      pythonEvidence(evidenceReport.replace(",\"truncated\":false", "")),
      pythonEvidence(evidenceReport.replace("\"value\":\"1\"", "\"value\":\"2\"")),
      valid.copy(value = ujson.read(evidenceReport)),
      valid.copy(stdout = "private output"), valid.copy(stderr = "private warning"),
      valid.copy(callbackOps = List(PythonCallback("private", "callback", List(ujson.Str("private value"))))))
    val request = JavaExecutionEvidenceRequest(evidenceSource)
    Future.traverse(invalid) { reply =>
      JavaTurtleEvidenceJob.run(request, _ => Future.successful(reply)).failed.map(assertEvidenceUnavailable)
    }.flatMap { _ =>
      JavaTurtleEvidenceJob.run(request, _ => Future.failed(IllegalStateException("private runtime failure")))
        .failed.map(assertEvidenceUnavailable)
    }
  }

  test("invalid Java evidence requests fail before Python is called") {
    val request = JavaExecutionEvidenceRequest(evidenceSource)
    val helper = request.copy(source = "class Drawing { static void draw(double n) {} public static void main(String[] args) {} }",
      method = Some("draw"))
    val invalid = Vector(request.copy(source = null), request.copy(source = "class PrivateDraft {"),
      request.copy(method = Some("missing")), request.copy(method = Some("main")),
      request.copy(arguments = Vector(Evidence.Scalar.Int32(1))), request.copy(limits = T.Limits(maxSteps = 0)),
      request.copy(commandIndex = Some(-1)), request.copy(commandIndex = Some(10000)),
      helper.copy(arguments = Vector(Evidence.Scalar.Float64("invalid"))),
      helper.copy(arguments = Vector(Evidence.Scalar.Float64("7ff8000000000042"))))
    val calls = State(0)
    Future.traverse(invalid) { value =>
      JavaTurtleEvidenceJob.run(value, _ => {
        calls.update(_ + 1)
        Future.successful(pythonEvidence())
      }).failed.map(assertEvidenceUnavailable)
    }.map(_ => assertEquals(calls.now(), 0))
  }

  test("Java evidence dispatch rejects unsupported source and remains available for later commands") {
    send("bad-java", javaExecutionEvidence.name, writeJs(JavaExecutionEvidenceRequest("class PrivateDraft {")))
      .flatMap { reply =>
        assertEquals(reply.requestId, "bad-java")
        assert(reply.response.result.isLeft)
        val logs = reply.response.additionalOutput.loggerOut + reply.response.additionalOutput.loggerError
        assert(logs.contains("Java execution observations are unavailable."))
        assert(!logs.contains("PrivateDraft"))
        assert(!write(reply.response.result.swap.toOption.get).contains("PrivateDraft"))
        send("health-after-java", health.name, writeJs(HealthRequest()))
      }.map { reply =>
        assertEquals(reply.requestId, "health-after-java")
        assert(reply.response.result.toOption.get.getAs[HealthResult].commands.contains(javaExecutionEvidence.name))
      }
  }
}
