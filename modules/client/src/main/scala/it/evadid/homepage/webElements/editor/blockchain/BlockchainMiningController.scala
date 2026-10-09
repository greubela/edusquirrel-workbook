package it.evadid.homepage.webElements.editor.blockchain

import com.raquo.laminar.api.L.*
import it.evadid.workbook.model.blockchain.TeachingChain

/** Each scheduled chunk checks its snapshot, preventing cancelled or stale jobs from writing. */
class BlockchainMiningController(state: Var[TeachingChain], zeros: Int,
    schedule: (() => Unit) => (() => Unit), batchSize: Int = 50, budget: Int = 5000) {
  require(batchSize >= 1 && batchSize <= 1000 && budget >= 1)
  TeachingChain.validateDifficulty(zeros)
  val running = Var(false)
  val attempts = Var(0)
  val message = Var("miningIdle")
  private var generation = 0
  private var cancelScheduled: () => Unit = () => ()
  def cancel(): Unit = {
    generation += 1
    cancelScheduled()
    cancelScheduled = () => ()
    if running.now() then message.set("miningStopped")
    running.set(false)
  }
  def start(index: Int): Unit = {
    require(index >= 0 && index < state.now().blocks.size)
    cancel()
    val job = generation
    attempts.set(0)
    running.set(true)
    message.set("miningRunning")
    def enqueue(snapshot: TeachingChain, startNonce: Int): Unit = {
      cancelScheduled = schedule(() => {
        if generation == job && running.now() then {
          if state.now() != snapshot then {
            running.set(false)
            message.set("miningChanged")
          } else {
            val result = snapshot.mine(index, zeros, startNonce, math.min(batchSize, budget - attempts.now()))
            val updated = snapshot.update(index, snapshot.blocks(index).copy(nonce = result.nonce))
            state.set(updated)
            attempts.update(_ + result.attempts)
            if result.found then { running.set(false); message.set("miningFound") }
            else if result.nextNonce.isEmpty then { running.set(false); message.set("miningExhausted") }
            else if attempts.now() >= budget then { running.set(false); message.set("miningBudget") }
            else enqueue(updated, result.nextNonce.get)
          }
        }
      })
    }
    enqueue(state.now(), state.now().blocks(index).nonce)
  }
}
