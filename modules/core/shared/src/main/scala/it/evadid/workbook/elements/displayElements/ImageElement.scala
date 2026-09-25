package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.file.CopyrightInfo.{AuthorNameInfo, CC_LICENCE, UnknownAuthorInfo, UnknownLicence}
import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, UrlFileDescription}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.TypeOfTextDisplay.{URL_RELATIVE_TO_GLOBAL_RESOURCES, URL_RELATIVE_TO_WORKBOOK_RESOURCES, URL_TYPE}
import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}

sealed trait ImageElement extends WorkbookDisplayElement

object ImageElement {

  def apply(elementId: String, fileDescription: FileDescription): ImageElement = FileBasedImageElement(elementId, fileDescription)

  def apply(elementId: String, languageMapContentId: LanguageMapContentId, copyrightInfo: CopyrightInfo, howToResolveUrl: URL_TYPE): ImageElement = LanguageMapBasedImageElement(elementId, languageMapContentId, copyrightInfo, howToResolveUrl)

  case class FileBasedImageElement(override val elementId: String, location: FileDescription) extends ImageElement {
  override val associatedFactory = FileBasedImageElement.factory

     }

  object FileBasedImageElement {
    val factory: WorkbookElementFactory[FileBasedImageElement] = WorkbookElementFactory.simple(
      element => withFileDescription(
        WorkbookElementSerializable(element.elementId, classOf[FileBasedImageElement].getSimpleName, Map()),
        element.location
      ),
      serialized => FileBasedImageElement(serialized.elementId, readFileDescription(serialized))
    )
     }

  case class LanguageMapBasedImageElement(override val elementId: String, languageMapContentId: LanguageMapContentId, copyrightInfo: CopyrightInfo, howToResolveUrl: URL_TYPE) extends ImageElement {
  override val associatedFactory = LanguageMapBasedImageElement.factory

     }

  object LanguageMapBasedImageElement {
    val factory: WorkbookElementFactory[LanguageMapBasedImageElement] = WorkbookElementFactory.simple(
      element => {
        val base = withCopyright(
          WorkbookElementSerializable(element.elementId, classOf[LanguageMapBasedImageElement].getSimpleName, Map())
            .withContentIdAdded("content", element.languageMapContentId),
          element.copyrightInfo
        )
        element.howToResolveUrl match {
          case URL_RELATIVE_TO_GLOBAL_RESOURCES => base.withElementAdded("urlType", "global")
          case URL_RELATIVE_TO_WORKBOOK_RESOURCES(root) =>
            base.withElementAdded("urlType", "workbook").withElementAdded("workbookRoot", root.asUrlString)
        }
      },
      serialized => {
        val urlType = serialized.getElementAsString("urlType") match {
          case "global" => URL_RELATIVE_TO_GLOBAL_RESOURCES
          case "workbook" => URL_RELATIVE_TO_WORKBOOK_RESOURCES(
            UrlFileDescription(serialized.getElementAsString("workbookRoot"), readCopyright(serialized))
          )
          case other => throw new IllegalArgumentException(s"Unknown image URL type '$other'")
        }
        LanguageMapBasedImageElement(serialized.elementId, serialized.getElementAsContentId("content"), readCopyright(serialized), urlType)
      }
    )
   }

  private def withFileDescription(base: WorkbookElementSerializable, file: FileDescription): WorkbookElementSerializable =
    withCopyright(base.withElementAdded("url", file.asUrlString), file.copyrightInfo)

  private def readFileDescription(serialized: WorkbookElementSerializable): FileDescription =
    UrlFileDescription(serialized.getElementAsString("url"), readCopyright(serialized))

  private def withCopyright(base: WorkbookElementSerializable, copyright: CopyrightInfo): WorkbookElementSerializable =
    base
      .withElementAdded("licence", copyright.licenceInfo match {
        case CC_LICENCE => "cc"
        case UnknownLicence => "unknown"
      })
      .withElementAdded("author", copyright.authorInfo.getName.getOrElse(""))

  private def readCopyright(serialized: WorkbookElementSerializable): CopyrightInfo = {
    val licence = serialized.getOptionalElementAsString("licence", "unknown") match {
      case "cc" => CC_LICENCE
      case _ => UnknownLicence
    }
    val author = serialized.getOptionalElementAsString("author", "") match {
      case "" => UnknownAuthorInfo()
      case name => AuthorNameInfo(name)
    }
    CopyrightInfo(licence, author)
  }

}
