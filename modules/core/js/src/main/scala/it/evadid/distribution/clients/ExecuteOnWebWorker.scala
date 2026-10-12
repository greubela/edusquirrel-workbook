package it.evadid.distribution.clients

/** Compatibility entry point for the original worker-client name. */
object ExecuteOnWebWorker {
  def apply(workerScriptPath: String): WebWorkerExecutionClient = WebWorkerExecutionClient(workerScriptPath)
}
