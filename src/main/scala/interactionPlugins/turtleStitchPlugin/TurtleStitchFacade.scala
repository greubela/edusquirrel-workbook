package interactionPlugins.turtleStitchPlugin

import `export`.workers.TurtleStitchWorker
import com.raquo.laminar.api.L.Var
import datastructures.core.language.{HumanLanguage, TranslationMaps}
import datastructures.web.storage.AsyncDataCache
import interactionPlugins.turtleStitchPlugin.TurtleStitchEditor.turtleLang

import scala.concurrent.{ExecutionContext, Future}

object TurtleStitchFacade {

  def getPngDataSrcOfGreenFlagProgramEditor(turtleStitchXml: String, language:HumanLanguage): Var[Option[String]] = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    programSvgDataSrcStorage.loadIntoVariable( (turtleStitchXml, language) )
  }

  private val worker: TurtleStitchWorker = new TurtleStitchWorker()
  @volatile private var workerValidated: Boolean = false

  private val programSvgDataSrcStorage: AsyncDataCache[(String, HumanLanguage), String] = new AsyncDataCache[(String, HumanLanguage), String]("ProgramSvgDataSrc", false) {
    protected def executeLoading(in: (String, HumanLanguage))(ec: ExecutionContext): Future[String] = {
      val (xml, language) = in
      calcPngDataSrcWithValidatedWorker(xml, language)
    }

    protected def defaultValueWhileLoading(in: (String, HumanLanguage)): Option[String] =
      Some(TranslationMaps.languageMapImageLoading.getInLanguage(in._2))

    protected def formatInputForLogging(in: (String, HumanLanguage)): String =
      s"XmlInput(${in._1.length}, ${in._1.substring(0, 60)}, ${turtleLang(in._2)})"

    protected def formatOutputForLogging(out: String): String =
      s"SvgOutput(${out.length}, ${out.substring(0, 60)} ...)"
  }

  private def calcPngDataSrcWithValidatedWorker(turtleStitchXml: String, language: HumanLanguage): Future[String] = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val workerAttempt =
      if (workerValidated) calcPngDataSrcOfGreenFlagProgramWorker(turtleStitchXml, language)
      else validateWorker().flatMap(_ => calcPngDataSrcOfGreenFlagProgramWorker(turtleStitchXml, language))
    workerAttempt
  }

  private def validateWorker()(implicit ec: ExecutionContext): Future[Unit] =
    worker.init().toFuture.map { _ =>
      workerValidated = true
    }

  private def calcPngDataSrcOfGreenFlagProgramWorker(turtleStitchXml: String, language: HumanLanguage): Future[String] = {
    worker.calcProgramSvg(turtleStitchXml, TurtleStitchEditor.turtleLang(language)).toFuture
  }

  def downloadDst(xml: String)(using ec: ExecutionContext): Future[Unit] =
    TurtleStitchEditor.downloadDst(xml)

}
