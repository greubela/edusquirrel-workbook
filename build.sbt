
import org.scalajs.linker.interface.ModuleKind

enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)

lazy val workbookApp = project.in(file("."))
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .settings(
    scalaVersion := "3.3.3",
    scalaJSUseMainModuleInitializer := true,

    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.CommonJSModule)
    },

    // Libraries
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "com.raquo" %%% "laminar" % "17.2.1",
      "com.lihaoyi" %%% "upickle" % "4.3.1",
      "com.lihaoyi" %%% "fastparse" % "3.1.1",
      "org.scalameta" %%% "munit" % "1.2.1" % Test
    ),

    // NPM dependencies
    //Compile / npmDependencies += "openai" -> "4.33.0"

  )
