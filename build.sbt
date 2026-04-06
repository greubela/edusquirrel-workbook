import org.scalajs.jsenv.nodejs.NodeJSEnv

import org.scalajs.linker.interface.ModuleKind

enablePlugins(ScalaJSPlugin)

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
