import sbt.Project

import BuildCommands.*

object BuildArchitecture {
  def workbookArtifactArchitecture(
      clientProject: Project,
      workerProject: Project,
      backendProject: Project
  ): ArtifactBuildArchitecture = ArtifactBuildArchitecture(
    client = JsArtifactModule("client", clientProject, "client.js"),
    worker = JsArtifactModule("worker", workerProject, "backend-worker.js"),
    backend = JvmArtifactModule("backend", backendProject, "server.jar")
  )
}
