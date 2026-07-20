package it.evadid.distribution.clients

case class RemoteExecutionConfig(backendDomain: String, port: Int) {
  lazy val executor: ExecutionClient = JsRemoteExecutionClient(backendDomain, port)
}
