package interactionPlugins.turtleStitchPlugin

import `export`.workers.TurtleStitchWorker
import com.raquo.laminar.api.L.Var
import datastructures.core.language.{HumanLanguage, TranslationMaps}
import datastructures.web.storage.AsyncDataCache
import interactionPlugins.turtleStitchPlugin.TurtleStitchEditor.{turtleLang, withSingletonEditor}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object TurtleStitchWorkerFacade {

  /**
   * Returns a preview image for the program code beneath green-flag event handlers.
   *
   * IMPORTANT:
   * - This is the "program snapshot" pipeline (editor scripts/blocks view),
   *   not the "executed stage after green-flag run" pipeline.
   * - The executed stage snapshot is handled by worker methods like simulateGreenFlag/getGreenFlagPng.
   */
  def getGreenFlagProgramSnapshotDataSrc(turtleStitchXml: String, language: HumanLanguage): Var[Option[String]] = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    programPngDataSrcStorage.loadIntoVariable((turtleStitchXml, language))
  }

  private val EMPTY_PNG_DATA_URL: String = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGNgYAAAAAMAASsJTYQAAAAASUVORK5CYII=";
  
  /** Backwards-compatible alias for previous API name. */
  def getPngDataSrcOfGreenFlagProgramEditor(turtleStitchXml: String, language: HumanLanguage): Var[Option[String]] =
    getGreenFlagProgramSnapshotDataSrc(turtleStitchXml, language)

  private val programPngDataSrcStorage: AsyncDataCache[(String, HumanLanguage), String] = new AsyncDataCache[(String, HumanLanguage), String]("ProgramPngDataSrc", false) {
    protected def executeLoading(in: (String, HumanLanguage))(ec: ExecutionContext): Future[String] = {
      val (xml, language) = in
      //calcPngDataSrcWithQueuedWorker(xml, language)(using ec)
      println("[WARN] TurtleStitchWorkerFacade::programPngDataSrcStorage - still using non-parallel rendering.")
      if(xml.strip.isEmpty || !xml.contains("TurtleStitch") || !xml.contains("scripts")) Future.successful(EMPTY_PNG_DATA_URL)
      else TurtleStitchEditor.withSingletonEditor(_.calcProgramSvg(xml, turtleLang(language)).toFuture)(using ec)
    }

    protected def defaultValueWhileLoading(in: (String, HumanLanguage)): Option[String] =
      Some(TranslationMaps.languageMapImageLoading.getInLanguage(in._2))

    protected def formatInputForLogging(in: (String, HumanLanguage)): String =
      val xmlStr =
        if (in._1.length > 60) s"XmlInput(${in._1.length}, ${in._1.substring(0, 60)})"
        else s"XmlInput($in._1)"
      s"XmlInput($xmlStr}, ${turtleLang(in._2)})"

    protected def formatOutputForLogging(out: String): String =
      if (out.length > 60) s"PngOutput(${out.length}, ${out.substring(0, 60)} ...)"
      else s"PngOutput($out)"
  }

  private var worker: TurtleStitchWorker = new TurtleStitchWorker()
  private val queueLock = new AnyRef
  private var queuedWork: Future[Unit] = Future.successful(())
  private var workerInit: Option[Future[Unit]] = None

  /**
   * Uses the worker's green-flag program snapshot operation (`calcProgramPng`) to render script blocks.
   * This intentionally does not execute the program.
   */
  private def calcPngDataSrcWithQueuedWorker(turtleStitchXml: String, language: HumanLanguage)(using ec: ExecutionContext): Future[String] =
    enqueueWorkerTask { worker =>
      worker.calcProgramPng(turtleStitchXml, TurtleStitchEditor.turtleLang(language)).toFuture
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
      }(ec)

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
          }(ExecutionContext.parasitic)
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
