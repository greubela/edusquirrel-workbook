package it.evadid.core.datastructures.file

trait LoadedFile {

  def description: FileDescription

  def data: Array[Byte]

  override def toString: String = "LoadedFile(" + description.toString + ": " + data.length + " bytes)"

  lazy val fileDataAsUtf8String: String = new String(data, "UTF-8")

}

object LoadedFile {

  private case class BasicLoadedFile(description: FileDescription, data: Array[Byte]) extends LoadedFile {

  }

  def apply(description: FileDescription, data: Array[Byte]): LoadedFile = BasicLoadedFile(description, data)


}