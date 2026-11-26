package it.evadid.core.datastructures.file

import upickle.default.*

import java.util.Base64
import scala.concurrent.Future

trait LoadedFile {

  def description: FileDescription

  def data: Array[Byte]

  override def toString: String = "LoadedFile(" + description.toString + ": " + data.length + " bytes)"

  lazy val fileDataAsUtf8String: String = new String(data, "UTF-8")

}

object LoadedFile {

  private case class BasicLoadedFile(description: FileDescription, data: Array[Byte]) extends LoadedFile {

  }

  /** A file received over a transport boundary: it carries its bytes, so it describes and loads itself. */
  private case class TransferredFile(asUrlString: String, copyrightInfo: CopyrightInfo, data: Array[Byte]) extends FileDescription with LoadedFile {
    override def description: FileDescription = this

    override def loadData(): Future[LoadedFile] = Future.successful(this)

    override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = None

    override def toString: String = "TransferredFile(" + asUrlString + ": " + data.length + " bytes)"
  }

  /** Wire form: only the file name (no source location), copyright and Base64-encoded bytes are transferred. */
  private case class LoadedFileJson(filename: String, copyrightInfo: CopyrightInfo, base64Data: String) derives ReadWriter

  given ReadWriter[LoadedFile] = readwriter[LoadedFileJson].bimap[LoadedFile](
    file => LoadedFileJson(file.description.filenameWithExtension, file.description.copyrightInfo, Base64.getEncoder.encodeToString(file.data)),
    json => TransferredFile(json.filename, json.copyrightInfo, Base64.getDecoder.decode(json.base64Data))
  )

  def apply(description: FileDescription, data: Array[Byte]): LoadedFile = BasicLoadedFile(description, data)


}
