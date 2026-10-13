package it.evadid.homepage.control.startup

import munit.FunSuite
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class WarmWorkerSlotSpec extends FunSuite {
  test("warmup starts once and transfers ownership to an early editor") {
    var created = 0
    var initialized = 0
    val ready = Promise[Unit]()
    val slot = new WarmWorkerSlot[Int](() => { created += 1; created }, _ => { initialized += 1; ready.future }, _ => fail("an owned worker must not be closed by the pool"))
    val first = slot.preheat()
    assert(first eq slot.preheat())
    assertEquals(initialized, 1)
    assertEquals(slot.acquire(), 1)
    assertEquals(created, 1)
    assertEquals(slot.acquire(), 2, "separate sessions receive separate workers")
    ready.success(())
    first
  }

  test("failed warmup closes the unused worker and permits retry") {
    var created = 0
    var closed = List.empty[Int]
    val slot = new WarmWorkerSlot[Int](() => { created += 1; created }, worker =>
      if (worker == 1) Future.failed(new RuntimeException("CDN unavailable")) else Future.successful(()),
      worker => closed = closed :+ worker)
    slot.preheat().failed.flatMap { _ =>
      assertEquals(closed, List(1))
      slot.preheat().map { _ => assertEquals(slot.acquire(), 2) }
    }
  }
}
