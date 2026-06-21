package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

object TurtleStitchXmlValidation {

  final case class ValidationResult(errors: Vector[String]) {
    def isValid: Boolean = errors.isEmpty
  }

  def validate(document: TurtleStitchXmlParser.XmlDocument): ValidationResult = {
    val errors = Vector.newBuilder[String]
    if (document.root.name != "project") errors += s"Expected <project> root but found <${document.root.name}>."

    val scenes = child(document.root, "scenes")
    if (scenes.isEmpty) errors += "Missing <scenes> container."
    else if (!childrenNamed(scenes.get, "scene").nonEmpty) errors += "No <scene> entries found."

    ValidationResult(errors.result())
  }

  private def child(parent: TurtleStitchXmlParser.XmlElement, name: String): Option[TurtleStitchXmlParser.XmlElement] =
    parent.children.find(_.name == name)

  private def childrenNamed(parent: TurtleStitchXmlParser.XmlElement, name: String): List[TurtleStitchXmlParser.XmlElement] =
    parent.children.filter(_.name == name)
}
