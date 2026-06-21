package it.evadid.evacuation.core.utility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js

class BufferedExecution(execute: () => Unit, timeBetweenExecution: Long = 200, minDelay: Long = 15) {

  private var currentlyRunning: Boolean = false

  private var lastRequestExecuted: Long = 0
  private var lastRequest: Long = 0

  private var requests: Long = 0
  private var executions: Long = 0

  def requestExecution(): Unit = {
    val currentTime = System.currentTimeMillis()
    // System.out.println("REQUEST " + new Date() + "(currentTime: " + currentTime + ")")
    lastRequest = currentTime
    requests += 1

    if (!currentlyRunning) {
      currentlyRunning = true
      Future {
        tryExecutionIn()
      }
    }

  }

  def tryExecutionIn(): Unit = {
    val timeSinceLastExecution = System.currentTimeMillis() - lastRequestExecuted
    val executionIn = timeBetweenExecution - timeSinceLastExecution
    if (executionIn < minDelay) {
      tryExecutionIn(minDelay)
    } else {
      tryExecutionIn(executionIn)
    }
  }

  def tryExecutionIn(milliseconds: Long): Unit = {
    if (milliseconds > 0) {
      val p = Promise[Unit]()
      js.timers.setTimeout(milliseconds.toDouble) {
        p.success(())
      }
      p.future.onComplete(_ => tryExecution())
    } else {
      tryExecution()
    }
  }

  private def tryExecution(): Unit = {
    if (lastRequest > lastRequestExecuted) {
      lastRequestExecuted = System.currentTimeMillis()

      execute()
      executions += 1

      val diff = (System.currentTimeMillis() - lastRequestExecuted)/1000.0

    //  println("FINISHED: " + new Date() + ". Executed: " + executions + "/" + requests + ", diff: " + diff + "s, current time: " + System.currentTimeMillis())

      if (lastRequest > lastRequestExecuted) {
        tryExecutionIn(minDelay)
      }

    }
    currentlyRunning = false

  }


}
