package it.evadid.homepage.control

import it.evadid.distribution.clients.ExecuteOnRemoteServer

object BackendServerConfig:
  lazy val executor: ExecuteOnRemoteServer = ExecuteOnRemoteServer("ypcgzj23.trafficplex.cloud", 443)
