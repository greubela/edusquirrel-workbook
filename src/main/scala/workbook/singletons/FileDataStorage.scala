package workbook.singletons

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import datastructures.web.file.{FileDescription, LoadedFile}
import datastructures.web.storage.AsyncDataCache
import org.scalajs.dom
import util.web.*

import scala.concurrent.{ExecutionContext, Future, Promise}

case class FileDataStorage() extends AsyncDataCache[FileDescription, LoadedFile]("FileDataStore", false) {

  def load(file: FileDescription)(using ec: ExecutionContext): Future[LoadedFile] = executeLoading(file)(ec)

  override protected def executeLoading(file: FileDescription)(ec: ExecutionContext): Future[LoadedFile] = {
    val data: Future[Array[Byte]] = file match {
      case FileDescription.InternetResourceFileDescription(url, serverLocationDir, nameWithoutExtension, extension, copyrightInfo) => {
        DownloadHelper.fetchUrl(url.href)
      }
      case FileDescription.UploadedResourceFileDescription(file, nameWithoutExtension, extension, copyrightInfo) => {
        DownloadHelper.fetchFile(file)
      }
      case FileDescription.NamedDataFileDescription(nameWithoutExtension, extension, data, copyrightInfo) => {
        Promise.successful(data).future
      }
    }

    val res: Future[LoadedFile] = data.map(data => LoadedFile(file, data))(ec)
    res

  }

  override protected def defaultValueWhileLoading(in: FileDescription): Option[LoadedFile] = None

  override protected def formatInputForLogging(in: FileDescription): String = in.toString

  override protected def formatOutputForLogging(out: LoadedFile): String = out.toString

}
