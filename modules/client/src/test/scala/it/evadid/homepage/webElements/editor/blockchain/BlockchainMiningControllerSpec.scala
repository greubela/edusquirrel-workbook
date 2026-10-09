package it.evadid.homepage.webElements.editor.blockchain

import com.raquo.airstream.state.Var
import it.evadid.workbook.model.blockchain.*
import munit.FunSuite
import scala.collection.mutable.Queue

class BlockchainMiningControllerSpec extends FunSuite {
  private class Scheduler {
    val pending = Queue.empty[() => Unit]
    // Deliberately allow cancelled callbacks to run to verify the generation guard.
    def schedule(callback: () => Unit): () => Unit = { pending.enqueue(callback); () => () }
    def tick(): Unit = pending.dequeue()()
    def drain(): Unit = {
      var ticks = 0
      while pending.nonEmpty && ticks < 100 do { tick(); ticks += 1 }
      assert(pending.isEmpty, "mining must finish within its attempt budget")
    }
  }
  test("mining yields between chunks and saves a discovered nonce") {
    val scheduler = new Scheduler
    val state = Var(TeachingChain(List(TeachingBlock("first"))))
    val c = new BlockchainMiningController(state, 1, scheduler.schedule, batchSize = 1, budget = 5)
    c.start(0)
    assertEquals(c.attempts.now(), 0)
    scheduler.tick()
    assert(c.running.now())
    assertEquals(c.attempts.now(), 1)
    scheduler.tick()
    assert(!c.running.now())
    assertEquals(c.message.now(), "miningFound")
    assertEquals(c.attempts.now(), 2)
    assertEquals(state.now().blocks.head.nonce, 1)
  }
  test("failed searches stop at the budget even when the last chunk is smaller") {
    val scheduler = new Scheduler
    val state = Var(TeachingChain(List(TeachingBlock("first"))))
    val c = new BlockchainMiningController(state, 4, scheduler.schedule, batchSize = 2, budget = 5)
    c.start(0); scheduler.drain()
    assertEquals(c.attempts.now(), 5)
    assertEquals(c.message.now(), "miningBudget")
    assertEquals(state.now().blocks.head.nonce, 4)
    assert(!c.running.now())
  }
  test("stop and fullscreen close prevent queued work from writing") {
    val scheduler = new Scheduler
    val initial = TeachingChain(List(TeachingBlock("first")))
    val state = Var(initial)
    val c = new BlockchainMiningController(state, 1, scheduler.schedule)
    c.start(0); c.cancel(); scheduler.drain()
    assertEquals(state.now(), initial)
    assertEquals(c.attempts.now(), 0)
    assertEquals(c.message.now(), "miningStopped")
  }
  test("external edits or reset invalidate a queued snapshot") {
    val scheduler = new Scheduler
    val state = Var(TeachingChain(List(TeachingBlock("first"))))
    val c = new BlockchainMiningController(state, 1, scheduler.schedule)
    c.start(0)
    val changed = TeachingChain(List(TeachingBlock("edited", 9)))
    state.set(changed); scheduler.drain()
    assertEquals(state.now(), changed)
    assertEquals(c.message.now(), "miningChanged")
    assertEquals(c.attempts.now(), 0)
  }
  test("starting another job invalidates the previous job") {
    val scheduler = new Scheduler
    val state = Var(TeachingChain(List(TeachingBlock("first"), TeachingBlock("second"))))
    val c = new BlockchainMiningController(state, 1, scheduler.schedule)
    c.start(0); c.start(1); scheduler.drain()
    assertEquals(state.now().blocks.head.nonce, 0)
    assertEquals(c.message.now(), "miningFound")
    assert(state.now().proofs(1)(1))
  }
  test("nonce exhaustion stops without wrapping or enqueuing another chunk") {
    val scheduler = new Scheduler
    val state = Var(TeachingChain(List(TeachingBlock("first", Int.MaxValue))))
    val c = new BlockchainMiningController(state, 4, scheduler.schedule)
    c.start(0); scheduler.drain()
    assertEquals(c.attempts.now(), 1)
    assertEquals(state.now().blocks.head.nonce, Int.MaxValue)
    assert(!c.running.now())
  }
}
