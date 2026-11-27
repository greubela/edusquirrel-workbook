package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.clients.{ExecutionClient, RemoteExecutionConfig, WebWorkerExecutionClient}
import it.evadid.facades.Workers
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.control.startup.ClientRuntimeConfig


case class HomepageDefaults(fullInfo: FullInfo) {

  lazy val defaultLanguagesAvailable: Seq[HumanLanguage] = List(German, English)


  /** Claim the Scala.js startup pool rather than starting a second worker. */
  lazy val workerExecuter: WebWorkerExecutionClient =
    WebWorkerExecutionClient.fromWorker(Workers.takeOrCreate(ClientRuntimeConfig.workerUrl))

  /** Commands wait for the runtime configuration, which may name another backend (see `ClientRuntimeConfig.backend`). */
  def backendExecutorWithCredentials(signedToken: Option[SignedToken]): ExecutionClient =
    RemoteExecutionConfig.deferredExecutor(ClientRuntimeConfig.backend, signedToken)

  def backendExecutor: ExecutionClient =
    backendExecutorWithCredentials(fullInfo.homepageInfoNow().userInfo.flatMap(_.credentials.signedBackendToken))


}
