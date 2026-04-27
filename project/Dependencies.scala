import sbt._
import Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.linker.interface.ModuleKind
import org.scalajs.linker.interface.OutputPatterns
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import Dependencies._

object Dependencies {

  val coreDependencies = Def.setting(Seq.empty[ModuleID])

  val jvmDependencies = Def.setting(Seq.empty[ModuleID])

  val jsDependencies = Def.setting(Seq(
    "org.scala-js" %%% "scalajs-dom" % "2.8.0",
    "com.raquo" %%% "laminar" % "17.2.1",
    "com.lihaoyi" %%% "upickle" % "4.3.1",
    "com.lihaoyi" %%% "fastparse" % "3.1.1",
    "org.gnieh" %%% "fs2-data-csv" % "1.11.3",
    "org.scalameta" %%% "munit" % "1.2.1" % Test,
    "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
    "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.6.0",
    ("org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0").cross(CrossVersion.for3Use2_13),
    ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13)
  ))
}