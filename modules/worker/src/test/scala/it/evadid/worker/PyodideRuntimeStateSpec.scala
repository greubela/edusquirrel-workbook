package it.evadid.worker

import it.evadid.distribution.commandTypes.WorkerCommands.PythonCallback
import it.evadid.facades.PyodideRuntime
import it.evadid.worker.PyodideRuntimeState.*
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.util.{Failure, Success}

class PyodideRuntimeStateSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val runtime = js.Object().asInstanceOf[PyodideRuntime]
  private val ready = PyodideRuntimeState.initial.startLoading(Future.successful(runtime)).finishLoading(Success(runtime))

  test("loading becomes ready with an empty session, and a failed load can be retried") {
    assertEquals(ready.interpreter, Interpreter.Ready(runtime, Session.empty))
    val failed = PyodideRuntimeState.initial.startLoading(Future.never).finishLoading(Failure(RuntimeException("offline")))
    assertEquals(failed.interpreter, Interpreter.NotStarted)
    assert(failed.startLoading(Future.never).interpreter.isInstanceOf[Interpreter.Loading])
  }

  test("illegal transitions are rejected") {
    intercept[IllegalStateException](ready.startLoading(Future.never))
    intercept[IllegalStateException](PyodideRuntimeState.initial.finishLoading(Success(runtime)))
    intercept[IllegalStateException](PyodideRuntimeState.initial.withModule("turtle"))
  }

  test("session transitions keep the namespace and replace the module set") {
    val namespace = js.Dynamic.literal()
    val session = ready.withNamespace(namespace).withModule("a").withModule("b").session
    assertEquals(session, Session(Some(namespace), Set("a", "b")))
    assertEquals(ready.withNamespace(namespace).withModule("a").withoutModules.session, Session(Some(namespace), Set.empty))
  }

  test("jobs start only after earlier jobs settle, including failed ones") {
    val gate = Promise[Unit]()
    val (afterFirst, first) = PyodideRuntimeState.initial.enqueue(() => gate.future.flatMap(_ => Future.failed[Int](RuntimeException("boom"))))
    val started = Promise[Unit]()
    val (_, second) = afterFirst.enqueue { () =>
      started.success(())
      Future.successful(2)
    }
    assert(!started.isCompleted)
    gate.success(())
    for {
      firstResult <- first.transform(Success(_))
      secondResult <- second
    } yield {
      assert(firstResult.isFailure)
      assertEquals(secondResult, 2)
    }
  }

  test("job output accumulates in order") {
    val callback = PythonCallback("turtle", "forward", List(ujson.Num(1)))
    val output = JobOutput.empty.withStdout("a").withStderr("e").withStdout("b").withCallback(callback)
    assertEquals(output, JobOutput(Vector("a", "b"), Vector("e"), Vector(callback)))
  }
}
