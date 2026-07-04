import sbt.Project

import BuildCommands.*

object BuildArchitecture {
  def workbookArtifactArchitecture(
      clientProject: Project,
      workerProject: Project,
      serverProject: Project
  ): ArtifactBuildArchitecture = ArtifactBuildArchitecture(
    client = JsArtifactModule("client", clientProject, "client.js"),
    worker = JsArtifactModule("worker", workerProject, "worker.js"),
    server = JvmArtifactModule("server", serverProject, "server.jar")
  )
}
