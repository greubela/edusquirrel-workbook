import sbtassembly.AssemblyPlugin.autoImport.*
import Dependencies.*
import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbtcrossproject.CrossPlugin.autoImport.*
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*
import sbtcrossproject.CrossPlugin.autoImport.*

lazy val buildFast = taskKey[Unit]("Build client as fast as possible")
buildFast := Def.sequential(
  client / Compile / fastLinkJS,
  Def.taskDyn {
    val clientFastOutput = (client / Compile / fastLinkJS / scalaJSLinkedFile).value.data
    val root = (ThisBuild / baseDirectory).value
    Build.moveClientFiles(root, clientFastOutput, false, "fast", "client.js")
  }
).value



lazy val buildWorkerFast = taskKey[Unit]("Build worker as fast as possible")
buildWorkerFast := Def.sequential(
  worker / Compile / fastLinkJS,
  Def.taskDyn {
    val workerFastOutput = (worker / Compile / fastLinkJS / scalaJSLinkedFile).value.data
    val root = (ThisBuild / baseDirectory).value
    Build.moveClientFiles(root, workerFastOutput, false, "fast", "backend-worker.js")
  }
).value

lazy val deployAll = taskKey[Unit]("Builds and deploys server + client")
deployAll := {
  Def.sequential(
    client / Compile / fullLinkJS,
    worker / Compile / fullLinkJS,
    Def.taskDyn {
      val clientOutput = (client / Compile / fullLinkJS / scalaJSLinkedFile).value.data
      val workerOutput = (worker / Compile / fullLinkJS / scalaJSLinkedFile).value.data
      val base = (ThisBuild / baseDirectory).value
      Def.sequential(
        Build.moveClientFiles(base, clientOutput, true, "full", "client.js"),
        Build.moveClientFiles(base, workerOutput, true, "full", "backend-worker.js")
      )
    },
  ).value

  Build.buildServer(server, "server.jar", true).value
}


lazy val root = (project in file("."))
  .settings(Settings.globalSettings)
  .aggregate(server, client, worker)
  .settings(
    name := "edusquirrel-workbook",
    publish / skip := true
  )

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("./modules/core"))
  .settings(Settings.globalSettings)
  .settings(
    name := "core",
    libraryDependencies ++= coreDependencies.value
  )
  .jsSettings(
    libraryDependencies ++= jsDependencies.value
  )
  .jvmSettings(
    libraryDependencies ++= jvmDependencies.value
  )

lazy val server = (project in file("./modules/server"))
  .settings(Settings.globalSettings).settings(Settings.jvmSettings)
  .dependsOn(core.jvm)
  .settings(
    name := "server",
    Compile / mainClass := Some("it.evadid.server.BackendServer"),
    assembly / mainClass := Some("it.evadid.server.BackendServer"),
    assembly / assemblyJarName := "server.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "versions", _*) => MergeStrategy.discard
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case x => (assembly / assemblyMergeStrategy).value(x)
    },
    libraryDependencies ++= (coreDependencies.value ++ jvmDependencies.value)
  )

lazy val client = (project in file("./modules/client"))
  .settings(Settings.globalSettings).settings(Settings.jsSettings)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(core.js)
  .settings(
    name := "client",
    scalaJSUseMainModuleInitializer := true,
    Test / jsEnv := new NodeJSEnv(),
    libraryDependencies ++= (coreDependencies.value ++ jsDependencies.value)
  )

lazy val worker = (project in file("./modules/worker"))
  .settings(Settings.globalSettings).settings(Settings.jsSettings)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(core.js)
  .settings(
    name := "worker",
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("it.evadid.worker.BackendWorker"),
    Test / jsEnv := new NodeJSEnv(),
    libraryDependencies ++= (coreDependencies.value ++ jsDependencies.value)
  )


