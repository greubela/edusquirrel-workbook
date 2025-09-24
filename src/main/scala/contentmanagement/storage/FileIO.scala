package contentmanagement.storage

import contentmanagement.model.FileInformation
import org.scalajs.dom
import org.scalajs.dom.File
import util.TypeConversion

import java.io.IOException
import scala.concurrent.{Future, Promise}
import scala.scalajs.js.typedarray.ArrayBuffer

object FileIO {




  def loadFile(file: File): Future[FileInformation] = {
    val reader = new dom.FileReader()
    reader.readAsArrayBuffer(file)

    val promise = Promise[FileInformation]()
    reader.onload = _ => promise.success(handleBuffer(file.name, reader.result.asInstanceOf[ArrayBuffer]))
    reader.onerror = event => promise.failure(new Exception(s"Could not read '${file.name}': ${event.toString}"))
    promise.future
  }

  private def handleBuffer(url: String, buffer: ArrayBuffer): FileInformation = {
    val content = TypeConversion.decodeArrayBuffer(buffer)
    FileInformation.fromPathAndData(url, content)
  }


  def fetchUrl(url: String): Future[FileInformation] = {
    val promise = Promise[FileInformation]()
    dom.fetch(url).`then`(
      onRejected = err => promise.failure(new IOException(s"Unknown Error while fetching '$url': ${err.toString}")),
      onFulfilled = response =>
        if (!response.ok)
          promise.failure(new IOException(s"IO Error while fetching '$url': response status ${response.status}"))
        else response.arrayBuffer().`then`(
          onRejected = err => promise.failure(new IOException(s"Error loading buffer after fetching '$url': ${err.toString}")),
          onFulfilled = buffer => promise.success(handleBuffer(url, buffer)))
    )
    promise.future
  }


}
