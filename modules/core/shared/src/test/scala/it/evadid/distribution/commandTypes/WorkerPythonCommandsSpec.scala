package it.evadid.distribution.commandTypes

import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.vm.simulation.java.{JavaTurtleExecutionEvidence as Evidence, JavaTurtleRuntime as T, JavaTurtleTrace as J}
import munit.FunSuite
import upickle.default.*

class WorkerPythonCommandsSpec extends FunSuite {
  test("Python requests preserve nested context, callback declarations and execution options") {
    val request = RunPythonRequest("print('学校')", Map("data" -> ujson.Arr(1, "Grüße", ujson.Null)),
      Map("turtle" -> List("forward", "turn")), List("numpy"),
      resetGlobals = false, captureStdout = false, captureStderr = true)
    assertEquals(read[RunPythonRequest](write(request)), request)
    assertEquals(readBinary[RunPythonRequest](writeBinary(request)), request)
    assertEquals(read[RunPythonRequest]("{\"code\":\"6*7\"}"), RunPythonRequest("6*7"))
  }
  test("Python responses preserve output, structured results and ordered callback arguments") {
    val response = RunPythonResult(ujson.Obj("answer" -> 42), "hello\n", "warning\n",
      List(PythonCallback("turtle", "forward", List(ujson.Num(10))),
        PythonCallback("turtle", "write", List(ujson.Str("学校")))))
    assertEquals(read[RunPythonResult](write(response)), response)
    assertEquals(readBinary[RunPythonResult](writeBinary(response)), response)
    assert(supportedCommands.contains(runPython.name))
  }

  test("Java evidence requests preserve exact source invocation values and execution bounds") {
    val values = Vector(Evidence.Scalar.Int32(Int.MinValue), Evidence.Scalar.Int32(Int.MaxValue),
      Evidence.Scalar.Bool(false), Evidence.Scalar.Bool(true), Evidence.Scalar.Float64("0000000000000000"),
      Evidence.Scalar.Float64("8000000000000000"), Evidence.Scalar.Float64("0000000000000001"),
      Evidence.Scalar.Float64("7fefffffffffffff"), Evidence.Scalar.Float64("7ff0000000000000"),
      Evidence.Scalar.Float64("fff0000000000000"), Evidence.Scalar.Float64("nan"))
    val request = JavaExecutionEvidenceRequest("/* 😀 Grüße */\r\nclass Drawing {}\r", Some("drawShape"), values,
      T.Limits(maxSteps = 77, maxCommands = 4, maxCallDepth = 3, maxBlockDepth = 5), J.Budget(97, 211))
    assertEquals(writeJs(request).apply("method"), ujson.Str("drawShape"))
    List(read[JavaExecutionEvidenceRequest](write(request)),
      readBinary[JavaExecutionEvidenceRequest](writeBinary(request))).foreach { restored =>
      assertEquals(restored, request)
      assertEquals(restored.arguments(5), Evidence.Scalar.Float64("8000000000000000"))
      assertEquals(restored.arguments.last, Evidence.Scalar.Float64("nan"))
    }
    val minimal = ujson.Obj("source" -> request.source)
    assertEquals(read[JavaExecutionEvidenceRequest](minimal), JavaExecutionEvidenceRequest(request.source))
    assertEquals(readBinary[JavaExecutionEvidenceRequest](writeBinary(minimal)), JavaExecutionEvidenceRequest(request.source))
    assert(supportedCommands.contains(javaExecutionEvidence.name))
  }

  test("Java evidence snapshots preserve outcome source occurrences and tagged IEEE values") {
    val values = Vector(Evidence.Scalar.Int32(-1), Evidence.Scalar.Bool(true),
      Evidence.Scalar.Float64("8000000000000000"), Evidence.Scalar.Float64("0000000000000001"),
      Evidence.Scalar.Float64("7fefffffffffffff"), Evidence.Scalar.Float64("nan"))
    val expressions = values.zipWithIndex.map { (value, index) =>
      Evidence.LocatedExpression(index % 3, "drawShape", index * 2, index * 2 + 1, value)
    }
    Evidence.Outcome.values.foreach { outcome =>
      val snapshot = Evidence.Snapshot(outcome, 77, 4, expressions)
      assertEquals(read[Evidence.Snapshot](write(snapshot)), snapshot)
      assertEquals(readBinary[Evidence.Snapshot](writeBinary(snapshot)), snapshot)
    }
  }

  test("Java evidence request decoding rejects invalid trace budgets") {
    Vector(("maxEvents", -1), ("maxEvents", 10001), ("maxDependencies", -1), ("maxDependencies", 100001)).foreach { (field, value) =>
      val payload = ujson.Obj("source" -> "class Drawing {}", "budget" -> ujson.Obj(field -> value))
      intercept[Exception](read[JavaExecutionEvidenceRequest](payload))
      intercept[Exception](readBinary[JavaExecutionEvidenceRequest](writeBinary(payload)))
    }
  }

  test("Java evidence request decoding never coerces numeric fields or scalar tags") {
    def reject(payload: ujson.Value): Unit = {
      intercept[Exception](read[JavaExecutionEvidenceRequest](payload))
      intercept[Exception](read[JavaExecutionEvidenceRequest](write(payload)))
      intercept[Exception](readBinary[JavaExecutionEvidenceRequest](writeBinary(payload)))
    }
    val invalidIntegers = Vector[ujson.Value](ujson.Num(0.5), ujson.Num(-1.9),
      ujson.Num(2147483648.0), ujson.Num(-2147483649.0), ujson.Num(4294967297.0),
      ujson.Num(1e100), ujson.Num(Double.NaN), ujson.Num(Double.PositiveInfinity),
      ujson.Str("1"), ujson.Bool(true), ujson.Null)
    val integerFields = Vector("limits" -> List("maxSteps", "maxCommands", "maxCallDepth", "maxBlockDepth"),
      "budget" -> List("maxEvents", "maxDependencies"))
    invalidIntegers.foreach { value =>
      reject(ujson.Obj("source" -> "class Drawing {}", "method" -> "draw",
        "arguments" -> ujson.Arr(ujson.Obj("$type" -> "Int32", "value" -> value))))
      integerFields.foreach { (group, fields) =>
        fields.foreach(field => reject(ujson.Obj("source" -> "class Drawing {}", group -> ujson.Obj(field -> value))))
      }
    }
    Vector(ujson.Obj("$type" -> "Bool", "value" -> 1), ujson.Obj("$type" -> "Bool", "value" -> "true"),
      ujson.Obj("$type" -> "Float64", "bits" -> 123)).foreach { argument =>
      reject(ujson.Obj("source" -> "class Drawing {}", "method" -> "draw", "arguments" -> ujson.Arr(argument)))
    }
    Vector(
      """{"source":"class Drawing {}","method":"draw","arguments":[{"$type":"Int32","value":18446744073709551617}]}""",
      """{"source":"class Drawing {}","limits":{"maxSteps":4294967297}}""",
      """{"source":"class Drawing {}","budget":{"maxEvents":4294967297}}"""
    ).foreach { raw =>
      intercept[Exception](read[JavaExecutionEvidenceRequest](raw))
      reject(ujson.read(raw))
    }
  }
}
