package it.evadid.homepage.workbook.legacy.singletons

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import org.scalajs.dom
import todomove.datastructures.web.storage.AsyncDataCache

import scala.concurrent.{ExecutionContext, Future, Promise}

case class FileDataStorage() extends AsyncDataCache[FileDescription, LoadedFile]("FileDataStore", false) {

  def load(file: FileDescription)(using ec: ExecutionContext): Future[LoadedFile] = file.loadData()

  override protected def executeLoading(file: FileDescription)(ec: ExecutionContext): Future[LoadedFile] = {
    file.loadData()
  }

  override protected def formatInputForLogging(in: FileDescription): String = in.toString

  override protected def formatOutputForLogging(out: LoadedFile): String = out.toString

}
