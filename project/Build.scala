import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbt.*
import sbtassembly.AssemblyPlugin.autoImport.*
import sbt.Keys.*

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Build {


  private def ensureDirectories(root: File): Unit = {
    IO.createDirectory(root / "artifacts")
    IO.createDirectory(root / "artifacts" / "newest")
    IO.createDirectory(root / "artifacts" / "stable")

    IO.createDirectory(root / "artifacts" / "history")
    IO.createDirectory(root / "artifacts" / "history" / "client")
    IO.createDirectory(root / "artifacts" / "history" / "server")
  }


  def moveClientFiles(root: File, outputFile: File, isRelease: Boolean, distTag: String, distFileName: String = "client.js"): Def.Initialize[Task[Unit]] = Def.task {

    val log = streams.value.log
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

    ensureDirectories(root)

    IO.copyFile(outputFile, (ThisBuild / baseDirectory).value / "artifacts" / "newest" / distFileName)
    log.info(s"Copied current version of $distFileName to newest!")

    if (isRelease) {
      IO.copyFile(outputFile, (ThisBuild / baseDirectory).value / "artifacts" / "stable" / distFileName)
      IO.copyFile(outputFile, (ThisBuild / baseDirectory).value / "artifacts" / "history" / "client" / s"$timestamp-$distTag-$distFileName")
      log.info(s"Copied release versions of $distFileName to stable!")
    }
  }


  def buildServer(
                   server: Project,
                   distFileName: String = "server.jar",
                   isRelease: Boolean = false
                 ): Def.Initialize[Task[Unit]] = Def.task {
    val log = streams.value.log
    val root = (ThisBuild / baseDirectory).value

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

    val serverJar = (server / assembly).value

    ensureDirectories(root)

    IO.copyFile(serverJar, root / "artifacts" / "newest" / distFileName)
    log.info(s"Copied current version of $distFileName to newest!")

    if (isRelease) {
      IO.copyFile(serverJar, root / "artifacts" / "stable" / distFileName)
      IO.copyFile(serverJar, root / "artifacts" / "history" / "server" / s"$timestamp-$distFileName")
      log.info(s"Copied release versions of $distFileName to stable!")
    }

  }
}