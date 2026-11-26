package it.evadid.distribution.clients

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken

case class RemoteExecutionConfig(backendDomain: String, port: Int) {

  def executor(token: Option[SignedToken]): ExecutionClient = {
    JsRemoteExecutionClient(backendDomain, port, token)
  }



}
