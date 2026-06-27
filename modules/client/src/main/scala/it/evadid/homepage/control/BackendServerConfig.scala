package it.evadid.homepage.control

import it.evadid.distribution.clients.*

object BackendServerConfig:
  lazy val executor: JsRemoteExecutionClient = JsRemoteExecutionClient("ypcgzj23.trafficplex.cloud", 443)
