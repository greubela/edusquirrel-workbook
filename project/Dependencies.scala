import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Dependencies {
  private val javaFxPlatform = {
    val os = sys.props("os.name").toLowerCase
    val arm = Set("aarch64", "arm64")(sys.props("os.arch"))
    if (os.contains("win")) "win"
    else if (os.contains("mac")) { if (arm) "mac-aarch64" else "mac" }
    else { if (arm) "linux-aarch64" else "linux" }
  }
  val desktopDependencies = Seq("base", "graphics").map(module =>
    "org.openjfx" % s"javafx-$module" % "21.0.13" classifier javaFxPlatform)


  val coreDependencies = Def.setting(Seq(
    "com.lihaoyi" %%% "upickle" % "4.4.3",
    "com.lihaoyi" %%% "fastparse" % "3.1.1",
    "org.scalameta" %%% "munit" % "1.3.6" % sbt.Test,
    "org.gnieh" %%% "fs2-data-csv" % "1.14.1",
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
    "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.8.0",
    ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13)
  ))
}
