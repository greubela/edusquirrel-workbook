package it.evadid.distribution.commandTypes

import it.evadid.distribution.commandTypes.WorkerCommands.*
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
}
