import it.evadid.worker.WebWorkerBackendServer
import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.distribution.formats.WorkerExecutionProtocol.{Request, Response}
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import munit.FunSuite
import upickle.default.*

class BackendWorkerTest extends FunSuite {
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
}
