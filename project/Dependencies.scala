import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.linker.interface.ModuleKind
import org.scalajs.linker.interface.OutputPatterns
import org.jetbrains.sbt.structure.Configuration.Test
import sbt._
import Dependencies._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Dependencies {

  val coreDependencies = Def.setting(Seq(
    "com.lihaoyi" %%% "upickle" % "4.4.3",
    "com.lihaoyi" %%% "fastparse" % "3.1.1",
    "org.creativescala" %% "doodle" % "0.34.0",
    "org.scalameta" %%% "munit" % "1.3.3" % sbt.Test,
  ))

  val jvmDependencies = Def.setting(Seq(
    "org.playframework" %% "play-netty-server" % "3.0.10",
    "org.playframework" %% "play-json" % "3.0.4",
    "org.scalafx" %% "scalafx" % "24.0.0-R35",
  ))

  val jsDependencies = Def.setting(Seq(
    "org.scala-js" %%% "scalajs-dom" % "2.8.1",
    "com.raquo" %%% "laminar" % "17.2.1",
    "org.gnieh" %%% "fs2-data-csv" % "1.14.0",
    "io.github.cquiroz" %%% "scala-java-time" % "2.7.0",
    "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.7.0",
    ("org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.1").cross(CrossVersion.for3Use2_13),
    ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13)
  ))
}