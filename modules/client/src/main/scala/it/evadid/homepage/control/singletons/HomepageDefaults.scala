package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.clients.{ExecutionClient, RemoteExecutionConfig, WebWorkerExecutionClient}
import it.evadid.facades.Workers
import it.evadid.homepage.control.startup.ClientRuntimeConfig


/** Page-independent defaults: languages, the backend address and the shared worker. */
case class HomepageDefaults() {

  lazy val defaultLanguagesAvailable: Seq[HumanLanguage] = List(German, English)


  private[control] lazy val defaultBackend: RemoteExecutionConfig = RemoteExecutionConfig("ypcgzj23.trafficplex.cloud", 443)

  /** Claim the Scala.js startup pool rather than starting a second worker. */
  lazy val workerExecuter: WebWorkerExecutionClient =
    WebWorkerExecutionClient.fromWorker(Workers.takeOrCreate(ClientRuntimeConfig.workerUrl))

  def backendExecutorWithCredentials(signedToken: Option[SignedToken]): ExecutionClient =
    defaultBackend.executor(signedToken)
}
