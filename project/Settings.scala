import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbt._
import sbt.Keys._

import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
object Settings {

  lazy val globalSettings = Seq(
    target := (ThisBuild / baseDirectory).value / "target" / name.value,
    organization := "it.evadid",
    version := "0.1",
    scalaVersion := "3.3.3"
  )

  lazy val jsSettings = Seq(
 //   Compile / fastLinkJS / scalaJSLinkerOutputDirectory := (ThisBuild / baseDirectory).value / "artifacts" / "client" / "fastLinkJS",
 //   Compile / fullLinkJS / scalaJSLinkerOutputDirectory := (ThisBuild / baseDirectory).value / "artifacts" / "client" / "fullLinkJS",
  )

  lazy val jvmSettings = Seq(
  //  Compile / packageBin / artifactPath := (ThisBuild / baseDirectory).value / "artifacts" / "server" / s"${name.value}.jar"
  )

}
