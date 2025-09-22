package contentmanagement.model

case class FileInformation(fileName: String, fileType: String, fileData: Array[Byte]) {
  def fullName: String = fileName + "." + fileType

  override def toString: String = fileName + "." + fileType + " (" + fileData.length + " bytes)"

  def fileDataAsString: String = new String(fileData, "UTF-8")
}


object FileInformation {


  def nameParts(fullPath: String): (String, String, String) = {
    val parts = fullPath.split("\\\\")
    val filename: String = parts.last.split("/").last.trim

    val filenameSplitted = filename.split("\\.")

    val extension: String = if (filenameSplitted.contains(".")) filenameSplitted.last.trim else ""

    val nameWithoutExtension = if (filenameSplitted.contains(".")) filename.substring(0, filename.length - extension.length - 1) else filename
    val filePath = fullPath.substring(0, fullPath.length - nameWithoutExtension.length - 1)

    (filePath, nameWithoutExtension, extension)
  }

  def fromPathAndData(fullPath: String, data: Array[Byte]): FileInformation = {
    val parts = nameParts(fullPath)
    FileInformation(parts(1), parts(2), data)
  }

}