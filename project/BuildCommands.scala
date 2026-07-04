import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbt.*
import sbt.Keys.*
import sbtassembly.AssemblyPlugin.autoImport.*

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object BuildCommands {
  sealed trait BuildMode {
    def release: Boolean
  }

  object BuildMode {
    case object Dev extends BuildMode {
      val release = false
    }

    case object Deploy extends BuildMode {
      val release = true
    }
  }

  sealed trait ArtifactModule {
    def moduleName: String
    def projectRef: Project
    def distFileName: String
  }

  final case class JsArtifactModule(
      moduleName: String,
      projectRef: Project,
      distFileName: String
  ) extends ArtifactModule

  final case class JvmArtifactModule(
      moduleName: String,
      projectRef: Project,
      distFileName: String
  ) extends ArtifactModule

  final case class ArtifactBuildArchitecture(
      client: JsArtifactModule,
      worker: JsArtifactModule,
      backend: JvmArtifactModule
  ) {
    val modules: Seq[ArtifactModule] = Seq(client, worker, backend)
  }

  lazy val buildClientDev = taskKey[Unit]("Build the client with fastLinkJS and copy it to artifacts/newest")
  lazy val buildClientDeploy = taskKey[Unit]("Build the client with fullLinkJS and copy it to newest, stable, and history")
  lazy val buildWorkerDev = taskKey[Unit]("Build the web worker with fastLinkJS and copy it to artifacts/newest")
  lazy val buildWorkerDeploy = taskKey[Unit]("Build the web worker with fullLinkJS and copy it to newest, stable, and history")
  lazy val buildBackendDev = taskKey[Unit]("Build the backend assembly and copy it to artifacts/newest")
  lazy val buildBackendDeploy = taskKey[Unit]("Build the backend assembly and copy it to newest, stable, and history")
  lazy val buildAllDev = taskKey[Unit]("Build all modules for development")
  lazy val buildAllDeploy = taskKey[Unit]("Build all modules for deployment")

  lazy val buildClientFast = taskKey[Unit]("Deprecated alias for buildClientDev")
  lazy val buildWorkerFast = taskKey[Unit]("Deprecated alias for buildWorkerDev")
  lazy val buildServerFast = taskKey[Unit]("Deprecated alias for buildBackendDev")
  lazy val deployAll = taskKey[Unit]("Deprecated alias for buildAllDeploy")

  private val TimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")

  def buildCommandSettings(architecture: ArtifactBuildArchitecture): Seq[Setting[_]] = Seq(
    buildClientDev := buildJsModuleDev(architecture.client).value,
    buildClientDeploy := buildJsModuleDeploy(architecture.client).value,
    buildWorkerDev := buildJsModuleDev(architecture.worker).value,
    buildWorkerDeploy := buildJsModuleDeploy(architecture.worker).value,
    buildBackendDev := buildJvmModule(architecture.backend, BuildMode.Dev).value,
    buildBackendDeploy := buildJvmModule(architecture.backend, BuildMode.Deploy).value,
    buildAllDev := Def.sequential(buildClientDev, buildWorkerDev, buildBackendDev).value,
    buildAllDeploy := Def.sequential(buildClientDeploy, buildWorkerDeploy, buildBackendDeploy).value,
    buildClientFast := buildClientDev.value,
    buildWorkerFast := buildWorkerDev.value,
    buildServerFast := buildBackendDev.value,
    deployAll := buildAllDeploy.value
  )

  private def buildJsModuleDev(module: JsArtifactModule): Def.Initialize[Task[Unit]] = Def.sequential(
    module.projectRef / Compile / fastLinkJS,
    Def.taskDyn {
      val output = (module.projectRef / Compile / fastLinkJS / scalaJSLinkedFile).value.data
      publishArtifact(output, module, BuildMode.Dev)
    }
  )

  private def buildJsModuleDeploy(module: JsArtifactModule): Def.Initialize[Task[Unit]] = Def.sequential(
    module.projectRef / Compile / fullLinkJS,
    Def.taskDyn {
      val output = (module.projectRef / Compile / fullLinkJS / scalaJSLinkedFile).value.data
      publishArtifact(output, module, BuildMode.Deploy)
    }
  )

  private def buildJvmModule(module: JvmArtifactModule, mode: BuildMode): Def.Initialize[Task[Unit]] = Def.taskDyn {
    val assemblyJar = (module.projectRef / assembly).value
    publishArtifact(assemblyJar, module, mode)
  }

  private def ensureDirectories(root: File): Unit = {
    IO.createDirectory(root / "artifacts")
    IO.createDirectory(root / "artifacts" / "newest")
    IO.createDirectory(root / "artifacts" / "stable")
    IO.createDirectory(root / "artifacts" / "history")
  }

  private def sha256(file: File): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = IO.readBytes(file)
    digest.digest(bytes).map("%02x".format(_)).mkString
  }

  private def historyFileName(moduleName: String, distFileName: String, hash: String): String = {
    val timestamp = LocalDateTime.now().format(TimestampFormatter)
    val shortHash = hash.take(12)
    s"$timestamp-$moduleName-$shortHash-$distFileName"
  }

  private def newestHistoryFileFor(historyDir: File, distFileName: String): Option[File] =
    if (historyDir.exists) {
      IO.listFiles(historyDir)
        .filter(file => file.isFile && file.getName.endsWith(s"-$distFileName"))
        .sortBy(file => (file.lastModified, file.getName))
        .lastOption
    } else {
      None
    }

  private def newestHistoryFileHasHash(historyDir: File, distFileName: String, hash: String): Boolean = {
    val shortHash = hash.take(12)
    newestHistoryFileFor(historyDir, distFileName).exists(_.getName.contains(s"-$shortHash-"))
  }

  private def publishArtifact(
      sourceFile: File,
      module: ArtifactModule,
      mode: BuildMode
  ): Def.Initialize[Task[Unit]] = Def.task {
    val log = streams.value.log
    val root = (ThisBuild / baseDirectory).value
    val artifactHash = sha256(sourceFile)
    val historyDir = root / "artifacts" / "history" / module.moduleName

    ensureDirectories(root)
    IO.createDirectory(historyDir)

    IO.copyFile(sourceFile, root / "artifacts" / "newest" / module.distFileName)
    log.info(s"Copied current ${module.moduleName} build to artifacts/newest/${module.distFileName}")

    if (mode.release) {
      IO.copyFile(sourceFile, root / "artifacts" / "stable" / module.distFileName)
      if (newestHistoryFileHasHash(historyDir, module.distFileName, artifactHash)) {
        log.info(s"Skipped history copy for ${module.moduleName} because newest history entry already has hash $artifactHash")
      } else {
        val historyFile = historyDir / historyFileName(module.moduleName, module.distFileName, artifactHash)
        IO.copyFile(sourceFile, historyFile)
        log.info(s"Copied ${module.moduleName} release to ${historyFile.relativeTo(root).getOrElse(historyFile)}")
      }
      log.info(s"Copied release ${module.moduleName} build to artifacts/stable/${module.distFileName}")
    }
  }
}
