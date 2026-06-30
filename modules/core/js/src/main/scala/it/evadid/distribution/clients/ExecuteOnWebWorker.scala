package it.evadid.distribution.clients

/*
class ExecuteOnWebWorker private[clients](worker: WorkerLike) extends ExecutionClient {

  private val pending = mutable.Map.empty[String, Promise[ExecutionInfo]]

  worker.onmessage = (event: dom.MessageEvent) => {
    event.data match {
      case text: String =>
        val payload = read[Map[String, String]](text)
        val requestId = payload.getOrElse("requestId",
          throw new IllegalStateException("Worker response is missing 'requestId' field")
        )
        val executionInfoJson = payload.getOrElse("executionInfo",
          throw new IllegalStateException("Worker response is missing 'executionInfo' field")
        )
        pending.remove(requestId).foreach(_.success(ExecutionInfo.fromJson(executionInfoJson)))
      case _ =>
    }
  }

  worker.onerror = (event: dom.ErrorEvent) =>
    val exception = new RuntimeException(s"Worker error at ${event.filename}:${event.lineno}: ${event.message}")
    pending.values.foreach(_.tryFailure(exception))
    pending.clear()

  override def handleExecution(executionCommand: ExecutionCommand, logger: Logger): AsyncData[Nothing, ExecutionInfo] = {
    val requestId = java.util.UUID.randomUUID().toString
    val promise = Promise[ExecutionInfo]()
    pending.put(requestId, promise)
    val payload = write(Map(
      "requestId" -> requestId,
      "command" -> executionCommand.toJson
    ))
    worker.postMessage(payload)
    AsyncData.forFuture(promise.future)
  }

  override def canExecuteCommand(executionCommand: ExecutionCommand): Boolean = true
}

object ExecuteOnWebWorker {

  def apply(workerScriptPath: String): ExecuteOnWebWorker = new ExecuteOnWebWorker(new DomWorkerAdapter(workerScriptPath))

  trait WorkerLike {
    def postMessage(message: String): Unit

    def onmessage: dom.MessageEvent => Unit

    def onmessage_=(handler: dom.MessageEvent => Unit): Unit

    def onerror: dom.ErrorEvent => Any

    def onerror_=(handler: dom.ErrorEvent => Any): Unit
  }

  private class DomWorkerAdapter(path: String) extends WorkerLike {
    private val worker = new dom.Worker(path)
    private var messageHandler: dom.MessageEvent => Unit = _ => ()
    private var errorHandler: dom.ErrorEvent => Any = _ => ()

    override def postMessage(message: String): Unit = worker.postMessage(message)

    override def onmessage: dom.MessageEvent => Unit = messageHandler

    override def onmessage_=(handler: dom.MessageEvent => Unit): Unit = messageHandler = handler

    override def onerror: dom.ErrorEvent => Any = errorHandler

    override def onerror_=(handler: dom.ErrorEvent => Any): Unit = errorHandler = handler

    worker.onmessage = (event: dom.MessageEvent) => onmessage(event)
    worker.onerror = (event: dom.ErrorEvent) => onerror(event)
  }
}
*/