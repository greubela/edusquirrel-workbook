package workbook.factory

import com.raquo.laminar.api.L
import content.WorkbookFactory
import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap}
import datastructures.web.file.FileDescription
import interactionPlugins.turtleStitchPlugin.TurtleStitchExploreProjectExercise
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement, HtmlUnsafeHtmlInstructionElement}
import workbook.htmlElements.interactions.HtmlBasicTextInteraction
import workbook.htmlElements.container.HtmlExerciseContainer
import workbook.model.{Workbook, WorkbookSection}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import scala.collection.mutable
import scala.concurrent.ExecutionContext

case class JsonWorkbookRuntimeFactory(
                                       override val workbookInfo: AllWorkbookInfo,
                                       jsonFactory: JsonWorkbookFactory
                                     ) extends WorkbookFactory {

  override def createWorkbook: Workbook = {
    applyMetadata(jsonFactory.workbookMetadata)

    val withoutDependencies: List[(WorkbookSectionJson, WorkbookSection)] = jsonFactory.workbookContent.sections.map(sectionJson => {
      val containers = sectionJson.sectionContent.map(container =>
        HtmlExerciseContainer(workbookInfo, container.elements.map(createWorkbookElement))
      )

      val sec = WorkbookSection(
        workbookInfo = workbookInfo,
        sectionTitle = LanguageMap.mapBasedLanguageMap(Map.empty),
        sectionContent = containers,
        sectionsRequiredBefore = List.empty,
        sectionsRecommendedBefore = List.empty,
        sectionTitleLanguageMapId = Some(sectionJson.sectionTitleMapId)
      )
      sectionJson -> sec
    })

    val byId = withoutDependencies.map(_._1.sectionId).zip(withoutDependencies.map(_._2)).toMap

    val allSections: List[WorkbookSection] = withoutDependencies.map((sectionJson, section) =>
      section.copy(
        sectionsRequiredBefore = sectionJson.sectionsRequiredBefore.flatMap(byId.get)
      )
    )

    val wb = Workbook(workbookInfo, jsonFactory.workbookMetadata.titleMapId, allSections)
    allSections.headOption.foreach(first => workbookInfo.updateConfig(_.copy(activeSection = Some(first))))
    JsonWorkbookRuntimeFactory.register(wb, jsonFactory)
    wb
  }

  private def applyMetadata(metadata: WorkbookMetadataJson): Unit = {
    workbookInfo.addLanguageFiles(metadata.languageMapFiles.map(path => FileDescription.relativeToResourceFolder(path)))

    val available = metadata.availableLanguages.flatMap(JsonWorkbookRuntimeFactory.parseHumanLanguage)
    val defaultLanguage = JsonWorkbookRuntimeFactory.parseHumanLanguage(metadata.defaultLanguage).getOrElse(AppLanguage.default())

    workbookInfo.workbookInfoVar.update(cur => cur.copy(
      availableLanguages = if (available.nonEmpty) available else cur.availableLanguages,
      config = cur.config.copy(currentWorkbookLanguage = defaultLanguage)
    ))
  }

  private def createWorkbookElement(factory: WorkbookElementFactory): HtmlWorkbookElement = {
    val args = factory.factoryArgs

    factory.elementName match {
      case "HtmlContainerTitle" =>
        HtmlContainerTitle(workbookInfo, args.getOrElse("languageMapId", throw missingArg("languageMapId", factory)))

      case "HtmlUnsafeHtmlInstructionElement" =>
        HtmlUnsafeHtmlInstructionElement(workbookInfo, args.getOrElse("languageMapId", throw missingArg("languageMapId", factory)))

      case "HtmlPlaintextInstructionElement" =>
        HtmlPlaintextInstructionElement(
          workbookInfo,
          workbookInfo.stringSignalFromLanguageMapId(args.getOrElse("languageMapId", throw missingArg("languageMapId", factory)))(ExecutionContext.global)
        )

      case "HtmlBasicTextInteraction" =>
        HtmlBasicTextInteraction(workbookInfo, args.getOrElse("id", nextId("json-text")))

      case "TurtleStitchExploreProjectExercise" =>
        val path = args.getOrElse("resourcePath", throw missingArg("resourcePath", factory))
        TurtleStitchExploreProjectExercise.createElementLine(workbookInfo, FileDescription.relativeToResourceFolder(path))

      case other =>
        HtmlUnsafeHtmlInstructionElement(workbookInfo, L.Val(s"[Unknown elementName: '$other']"))
    }
  }

  private def missingArg(requiredArg: String, factory: WorkbookElementFactory): IllegalArgumentException =
    IllegalArgumentException(s"Missing arg '$requiredArg' for element '${factory.elementName}'")
}

object JsonWorkbookRuntimeFactory {

  private val sourceJsonByWorkbookIdentity: mutable.HashMap[Int, JsonWorkbookFactory] = mutable.HashMap.empty

  def fromJson(workbookInfo: AllWorkbookInfo, json: String): JsonWorkbookRuntimeFactory =
    JsonWorkbookRuntimeFactory(workbookInfo, JsonWorkbookFactory.fromJson(json))

  def toJsonString(workbook: Workbook, pretty: Boolean = true): Option[String] =
    sourceJsonByWorkbookIdentity.get(System.identityHashCode(workbook)).map(_.toJson(pretty))

  private[factory] def register(workbook: Workbook, source: JsonWorkbookFactory): Unit = {
    sourceJsonByWorkbookIdentity.put(System.identityHashCode(workbook), source)
  }

  private def parseHumanLanguage(str: String): Option[HumanLanguage] = str.trim.toLowerCase match {
    case "en" | "english" => Some(AppLanguage.English)
    case "de" | "german" => Some(AppLanguage.German)
    case "ua" | "uk" | "ukrainian" => Some(AppLanguage.Ukrainian)
    case "dk" | "da" | "danish" => Some(AppLanguage.Danish)
    case "tr" | "turkish" => Some(AppLanguage.Turkish)
    case "fr" | "french" => Some(AppLanguage.French)
    case "ru" | "russian" => Some(AppLanguage.Russian)
    case "es" | "spanish" => Some(AppLanguage.Spanish)
    case _ => None
  }
}
