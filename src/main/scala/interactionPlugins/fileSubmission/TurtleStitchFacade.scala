package interactionPlugins.fileSubmission

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import util.FunctionalUtility

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.Promise
import scala.scalajs.js.annotation.*
import scala.util.{Failure, Success}

object TurtleStitchFacade {

  @js.native
  @JSGlobal("TurtleStitchPoC")
  object TurtleStitchFacadeNative extends js.Object {

    /** returns PNG data URL for the blocks/program rendering */
    def calcProgramPng(xml_content: String, language: String): Promise[String] = js.native

    /** runs green flag once, returns stage screenshot PNG data URL */
    def simulateGreenFlag(xml_content: String): Promise[String] = js.native

    /** downloads DST for the given program XML */
    def downloadDst(xml_content: String): Promise[Unit] = js.native
  }


  val cachedFunc: ((String, String)) => L.Var[Option[String]] =  FunctionalUtility.withCacheToVar[(String, String), String](calcProgramPngDataSrc)

  def getProgramPngDataSrc(xml: String, language: String): L.Var[Option[String]] = cachedFunc((xml, language))


  def calcProgramPngDataSrc(
                             xml: String,
                             language: String
                           ): Future[String] = {
    println("called calc(" + xml.length + ", " + language + ")")
    val res = TurtleStitchFacadeNative
      .calcProgramPng(xml, language)
      .toFuture
    res.foreach(res => println("finished calc(" + xml.length + ", " + language + "): " + res.length))(ExecutionContext.global)
    res
  }

  def simulateGreenFlag(
                         xml: String
                       ): Future[String] =
    TurtleStitchFacadeNative
      .simulateGreenFlag(xml)
      .toFuture

  def downloadDst(xml: String): Future[Unit] =
    TurtleStitchFacadeNative
      .downloadDst(xml)
      .toFuture
}