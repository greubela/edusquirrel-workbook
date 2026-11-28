import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Dependencies {
  val coreDependencies = Def.setting(Seq(
    "com.lihaoyi" %%% "upickle" % "4.4.3",
    "com.lihaoyi" %%% "fastparse" % "3.1.1",
    "org.scalameta" %%% "munit" % "1.3.6" % sbt.Test,
  ))

  val serverDependencies = Def.setting(Seq(
    "org.playframework" %% "play-netty-server" % "3.0.12",
    "org.playframework" %% "play-json" % "3.0.6",
    "com.github.jwt-scala" %% "jwt-play-json" % "12.0.0",
  ))

  val jsDependencies = Def.setting(Seq(
    "org.scala-js" %%% "scalajs-dom" % "2.8.1",
    "com.raquo" %%% "laminar" % "17.2.1",
    "io.github.cquiroz" %%% "scala-java-time" % "2.8.0",
    ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13)
  ))
}
