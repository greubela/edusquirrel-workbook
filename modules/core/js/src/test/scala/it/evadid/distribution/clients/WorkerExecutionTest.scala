package it.evadid.distribution.clients

import munit.FunSuite

class WorkerExecutionTest extends FunSuite {
  test("worker execution adapter is currently disabled") {
    // ExecuteOnWebWorker is commented out in main sources; keep this JS test compiling
    // until the adapter is restored.
    assert(true)
  }
}
