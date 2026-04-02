package interactionPlugins.turtleStitchPlugin

import `export`.workers.TurtleStitchWorker
import com.raquo.laminar.api.L.Var
import datastructures.core.language.{HumanLanguage, TranslationMaps}
import datastructures.web.storage.AsyncDataCache
import interactionPlugins.turtleStitchPlugin.TurtleStitchEditor.turtleLang

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object TurtleStitchWorkerFacade {

  def getPngDataSrcOfGreenFlagProgramEditor(turtleStitchXml: String, language:HumanLanguage): Var[Option[String]] = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    programSvgDataSrcStorage.loadIntoVariable( (turtleStitchXml, language) )
  }

  private val programSvgDataSrcStorage: AsyncDataCache[(String, HumanLanguage), String] = new AsyncDataCache[(String, HumanLanguage), String]("ProgramSvgDataSrc", false) {
    protected def executeLoading(in: (String, HumanLanguage))(ec: ExecutionContext): Future[String] = {
      val (xml, language) = in
      calcPngDataSrcWithQueuedWorker(xml, language)(using ec)
    }

    protected def defaultValueWhileLoading(in: (String, HumanLanguage)): Option[String] =
      Some(TranslationMaps.languageMapImageLoading.getInLanguage(in._2))

    protected def formatInputForLogging(in: (String, HumanLanguage)): String =
      s"XmlInput(${in._1.length}, ${in._1.substring(0, 60)}, ${turtleLang(in._2)})"

    protected def formatOutputForLogging(out: String): String =
      s"SvgOutput(${out.length}, ${out.substring(0, 60)} ...)"
  }

  private var worker: TurtleStitchWorker = new TurtleStitchWorker()
  private val queueLock = new AnyRef
  private var queuedWork: Future[Unit] = Future.successful(())
  private var workerInit: Option[Future[Unit]] = None

  private def calcPngDataSrcWithQueuedWorker(turtleStitchXml: String, language: HumanLanguage)(using ec: ExecutionContext): Future[String] =
    enqueueWorkerTask { worker =>
      worker.calcProgramSvg(turtleStitchXml, TurtleStitchEditor.turtleLang(language)).toFuture
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
