package it.evadid.vm.test

import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands.{RunPythonRequest, RunPythonResult, runPython}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import scala.concurrent.{ExecutionContext, Future}

/** Runs worker Python requests in a local CPython, as the Pyodide worker does: the request context becomes
  * the globals, and the value of a final expression is the result. Only the features the unit-test runner
  * uses are supported (no callbacks, packages or turtle logging).
  */
object CPythonRuntime {
  private val harness = """import ast, io, json, sys
payload = json.load(sys.stdin)
namespace = dict(payload["context"])
tree = ast.parse(payload["code"], "<worker>", "exec")
last = tree.body.pop() if tree.body and isinstance(tree.body[-1], ast.Expr) else None
output, stdout, stderr = sys.stdout, io.StringIO(), io.StringIO()
sys.stdout, sys.stderr = stdout, stderr
try:
    exec(compile(tree, "<worker>", "exec"), namespace)
    value = eval(compile(ast.Expression(last.value), "<worker>", "eval"), namespace) if last else None
finally:
    sys.stdout, sys.stderr = output, sys.__stderr__
output.write(json.dumps({"value": value, "stdout": stdout.getvalue(), "stderr": stderr.getvalue()}, allow_nan=False))
"""

  lazy val available: Boolean = scala.util.Try(new ProcessBuilder("python3", "--version").start().waitFor() == 0).getOrElse(false)

  def run(request: RunPythonRequest): RunPythonResult = {
    require(request.callbacks.isEmpty && request.packages.isEmpty && request.turtleCalls == it.evadid.vm.simulation.python.PythonTurtleCalls.Native, "Unsupported worker request")
    val script = Files.createTempFile("worker-harness", ".py")
    try {
      Files.writeString(script, harness)
      val process = new ProcessBuilder("python3", "-I", script.toString).redirectError(ProcessBuilder.Redirect.DISCARD).start()
      val input = ujson.write(ujson.Obj("code" -> request.code, "context" -> ujson.Obj.from(request.context)))
      process.getOutputStream.write(input.getBytes(UTF_8))
      process.getOutputStream.close()
      val output = new String(process.getInputStream.readAllBytes(), UTF_8)
      require(process.waitFor() == 0, "Python execution failed")
      val reply = ujson.read(output)
      RunPythonResult(reply("value"), if request.captureStdout then reply("stdout").str else "",
        if request.captureStderr then reply("stderr").str else "", Nil)
    } finally Files.deleteIfExists(script)
  }

  def client(using ExecutionContext): ExecutionClient = runPython.toLocalExecutionClient((request, _) => Future(run(request)))
}
