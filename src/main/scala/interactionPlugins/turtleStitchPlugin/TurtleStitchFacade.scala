package interactionPlugins.turtleStitchPlugin

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap, TranslationMaps}
import contentmanagement.storage.DataStorage

import scala.Predef.->
import scala.concurrent.{ExecutionContext, Future, Promise as ScalaPromise}
import scala.scalajs.js
import scala.scalajs.js.Promise as JsPromise
import scala.scalajs.js.annotation.*

object TurtleStitchFacade {

  @js.native
  @JSGlobal("TurtleStitchPoC")
  object TurtleStitchFacadeNative extends js.Object {

    /** returns SVG data URL containing all script/program renderings */
    def calcProgramSvg(xml_content: String, language: String): JsPromise[String] = js.native

    /** runs green flag once, returns stage screenshot PNG data URL */
    def simulateGreenFlag(xml_content: String): JsPromise[String] = js.native

    /** downloads DST for the given program XML */
    def downloadDst(xml_content: String): JsPromise[Unit] = js.native
  }

  private val executionLock = new AnyRef
  private var previousExecution: Future[Unit] = Future.successful(())

  private def runSerially[T](task: => Future[T])(using ec: ExecutionContext): Future[T] = {
    val taskResult = ScalaPromise[T]()
    executionLock.synchronized {
      val queued = previousExecution.recover { case _ => () }.flatMap(_ => task)
      queued.onComplete(taskResult.complete)(ec)
      previousExecution = queued.map(_ => ()).recover { case _ => () }
    }
    taskResult.future
  }

  val programSvgDataSrcStorage: DataStorage[(String, HumanLanguage), String] = new DataStorage[(String, HumanLanguage), String]("ProgramSvgDataSrc", false) {
    private def turtleLang(language: HumanLanguage) = AppLanguage.turtleStitchLangMap.getOrElse(language, "en")

    protected def executeLoading(in: (String, HumanLanguage))(ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[String] = {

      val (xml, language) = in
      runSerially(TurtleStitchFacadeNative.calcProgramSvg(xml, turtleLang(language)).toFuture)(using ec)
    }

    protected def initialValueWhileLoading(in: (String, HumanLanguage)): Option[String] = {
      Some(TranslationMaps.languageMapImageLoading.getInLanguage(in._2))
    }

    protected def formatInputForLogging(in: (String, HumanLanguage)): String = "XmlInput(" + in._1.length + ", " + in._1.substring(0, 60) + ", " + turtleLang(in._2) + ")"

    protected def formatOutputForLogging(out: String): String = "SvgOutput(" + out.length + ", " + out.substring(0, 60) + " ...)"
  }

  val programOutputDataSrcStorage: DataStorage[String, String] = new DataStorage[String, String]("ProgramSvgDataSrc", false) {

    protected def executeLoading(xml: String)(ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[String] = {
      runSerially(TurtleStitchFacadeNative.simulateGreenFlag(xml).toFuture)(using ec)
    }

    protected def initialValueWhileLoading(in: String): Option[String] = {
      None
    }

    protected def formatInputForLogging(in: String): String = "XmlInput(" + in.length + ", " + in.substring(0, 60) + ")"

    protected def formatOutputForLogging(out: String): String = "PngOutput(" + out.length + ", " + out.substring(0, 60) + " ...)"
  }


  def downloadDst(xml: String)(using ec: ExecutionContext): Future[Unit] =
    runSerially(
      TurtleStitchFacadeNative
        .downloadDst(xml)
        .toFuture
    )
}
