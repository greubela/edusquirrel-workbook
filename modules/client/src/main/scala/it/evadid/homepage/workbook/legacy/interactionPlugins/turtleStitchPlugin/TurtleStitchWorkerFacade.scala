package it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.TurtleStitchEditor.turtleLang
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import todomove.`export`.workers.TurtleStitchWorker
import todomove.datastructures.web.file.FullImage
import todomove.datastructures.web.file.FullImage.DataSourceImage

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js.JSConverters.*
import scala.util.{Failure, Success}

object TurtleStitchWorkerFacade {


  lazy val basicCacheLogger: Logger = {
    println("### Inited Logger in TurtleStitchWorkerFacade::basicCacheLogger")
    Logger.withNameAndPrefixes(Some("TurtleStitchWorkerFacade::ProgramPngDataSrcStore"), PrintToStdLogger.printWarnAndError)
  }
  private lazy val programPngDataSrcStorage: AsyncDataCache[(String, HumanLanguage), String] = new AsyncDataCache[(String, HumanLanguage), String](basicCacheLogger) {
    protected def executeLoading(in: (String, HumanLanguage))(ec: ExecutionContext): Future[String] = {
      val (xml, language) = in
      //calcPngDataSrcWithQueuedWorker(xml, language)(using ec)
      basicCacheLogger.logWarn("TurtleStitchWorkerFacade::programPngDataSrcStorage - still using non-parallel rendering.")
      if (xml.strip.isEmpty || !xml.contains("scripts")) Future.successful(emptyPngDataUrl)
      else TurtleStitchEditor.withSingletonEditor(_.calcProgramSvg(xml, turtleLang(language)).toFuture)(using ec)
    }

    protected def formatInputForLogging(in: (String, HumanLanguage)): String =
      val xmlStr =
        if (in._1.length > 60) s"XmlInput(${in._1.length}, ${in._1.substring(0, 60)})"
        else s"XmlInput($in._1)"
      s"XmlInput($xmlStr}, ${turtleLang(in._2)})"

    protected def formatOutputForLogging(out: String): String =
      if (out.length > 60) s"PngOutput(${out.length}, ${out.substring(0, 60)} ...)"
      else s"PngOutput($out)"
  }


  /**
   * Returns a preview image for the program code beneath green-flag event handlers.
   *
   * IMPORTANT:
   * - This is the "program snapshot" pipeline (editor scripts/blocks view),
   *   not the "executed stage after green-flag run" pipeline.
   * - The executed stage snapshot is [[getExecutedStageSnapshotDataSrc]].
   */
  def getGreenFlagProgramSnapshotDataSrc(turtleStitchXml: String, language: HumanLanguage): AsyncData[Nothing, FullImage] = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val asyncResult: AsyncData[Nothing, String] = programPngDataSrcStorage.loadIntoVariable((turtleStitchXml, language))
    val res: AsyncData[Nothing, FullImage] = asyncResult.map(curPngStr => DataSourceImage(curPngStr, "png"))
    res
  }

  private lazy val stagePngLogger: Logger =
    Logger.withNameAndPrefixes(Some("TurtleStitchWorkerFacade::StagePngDataSrcStore"), PrintToStdLogger.printWarnAndError)

  private lazy val stagePngDataSrcStorage: AsyncDataCache[String, String] = new AsyncDataCache[String, String](stagePngLogger) {
    protected def executeLoading(xml: String)(ec: ExecutionContext): Future[String] = {
      if (xml.strip.isEmpty || !xml.contains("<project")) Future.successful(emptyPngDataUrl)
      else simulateStageWithQueuedWorker(xml)(using ec)
    }

    protected def formatInputForLogging(in: String): String =
      if (in.length > 60) s"XmlInput(${in.length}, ${in.substring(0, 60)})"
      else s"XmlInput($in)"

    protected def formatOutputForLogging(out: String): String =
      if (out.length > 60) s"PngOutput(${out.length}, ${out.substring(0, 60)} ...)"
      else s"PngOutput($out)"
  }

  /**
   * Returns the stage image after loading the project XML and running green-flag once
   * (`TurtleStitchWorker.simulateGreenFlag` → `stage.fullImage()` PNG data URL).
   */
  def getExecutedStageSnapshotDataSrc(turtleStitchXml: String): AsyncData[Nothing, FullImage] = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    stagePngDataSrcStorage
      .loadIntoVariable(turtleStitchXml)
      .map(png => DataSourceImage(png, "png"))
  }

  // Verified 1x1 transparent PNG. Some previous variants shipped with invalid IDAT CRC,
  // which browsers may reject with "Image corrupt or truncated.".
  val emptyPngDataUrl: String =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGBgAAAABQABpfZFQAAAAABJRU5ErkJggg=="

  private var worker: TurtleStitchWorker = new TurtleStitchWorker()
  private val queueLock = new AnyRef
  private var queuedWork: Future[Unit] = Future.successful(())
  private var workerInit: Option[Future[Unit]] = None

  private def simulateStageWithQueuedWorker(turtleStitchXml: String)(using ec: ExecutionContext): Future[String] =
    enqueueWorkerTask { w =>
      w.simulateGreenFlag(turtleStitchXml).toFuture
    }

  private def enqueueWorkerTask[T](task: TurtleStitchWorker => Future[T])(using ec: ExecutionContext): Future[T] = {
    val result = Promise[T]()

    queueLock.synchronized {
      val runTask = queuedWork
        .recover { case _ => () }
        .flatMap(_ => ensureWorkerInitialized())
        .flatMap(_ => task(worker))

      runTask.onComplete {
        case Success(value) => result.success(value)
        case Failure(error) => result.failure(error)
      }(using ec)

      queuedWork = runTask.map(_ => ()).recover { case _ => () }
    }

    result.future
  }

  private def ensureWorkerInitialized()(using ec: ExecutionContext): Future[Unit] =
    queueLock.synchronized {
      workerInit match {
        case Some(existingInit) => existingInit
        case None =>
          val init = worker.init().toFuture
          workerInit = Some(init)
          init.andThen {
            case Failure(_) =>
              queueLock.synchronized {
                workerInit = None
              }
            case Success(_) => ()
          }(using ExecutionContext.parasitic)
          init
      }
    }

  def destroyWorker(): Unit =
    queueLock.synchronized {
      worker.destroy()
      worker = new TurtleStitchWorker()
      workerInit = None
      queuedWork = Future.successful(())
    }
}
