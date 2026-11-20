package it.evadid.vm.projectexport

import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime

/** A text file of an exported project, relative to the project folder. */
final case class ProjectFile(path: String, content: String) {
  require(path.nonEmpty && !path.startsWith("/") && !path.endsWith("/") && !path.split('/').contains(".."), s"Invalid project path: $path")
}

/**
 * Files to continue a workbook program in another IDE. `folder` is the project directory inside the
 * archive, so unpacking yields one folder that the IDE opens (for BlueJ: the folder with `package.bluej`).
 */
final case class ExportedProject(folder: String, files: Seq[ProjectFile]) {
  require(folder.nonEmpty && !folder.contains('/'), s"Invalid project folder: $folder")
  require(files.map(_.path).distinct.size == files.size, "Project file paths must be unique.")

  def fileName: String = s"$folder.zip"

  def zip(modified: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)): Array[Byte] = {
    val folders = files.flatMap(file => file.path.split('/').init.inits.toSeq.filter(_.nonEmpty).map(_.mkString("", "/", "/"))).distinct.sorted
    ZipArchive.write(
      ZipArchive.Entry(s"$folder/", Array.emptyByteArray) +:
        (folders.map(path => ZipArchive.Entry(s"$folder/$path", Array.emptyByteArray)) ++
          files.map(file => ZipArchive.Entry(s"$folder/${file.path}", file.content.getBytes(UTF_8)))),
      modified)
  }
}

object ExportedProject {
  private val transliterations = Map('ä' -> "ae", 'ö' -> "oe", 'ü' -> "ue", 'Ä' -> "Ae", 'Ö' -> "Oe", 'Ü' -> "Ue", 'ß' -> "ss",
    'å' -> "aa", 'Å' -> "Aa", 'æ' -> "ae", 'Æ' -> "Ae", 'ø' -> "oe", 'Ø' -> "Oe")

  /** A folder name that unpacks the same everywhere: ASCII letters, digits, `-` and `_`. */
  def folderName(title: String): String = {
    val ascii = title.trim.flatMap(c => transliterations.getOrElse(c, c.toString))
    val cleaned = ascii.map(c => if (c.isLetterOrDigit && c < 128) || c == '-' || c == '_' then c else '_')
      .replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")
    if cleaned.isEmpty then "project" else cleaned
  }
}
