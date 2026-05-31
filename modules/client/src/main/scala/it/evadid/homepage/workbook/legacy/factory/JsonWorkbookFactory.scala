package it.evadid.homepage.workbook.legacy.factory





/*
import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
import upickle.default.{ReadWriter, macroRW, read, write}
/**
 * Serializable DTO model for building workbooks from JSON.
 *
 * This file intentionally focuses on the data model + json (de)serialization
 * support. Conversion into workbook.model.Workbook is planned as a next step.
 */
case class JsonWorkbookFactory(
                                workbookMetadata: WorkbookMetadataJson,
                                workbookContent: WorkbookContentJson
                              ) {

  def toJson(pretty: Boolean = true): String =
    if (pretty) write(this, indent = 2) else write(this)
}

object JsonWorkbookFactory {
  given ReadWriter[JsonWorkbookFactory] = macroRW

  def fromJson(json: String): JsonWorkbookFactory = read[JsonWorkbookFactory](json)
}

case class WorkbookMetadataJson(
                                 id: String,
                                 availableLanguages: List[String],
                                 defaultLanguage: String,
                                 languageMapFiles: List[String],
                                 titleMapId: String,
                                 estimatedInteractionDurationSeconds: Map[String, Double] = Map.empty
                               )

object WorkbookMetadataJson {
  given ReadWriter[WorkbookMetadataJson] = macroRW
}

case class WorkbookContentJson(
                                sections: List[WorkbookSectionJson]
                              )

object WorkbookContentJson {
  given ReadWriter[WorkbookContentJson] = macroRW
}

case class WorkbookSectionJson(
                                sectionId: String,
                                sectionTitleMapId: String,
                                sectionsRequiredBefore: List[String] = List.empty,
                                sectionContent: List[ExerciseContainerJson]
                              )

object WorkbookSectionJson {
  given ReadWriter[WorkbookSectionJson] = macroRW
}

case class ExerciseContainerJson(
                                  exerciseId: String,
                                  elements: List[WorkbookElementFactory],
                                  level: Int = 1
                                )

object ExerciseContainerJson {
  given ReadWriter[ExerciseContainerJson] = macroRW
}

/**
 * Generic, serializable factory call descriptor.
 *
 * Example:
 *   WorkbookElementFactory(
 *     elementName = "HtmlUnsafeHtmlInstructionElement",
 *     factoryArgs = Map("languageMapId" -> "CustomWorkbook/Ex1Instr2b")
 *   )
 */
case class WorkbookElementFactory(
                                   elementName: String,
                                   factoryArgs: Map[String, String]
                                 )

object WorkbookElementFactory {
  given ReadWriter[WorkbookElementFactory] = macroRW
}
*/