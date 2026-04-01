package datastructures.web.file

import util.web.DownloadHelper

case class LoadedFile(description: FileDescription, data: Array[Byte]) {

  override def toString: String = "LoadedFile(" + description.toString + ": " + data.length + " bytes)"

  lazy val fileDataAsUtf8String: String = new String(data, "UTF-8")

  def toImage: FullImage = FullImage.LoadedFileImage(this)

  def download(): Unit = DownloadHelper.downloadFile(description.filename, data)
  
}

