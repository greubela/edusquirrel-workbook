package it.evadid.util

import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.util.FileFactory.{InternetResourceFileDescription, UploadedResourceFileDescription}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import org.scalajs.dom
import org.scalajs.dom.{File, URL}

import java.io.IOException
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js.typedarray.ArrayBuffer

object FetchFromRemote {
  lazy val singleton = FetchFromRemote(
    Logger.withNameAndPrefixes(Some("FetchFromRemoteLogger"), PrintToStdLogger.printWarnAndError),
    ExecutionContext.global
  )
}

case class FetchFromRemote(logger: Logger, ec: ExecutionContext) {

  given ExecutionContext = ec

  private[util] def fetch(fileDescription: FileDescription, forceReload: Boolean = false): Future[LoadedFile] = cache.loadAsFuture(fileDescription, forceReload)

  private val cache: AsyncDataCache[FileDescription, LoadedFile] = new AsyncDataCache[FileDescription, LoadedFile](logger) {
    def load(file: FileDescription)(using ec: ExecutionContext): Future[LoadedFile] = file.loadData()

    override protected def executeLoading(file: FileDescription)(ec: ExecutionContext): Future[LoadedFile] = file.match {
      case u: UploadedResourceFileDescription => executeFetch(u.file).map(LoadedFile(file, _))
      case i: InternetResourceFileDescription => executeFetch(i.url.href).map(LoadedFile(file, _))
      case _ => file.loadData()
    }

    override protected def formatInputForLogging(in: FileDescription): String = in.toString

    override protected def formatOutputForLogging(out: LoadedFile): String = out.toString
  }


  private[util] def executeFetch(file: File): Future[Array[Byte]] = {
    val reader = new dom.FileReader()
    reader.readAsArrayBuffer(file)

    val promise = Promise[Array[Byte]]()
    reader.onload = _ => promise.success(JsHelpers.decodeArrayBuffer(reader.result.asInstanceOf[ArrayBuffer]))
    reader.onerror = event => promise.failure(new Exception(s"Could not read '${file.name}': ${event.toString}"))
    promise.future
  }


  private[util] def executeFetch(url: String): Future[Array[Byte]] = {
    JsHelpers.promiseToFuture(dom.fetch(url))
      .recoverWith { case err =>
        Future.failed(new IOException(s"Unknown Error while fetching '$url': ${err.toString}"))
      }(using ec)
      .flatMap { response =>
        if (!response.ok)
          Future.failed(new IOException(s"IO Error while fetching '$url': response status ${response.status}"))
        else
          JsHelpers.promiseToFuture(response.arrayBuffer()).map(JsHelpers.decodeArrayBuffer).recoverWith { case err =>
            Future.failed(new IOException(s"Error loading buffer after fetching '$url': ${err.toString}"))
          }
      }
  }
}


