import org.scalajs.jsenv.nodejs.NodeJSEnv
import Dependencies.*
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import BuildArchitecture.*
import BuildCommands.*

lazy val root = (project in file("."))
  .settings(Settings.globalSettings)
  .aggregate(server, client, worker, desktop)
  .settings(
    name := "edusquirrel-workbook",
    publish / skip := true
  )
  .settings(buildCommandSettings(workbookArtifactArchitecture(client, worker, server)))

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

// Desktop adapters depend on core, while the server depends only on core.
lazy val desktop = (project in file("./modules/desktop"))
  .settings(Settings.globalSettings)
  .dependsOn(core.jvm)
  .settings(
    name := "desktop",
    publish / skip := true,
    libraryDependencies ++= desktopDependencies
  )

lazy val server = (project in file("./modules/server"))
  .settings(Settings.globalSettings).settings(Settings.jvmSettings)
  .dependsOn(core.jvm)
  .settings(
    name := "server",
    Compile / mainClass := Some("it.evadid.server.BackendServer"),
    assembly / mainClass := Some("it.evadid.server.BackendServer"),
    assembly / assemblyJarName := "server.jar",
    assembly / packageOptions += Package.ManifestAttributes("Multi-Release" -> "true"),
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class") => MergeStrategy.discard
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case PathList("google", "protobuf", file) if file.endsWith(".proto") => MergeStrategy.first

      case PathList("META-INF", "versions", _, "module-info.class") => MergeStrategy.discard
      case x => (assembly / assemblyMergeStrategy).value(x)

    },
    // Dependencies exported by core.jvm are already on this project's classpath.
    libraryDependencies ++= serverDependencies.value ++ Seq(
      "com.mysql" % "mysql-connector-j" % "26.10.0",
      "org.eclipse.angus" % "jakarta.mail" % "2.0.5",
      "org.slf4j" % "slf4j-simple" % "2.0.20" % Runtime,
      "org.scalameta" %% "munit" % "1.3.6" % Test
    )
  )

lazy val client = (project in file("./modules/client"))
  .settings(Settings.globalSettings).settings(Settings.jsSettings)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(core.js)
  .settings(
    name := "client",
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("mainApp"),
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.6" % Test,
    Test / jsEnv := new NodeJSEnv()
  )

lazy val worker = (project in file("./modules/worker"))
  .settings(Settings.globalSettings).settings(Settings.jsSettings)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(core.js)
  .settings(
    name := "worker",
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("it.evadid.worker.WebWorkerBackendServer"),
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.6" % Test,
    Test / jsEnv := new NodeJSEnv()
  )
