package interactionPlugins.fileSubmission

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap, TranslationMaps}
import contentmanagement.storage.DataStorage

import scala.Predef.->
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.Promise
import scala.scalajs.js.annotation.*

object TurtleStitchFacade {

  @js.native
  @JSGlobal("TurtleStitchPoC")
  object TurtleStitchFacadeNative extends js.Object {

    /** returns SVG data URL containing all script/program renderings */
    def calcProgramSvg(xml_content: String, language: String): Promise[String] = js.native

    /** runs green flag once, returns stage screenshot PNG data URL */
    def simulateGreenFlag(xml_content: String): Promise[String] = js.native

    /** downloads DST for the given program XML */
    def downloadDst(xml_content: String): Promise[Unit] = js.native
  }

  val programSvgDataSrcStorage: DataStorage[(String, HumanLanguage), String] = new DataStorage[(String, HumanLanguage), String]("ProgramSvgDataSrc", true) {
    private def turtleLang(language: HumanLanguage) = AppLanguage.turtleStitchLangMap.getOrElse(language, "en")
    protected def executeLoading(in: (String, HumanLanguage))(ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[String] = {

      val (xml, language) = in
      TurtleStitchFacadeNative.calcProgramSvg(xml, turtleLang(language)).toFuture
    }

    protected def initialValueWhileLoading(in: (String, HumanLanguage)): Option[String] = {
      Some(TranslationMaps.languageMapImageLoading.getInLanguage(in._2))
    }

    protected def formatInputForLogging(in: (String, HumanLanguage)): String = "XmlInput(" + in._1.length + ", " + in._1.substring(0, 60) + ", "+ turtleLang(in._2) + ")"

    protected def formatOutputForLogging(out: String): String = "SvgOutput(" + out.length + ", " + out.substring(0, 60) + " ...)"
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