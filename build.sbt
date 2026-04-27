import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.linker.interface.ModuleKind
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import Dependencies._
import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.linker.interface.ModuleKind
import org.scalajs.linker.interface.OutputPatterns
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import Dependencies._

lazy val buildAll = taskKey[Unit]("Build all deliverable artifacts")

buildAll := {
  (server / Compile / packageBin).value
  (client / Compile / fullLinkJS).value
}

lazy val root = (project in file("."))
  .aggregate(core, server, client)
  .settings(
    name := "edusquirrel-workbook",
    publish / skip := true
  )

ThisBuild / organization := "it.evadid"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "3.3.3"

lazy val core = (project in file("./modules/core"))
  .settings(
    name := "core",
    libraryDependencies ++= coreDependencies.value
  )

lazy val server = (project in file("./modules/server"))
  .dependsOn(core)
  .settings(
    name := "server",
    libraryDependencies ++= (coreDependencies.value ++ jvmDependencies.value)
  )

lazy val client = (project in file("./modules/client"))
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

ThisBuild / organization := "it.evadid"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "3.3.3"

ThisBuild / Compile / packageBin / artifactPath := (ThisBuild / baseDirectory).value / "artifacts" / s"${name.value}.jar"
ThisBuild / Compile / fastLinkJS / scalaJSLinkerOutputDirectory := (ThisBuild / baseDirectory).value / "artifacts"
ThisBuild / Compile / fullLinkJS / scalaJSLinkerOutputDirectory := (ThisBuild / baseDirectory).value / "artifacts"
ThisBuild / Compile / fastLinkJS / scalaJSLinkerConfig := scalaJSLinkerConfig.value.withOutputPatterns(OutputPatterns.fromJSFile(s"${name.value}-fastopt.js"))
ThisBuild / Compile / fullLinkJS / scalaJSLinkerConfig := scalaJSLinkerConfig.value.withOutputPatterns(OutputPatterns.fromJSFile(s"${name.value}-fullopt.js"))
