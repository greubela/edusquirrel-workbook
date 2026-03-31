package interactionPlugins.turtleStitchPlugin

import contentmanagement.model.language.{AppLanguage, HumanLanguage, TranslationMaps}
import contentmanagement.storage.DataStorage

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.Promise as JsPromise
import scala.scalajs.js.annotation.*

object TurtleStitchFacade {

  @js.native
  @JSGlobal("TurtleStitchPoC")
  object TurtleStitchFacadeNative extends js.Object {
    def createEditor(options: js.Object): JsPromise[TurtleStitchEditorHandle] = js.native
  }

  @js.native
  trait TurtleStitchEditorHandle extends js.Object {
    def calcProgramSvg(xml_content: String, language: String): JsPromise[String] = js.native
    def simulateGreenFlag(xml_content: String): JsPromise[String] = js.native
    def downloadDst(xml_content: String): JsPromise[Unit] = js.native
    def destroy(): Unit = js.native
  }

  private def withFreshEditor[T](task: TurtleStitchEditorHandle => Future[T])(using ec: ExecutionContext): Future[T] = {
    TurtleStitchFacadeNative
      .createEditor(js.Dynamic.literal(hidden = true).asInstanceOf[js.Object])
      .toFuture
      .flatMap { editor =>
        task(editor).andThen { case _ =>
          try editor.destroy()
          catch { case _: Throwable => () }
        }
      }
  }

  private def turtleLang(language: HumanLanguage): String =
    AppLanguage.turtleStitchLangMap.getOrElse(language, "en")

  val programXmlRunOutputStorage: DataStorage[String, String] = new DataStorage[String, String]("ProgramXmlRunOutput", true) {
    protected def executeLoading(xml: String)(ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[String] =
      withFreshEditor(_.simulateGreenFlag(xml).toFuture)(using ec)

    override protected def defaultValueWhileLoading(in: String): Option[String] = None

    protected def formatInputForLogging(in: String): String = "XmlInput(" + in.length + ", " + in.substring(0, 20) + ")"

    override protected def formatOutputForLogging(out: String): String = out
  }

  val programSvgDataSrcStorage: DataStorage[(String, HumanLanguage), String] = new DataStorage[(String, HumanLanguage), String]("ProgramSvgDataSrc", false) {

    protected def executeLoading(in: (String, HumanLanguage))(ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[String] = {
      val (xml, language) = in
      println("execute loading program svg data src storage")
      withFreshEditor(_.calcProgramSvg(xml, turtleLang(language)).toFuture)(using ec)
    }

    protected def defaultValueWhileLoading(in: (String, HumanLanguage)): Option[String] =
      Some(TranslationMaps.languageMapImageLoading.getInLanguage(in._2))

    protected def formatInputForLogging(in: (String, HumanLanguage)): String = "XmlInput(" + in._1.length + ", " + in._1.substring(0, 60) + ", " + turtleLang(in._2) + ")"

    protected def formatOutputForLogging(out: String): String = "SvgOutput(" + out.length + ", " + out.substring(0, 60) + " ...)"
  }

  val programOutputDataSrcStorage: DataStorage[String, String] = new DataStorage[String, String]("ProgramSvgDataSrc", false) {

    protected def executeLoading(xml: String)(ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[String] =
      withFreshEditor(_.simulateGreenFlag(xml).toFuture)(using ec)

    protected def defaultValueWhileLoading(in: String): Option[String] = None

    protected def formatInputForLogging(in: String): String = "XmlInput(" + in.length + ", " + in.substring(0, 60) + ")"

    protected def formatOutputForLogging(out: String): String = "PngOutput(" + out.length + ", " + out.substring(0, 60) + " ...)"
  }

  def downloadDst(xml: String)(using ec: ExecutionContext): Future[Unit] =
    withFreshEditor(_.downloadDst(xml).toFuture)
}
