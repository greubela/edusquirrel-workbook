import Dependencies.*
import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*

lazy val buildAll = taskKey[Unit]("Build server/client, copy artifacts, then clean targets")
buildAll := Build.executeBuild(server, client, false).value

lazy val deployAll = taskKey[Unit]("BuildAll, then copy new files to /artifact/stable")
deployAll := Build.executeBuild(server, client, true).value

lazy val root = (project in file("."))
  .settings(Settings.globalSettings)
  .aggregate(core, server, client)
  .settings(
    name := "edusquirrel-workbook",
    publish / skip := true
  )

lazy val core = (project in file("./modules/core"))
  .settings(Settings.globalSettings)
  .settings(
    name := "core",
    libraryDependencies ++= coreDependencies.value
  )

lazy val server = (project in file("./modules/server"))
  .settings(Settings.globalSettings).settings(Settings.jvmSettings)
  .dependsOn(core)
  .settings(
    name := "server",
    libraryDependencies ++= (coreDependencies.value ++ jvmDependencies.value)
  )

lazy val client = (project in file("./modules/client"))
  .settings(Settings.globalSettings).settings(Settings.jsSettings)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(core)
  .settings(
    name := "client",
    scalaJSUseMainModuleInitializer := true,
    Test / jsEnv := new NodeJSEnv(),
    libraryDependencies ++= (coreDependencies.value ++ jsDependencies.value)
  )


// Todo: Worker Module 

/*
lazy val workbookApp = project.in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaVersion := "3.3.3",
    scalaJSUseMainModuleInitializer := true,

    // For the simulation-style specs (e.g. PRINT_SIMULATION=1), we want to see
    // stdout/stderr even when tests pass.
    Test / logBuffered := !sys.env.get("PRINT_SIMULATION").contains("1"),

    // Use plain Node.js for tests so the suite does not depend on jsdom
    // being available in the surrounding environment.
    Test / jsEnv := new NodeJSEnv(),

    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
    },

    Test / scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.NoModule)
    },

    // Libraries
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "com.raquo" %%% "laminar" % "17.2.1",
      "com.lihaoyi" %%% "upickle" % "4.3.1",
      "com.lihaoyi" %%% "fastparse" % "3.1.1",
      "org.gnieh" %%% "fs2-data-csv" % "1.11.3",
      "org.scalameta" %%% "munit" % "1.2.1" % Test,

      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.6.0", // needed for ZoneId / TZ database

      ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0")
        .cross(CrossVersion.for3Use2_13)
    ),

    // NPM dependencies
    //Compile / npmDependencies += "openai" -> "4.33.0"

  )
*/
