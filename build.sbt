import Dependencies.{*, coreDependencies, jvmDependencies}
import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import BuildArchitecture.*
import BuildCommands.*

lazy val root = (project in file("."))
  .settings(Settings.globalSettings)
  .aggregate(server, client, worker)
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
      case PathList("module-info.class") => MergeStrategy.discard
        // JavaFX / Gluon native-image metadata; conflicts across javafx-* jars
      case PathList("META-INF", "substrate", "config", _*) => MergeStrategy.discard
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case PathList("google", "protobuf", file) if file.endsWith(".proto") => MergeStrategy.first

      case PathList("META-INF", "substrate", "config", _*) => MergeStrategy.first

      case PathList("META-INF", "versions", _*) => MergeStrategy.discard
      case x => (assembly / assemblyMergeStrategy).value(x)

    },
    libraryDependencies ++= (coreDependencies.value ++ jvmDependencies.value ++ Seq(
      "com.mysql" % "mysql-connector-j" % "9.7.0",
      "org.eclipse.angus" % "jakarta.mail" % "2.0.3"
    ))
  )

lazy val client = (project in file("./modules/client"))
  .settings(Settings.globalSettings).settings(Settings.jsSettings)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(core.js)
  .settings(
    name := "client",
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("mainApp"),
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
    Compile / mainClass := Some("it.evadid.worker.WebWorkerBackendServer"),
    Test / jsEnv := new NodeJSEnv(),
    libraryDependencies ++= (coreDependencies.value ++ jsDependencies.value)
  )

