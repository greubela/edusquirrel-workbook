package it.evadid.homepage.webElements.facades

import com.raquo.airstream.state.Var
import it.evadid.facades.{EvaExternalDependency, ExternalDependency, Pyodide}
import it.evadid.homepage.webElements.editor.code.EditorDomSuite

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class ExternalDependenciesSpec extends EditorDomSuite {
  test("preloading never allocates an instance; creation waits for readiness") {
    val loaded = Var(false)
    val creations = Var(0)
    val gate = Promise[Unit]()
    val dependency: EvaExternalDependency[Int] = new ExternalDependency(
      () => gate.future.map { _ => loaded.set(true) }, () => loaded.now(),
      () => { creations.update(_ + 1); creations.now() })
    val preloaded = dependency.preload()
    val created = dependency.createInstance()
    assertEquals(creations.now(), 0)
    assert(!created.isCompleted)
    gate.success(())
    preloaded.flatMap(_ => created).map { instance =>
      assertEquals(instance, 1)
      assertEquals(creations.now(), 1)
    }
  }

  test("ready dependencies create independent consumer-owned handles") {
    val dependency = new ExternalDependency(() => Future.unit, () => true, () => new Object)
    for {
      a <- dependency.createInstance()
      b <- dependency.createInstance()
    } yield assert(a ne b)
  }

  test("a failed preload cannot allocate and a later call can retry") {
    val available = Var(false)
    val failed = Var(true)
    val creations = Var(0)
    val dependency = new ExternalDependency(
      () => if failed.now() then Future.failed(IllegalStateException("offline"))
        else { available.set(true); Future.unit },
      () => available.now(), () => { creations.update(_ + 1); creations.now() })
    dependency.createInstance().failed.flatMap { error =>
      assertEquals(error.getMessage, "offline")
      assertEquals(creations.now(), 0)
      failed.set(false)
      dependency.createInstance().map(value => assertEquals(value, 1))
    }
  }

  test("synchronous creation requires readiness; constructor failures are failed futures") {
    val unavailable = new ExternalDependency(() => Future.unit, () => false, () => new Object)
    intercept[IllegalArgumentException](unavailable.createLoadedInstance())
    val broken = new ExternalDependency[Object](() => Future.unit, () => true,
      () => throw IllegalStateException("constructor failed"))
    broken.createInstance().failed.map(error => assertEquals(error.getMessage, "constructor failed"))
  }

  test("Pyodide preload creates no interpreter and each instance is fresh") {
    val global = js.Dynamic.global.globalThis
    val previous = global.selectDynamic("loadPyodide")
    val creations = Var(0)
    global.updateDynamic("loadPyodide")((() => {
      creations.update(_ + 1)
      js.Promise.resolve(js.Dynamic.literal(globals = js.Dynamic.literal()))
    }): js.Function0[js.Promise[js.Dynamic]])
    Pyodide.preload().flatMap { _ =>
      assertEquals(creations.now(), 0)
      for {
        a <- Pyodide.createInstance()
        b <- Pyodide.createInstance()
      } yield {
        assert(a ne b)
        assertEquals(creations.now(), 2)
      }
    }.andThen { case _ => global.updateDynamic("loadPyodide")(previous) }
  }
}
