package interactionPlugins.fileSubmission

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage}
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

    /** returns PNG data URL for the blocks/program rendering */
    def calcProgramPng(xml_content: String, language: String): Promise[String] = js.native

    /** returns SVG data URL containing all script/program renderings */
    def calcProgramSvg(xml_content: String, language: String): Promise[String] = js.native

    /** runs green flag once, returns stage screenshot PNG data URL */
    def simulateGreenFlag(xml_content: String): Promise[String] = js.native

    /** downloads DST for the given program XML */
    def downloadDst(xml_content: String): Promise[Unit] = js.native
  }


  //val cachedFunc: ((String, String)) => L.Var[Option[String]] = FunctionalUtility.withCacheToVar[(String, String), String](calcProgramPngDataSrc)

  /*private val turtleStitchLanguageCode[HumanLanguage, String] = Map(
    AppLanguage.ENGLISH -> "en",
    AppLanguage.German -> "de",
    AppLanguage.French
  )*/

  private val programPngDataSrcStorage: DataStorage[(String, HumanLanguage), String] = new DataStorage[(String, HumanLanguage), String]("ProgramPngDataSrc", true) {
    protected def executeLoading(in: (String, HumanLanguage))(ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[String] = {
      val (xml, language) = in
      TurtleStitchFacadeNative.calcProgramPng(xml, language.nameAbbr.toLowerCase).toFuture
    }

    protected def initialValueWhileLoading(in: (String, HumanLanguage)): String = {
      "Loading DataSrc for Language " + in._2.nameAbbr
    }
  }


  private val programSvgDataSrcStorage: DataStorage[(String, HumanLanguage), String] = new DataStorage[(String, HumanLanguage), String]("ProgramSvgDataSrc", true) {
    protected def executeLoading(in: (String, HumanLanguage))(ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[String] = {
      val (xml, language) = in
      TurtleStitchFacadeNative.calcProgramSvg(xml, language.nameAbbr.toLowerCase).toFuture
    }

    protected def initialValueWhileLoading(in: (String, HumanLanguage)): String = {
      "Loading SVG DataSrc for Language " + in._2.nameAbbr
    }
  }

  def getProgramPngDataSrc(xml: String, language: HumanLanguage): L.Var[Option[String]] = programPngDataSrcStorage.loadIntoVariable((xml, language))(ExecutionContext.global)

  def getProgramPngDataSrc(xml: String, language: Signal[HumanLanguage]): L.Signal[Option[String]] = {
    programPngDataSrcStorage.createSignalDependendVar(language.map(lang => (xml, lang)))(ExecutionContext.global).signal
  }

  def getProgramSvgDataSrc(xml: String, language: HumanLanguage): L.Var[Option[String]] = programSvgDataSrcStorage.loadIntoVariable((xml, language))(ExecutionContext.global)

  def getProgramSvgDataSrc(xml: String, language: Signal[HumanLanguage]): L.Signal[Option[String]] = {
    programSvgDataSrcStorage.createSignalDependendVar(language.map(lang => (xml, lang)))(ExecutionContext.global).signal
  }

  /*
    def getProgramPngSignal(id: String, xml: String, languageSignal: Signal[HumanLanguage]): L.Signal[Option[String]] = {
      val res = Var[Option[String]](None)
      languageSignal.foreach(nextVal => {
        TurtleStitchFacade.calcProgramPngDataSrc(xml, nextVal.nameAbbr).onComplete {
          case Success(data) => {
            println("SUCCESS (" + id + "), data: " + data.length)
            res.set(Some(data))
          }
          case Failure(err) => println("[ERROR]]: " + err)
        }(ExecutionContext.global)
      })(unsafeWindowOwner)

      res.signal.foreach(newValue => {
        println("signal received in var underlying signal for id " + id + ": " + newValue.map(_.length) + " bytes")
      })(unsafeWindowOwner)

      res.signal
    }*/


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