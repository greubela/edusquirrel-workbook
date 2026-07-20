package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.homepage.util.web.JsHelpers
import it.evadid.util.logging.Logger
import org.scalajs.dom
import org.scalajs.dom.*

import java.io.IOException
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}

object FileStore {


  object FetchFromExternal {

  }

  object DownloadToDisc {

  }

  object PostToExternal {

  }
}


case class FileStore(logger: Logger) {

  case class FetchFromExternal(logger: Logger) {


    val cache: AsyncDataCache[FileDescription, LoadedFile] = new AsyncDataCache[FileDescription, LoadedFile](logger) {
      def load(file: FileDescription)(using ec: ExecutionContext): Future[LoadedFile] = file.loadData()

      override protected def executeLoading(file: FileDescription)(ec: ExecutionContext): Future[LoadedFile] = file.loadData()

      override protected def formatInputForLogging(in: FileDescription): String = in.toString

      override protected def formatOutputForLogging(out: LoadedFile): String = out.toString
    }

    def fetchFile(file: File): Future[Array[Byte]] = {
      val reader = new dom.FileReader()
      reader.readAsArrayBuffer(file)

      val promise = Promise[Array[Byte]]()
      reader.onload = _ => promise.success(JsHelpers.decodeArrayBuffer(reader.result.asInstanceOf[ArrayBuffer]))
      reader.onerror = event => promise.failure(new Exception(s"Could not read '${file.name}': ${event.toString}"))
      promise.future
    }

    def fetchUrl(url: String): Future[Array[Byte]] = {
      JsHelpers.promiseToFuture(dom.fetch(url))
        .recoverWith { case err =>
          Future.failed(new IOException(s"Unknown Error while fetching '$url': ${err.toString}"))
        }
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




}


