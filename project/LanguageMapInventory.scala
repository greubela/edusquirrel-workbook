import sbt._

/** Compile the actual resource inventory so browsers never probe absent translations. */
object LanguageMapInventory {
  def generate(root: File, managed: File): Seq[File] = {
    val base = root / "resources" / "languageMaps" / "eva"
    val filename = "map-([a-z]+)\\.json".r
    val entries = IO.listFiles(base).filter(_.isDirectory).sortBy(_.getName).map { directory =>
      val suffixes = IO.listFiles(directory).toList.flatMap { file => file.getName match {
        case filename(suffix) => Some("\"" + suffix + "\"")
        case _ => None
      }}.sorted
      "    \"" + directory.getName + "\" -> Set(" + suffixes.mkString(", ") + ")"
    }
    val source = """package it.evadid.homepage.control.change

private[change] object AvailableLanguageMaps {
  val suffixesByDirectory: Map[String, Set[String]] = Map(
""" + entries.mkString(",\n") + "\n  )\n}\n"
    val output = managed / "it" / "evadid" / "homepage" / "control" / "change" / "AvailableLanguageMaps.scala"
    if (!output.exists || IO.read(output) != source) IO.write(output, source)
    Seq(output)
  }
}
