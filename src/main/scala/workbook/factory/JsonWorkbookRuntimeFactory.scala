package workbook.factory

import com.raquo.laminar.api.L.{*, given}
import content.WorkbookFactory
import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap}
import datastructures.web.file.FileDescription
import interactionPlugins.slideshow.{SlideDeckExercise, SlidePanel}
import interactionPlugins.turtleStitchPlugin.TurtleStitchExploreProjectExercise
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlMarkdownInstructionElement, HtmlPlaintextInstructionElement, HtmlUnsafeHtmlInstructionElement}
import workbook.htmlElements.interactions.{HtmlBasicCheckboxInteraction, HtmlBasicTextInteraction, HtmlReorderInteraction}
import workbook.htmlElements.container.{HtmlExerciseContainer, HtmlSubContainer}
import workbook.model.{Workbook, WorkbookSection}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import upickle.default.{ReadWriter, macroRW, read}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.Try

case class JsonWorkbookRuntimeFactory(
                                       override val workbookInfo: AllWorkbookInfo,
                                       jsonFactory: JsonWorkbookFactory
                                     ) extends WorkbookFactory {

  override def createWorkbook: Workbook = {
    applyMetadata(jsonFactory.workbookMetadata)

    val withoutDependencies: List[(WorkbookSectionJson, WorkbookSection)] = jsonFactory.workbookContent.sections.map(sectionJson => {
      val containers = sectionJson.sectionContent.map(container =>
        HtmlExerciseContainer.withLevel(workbookInfo, container.elements.map(createWorkbookElement), container.level)
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
        HtmlContainerTitle.withLevel(
          workbookInfo,
          args.getOrElse("languageMapId", throw missingArg("languageMapId", factory)),
          JsonWorkbookRuntimeFactory.parseIntArg(args.get("level"), 2)
        )

      case "HtmlMarkdownInstructionElement" =>
        HtmlMarkdownInstructionElement(workbookInfo, args.getOrElse("languageMapId", throw missingArg("languageMapId", factory)))

      case "HtmlUnsafeHtmlInstructionElement" =>
        HtmlUnsafeHtmlInstructionElement(workbookInfo, args.getOrElse("languageMapId", throw missingArg("languageMapId", factory)))

      case "HtmlPlaintextInstructionElement" =>
        HtmlPlaintextInstructionElement(
          workbookInfo,
          workbookInfo.stringSignalFromLanguageMapId(args.getOrElse("languageMapId", throw missingArg("languageMapId", factory)))(ExecutionContext.global)
        )

      case "HtmlBasicTextInteraction" =>
        HtmlBasicTextInteraction(workbookInfo, args.getOrElse("id", nextId("json-text")))

      case "HtmlBasicCheckboxInteraction" =>
        HtmlBasicCheckboxInteraction(
          workbookInfo,
          args.getOrElse("id", nextId("json-checkbox")),
          args.getOrElse("labelLanguageMapId", throw missingArg("labelLanguageMapId", factory))
        )

      case "HtmlReorderInteraction" =>
        val elements = JsonWorkbookRuntimeFactory.parseStringListArg(args.get("elementsJson"), args.get("elements"))
        HtmlReorderInteraction[String](
          workbookInfo = workbookInfo,
          id = args.getOrElse("id", nextId("json-reorder")),
          elements = elements,
          elementRenderer = snippet => pre(code(snippet))
        )

      case "SlideDeckExercise" =>
        val slides = JsonWorkbookRuntimeFactory.parseSlideSpecs(args.getOrElse("slidesJson", throw missingArg("slidesJson", factory)), factory)
          .map(spec =>
            SlidePanel.imageSlide(
              image = FileDescription.relativeToResourceFolder(spec.imagePath),
              textMapId = spec.textMapId,
              sourceMapId = spec.sourceMapId,
              descriptionMapId = spec.descriptionMapId,
              workbookInfo = workbookInfo
            )
          )
        SlideDeckExercise(
          workbookInfo = workbookInfo,
          id = args.getOrElse("id", nextId("json-slideshow")),
          slides = slides
        )

      case "TurtleStitchExploreProjectExercise" =>
        val path = args.getOrElse("resourcePath", throw missingArg("resourcePath", factory))
        TurtleStitchExploreProjectExercise.createElementLine(workbookInfo, FileDescription.relativeToResourceFolder(path))

      case "HtmlSubContainer" =>
        val serializedChildren = args.getOrElse("childrenJson", throw missingArg("childrenJson", factory))
        val nestedFactories = JsonWorkbookRuntimeFactory.parseNestedElements(serializedChildren, factory)
        HtmlSubContainer.withLevel(
          workbookInfo = workbookInfo,
          children = nestedFactories.map(createWorkbookElement),
          level = JsonWorkbookRuntimeFactory.parseIntArg(args.get("level"), 2)
        )

      case other =>
        HtmlUnsafeHtmlInstructionElement(workbookInfo, Val(s"[Unknown elementName: '$other']"))
    }
  }

  private def missingArg(requiredArg: String, factory: WorkbookElementFactory): IllegalArgumentException =
    IllegalArgumentException(s"Missing arg '$requiredArg' for element '${factory.elementName}'")
}

object JsonWorkbookRuntimeFactory {

  private case class SlideSpecJson(imagePath: String, textMapId: String, sourceMapId: String, descriptionMapId: String)
  private given ReadWriter[SlideSpecJson] = macroRW

  private def parseStringListArg(jsonValue: Option[String], newlineValue: Option[String]): List[String] =
    jsonValue.flatMap(raw => Try(read[List[String]](raw)).toOption)
      .orElse(newlineValue.map(_.split("\n").toList.map(_.trim).filter(_.nonEmpty)))
      .getOrElse(List.empty)

  private def parseSlideSpecs(serializedSlides: String, factory: WorkbookElementFactory): List[SlideSpecJson] =
    Try(read[List[SlideSpecJson]](serializedSlides))
      .getOrElse(throw IllegalArgumentException(s"Invalid slidesJson for element '${factory.elementName}'"))

  private def parseNestedElements(serializedElements: String, factory: WorkbookElementFactory): List[WorkbookElementFactory] =
    Try(read[List[WorkbookElementFactory]](serializedElements))
      .getOrElse(throw IllegalArgumentException(s"Invalid childrenJson for element '${factory.elementName}'"))

  private def parseIntArg(maybeRaw: Option[String], defaultValue: Int): Int =
    maybeRaw.flatMap(raw => Try(raw.trim.toInt).toOption).getOrElse(defaultValue)

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
