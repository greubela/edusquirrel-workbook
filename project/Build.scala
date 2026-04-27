import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbt.*
import sbt.Keys.{artifacts, *}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Build {


  def executeBuild(
                    server: Project,
                    client: Project,
                    copyToStable: Boolean = false
                  ): Def.Initialize[Task[Unit]] = Def.task {
    val log = streams.value.log

    val serverJar = (server / Compile / packageBin).value

    val fullReport = (client / Compile / fullLinkJS).value
    val fastReport = (client / Compile / fastLinkJS).value

    val clientFull =
      (client / Compile / fullLinkJS / scalaJSLinkedFile).value.data

    val clientFast =
      (client / Compile / fastLinkJS / scalaJSLinkedFile).value.data

    val root = (ThisBuild / baseDirectory).value
    val artifacts = root / "artifacts"

    val newest = artifacts / "newest"
    val historyClient = artifacts / "history" / "client"
    val historyServer = artifacts / "history" / "server"

    IO.createDirectory(newest)
    IO.createDirectory(historyClient)
    IO.createDirectory(historyServer)

    val timestamp =
      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

    IO.copyFile(serverJar, newest / "server.jar")
    IO.copyFile(clientFull, newest / "client.js")
    IO.copyFile(clientFast, newest / "client-fastOpt.js")

    IO.copyFile(serverJar, historyServer / s"$timestamp-server.jar")
    IO.copyFile(clientFull, historyClient / s"$timestamp-client-fullOpt.js")

    log.info(s"Copied newest artifacts to ${artifacts.getAbsolutePath}")

    if(copyToStable) {
      val stable = artifacts / "stable"
      val stableClient = artifacts / "history" / "client"
      val stableServer = artifacts / "history" / "server"

      IO.createDirectory(stable)
      IO.createDirectory(stableClient)
      IO.createDirectory(stableServer)

      IO.copyFile(serverJar, stable / "server.jar")
      IO.copyFile(clientFull, stable / "client.js")
      log.info(s"Copied stable artifacts to ${artifacts.getAbsolutePath}")
    }

  }
}