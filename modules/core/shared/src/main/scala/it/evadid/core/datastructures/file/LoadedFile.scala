package it.evadid.core.datastructures.file

import upickle.default.*

import java.util.Base64

trait LoadedFile {

  def description: FileDescription

  def data: Array[Byte]

  override def toString: String = "LoadedFile(" + description.toString + ": " + data.length + " bytes)"

  lazy val fileDataAsUtf8String: String = new String(data, "UTF-8")

}

object LoadedFile {

  private case class BasicLoadedFile(description: FileDescription, data: Array[Byte]) extends LoadedFile {

  }

  /** Wire form: only the file name (no source location), copyright and Base64-encoded bytes are transferred. */
  private case class LoadedFileJson(filename: String, copyrightInfo: CopyrightInfo, base64Data: String) derives ReadWriter

  given ReadWriter[LoadedFile] = readwriter[LoadedFileJson].bimap[LoadedFile](
    file => LoadedFileJson(file.description.filenameWithExtension, file.description.copyrightInfo, Base64.getEncoder.encodeToString(file.data)),
    json => NamedDataFileDescription(json.filename, Base64.getDecoder.decode(json.base64Data), json.copyrightInfo)
  )

  def apply(description: FileDescription, data: Array[Byte]): LoadedFile = BasicLoadedFile(description, data)


}
