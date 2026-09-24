package it.evadid.workbook.elements.displayElements

import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, LoadedFile}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_TYPE
import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

import scala.concurrent.Future

sealed trait ImageElement extends WorkbookDisplayElement

object ImageElement {
  private case class UrlOnlyFileDescription(url: String) extends FileDescription {
    override val copyrightInfo: CopyrightInfo = CopyrightInfo.unknownCopyrightInfo
    override def asUrlString: String = url
    override def loadData(): Future[LoadedFile] = Future.failed(UnsupportedOperationException(s"No loader available for $url"))
    override def getChildrenFile(childName: String, cCopyrightInfo: CopyrightInfo): Option[FileDescription] = None
  }

  def apply(elementId: String, fileDescription: FileDescription): ImageElement = FileBasedImageElement(elementId, fileDescription)

  def apply(elementId: String, languageMapContentId: LanguageMapContentId, copyrightInfo: CopyrightInfo, howToResolveUrl: URL_TYPE): ImageElement = LanguageMapBasedImageElement(elementId, languageMapContentId, copyrightInfo, howToResolveUrl)

  case class FileBasedImageElement(override val elementId: String, location: FileDescription) extends ImageElement {
    override def toSerializableType: WorkbookElementSerializable = toFactoryBase.withElementAdded("url", location.asUrlString)
  }

  object FileBasedImageElement {
    def fromFactory(f: WorkbookElementSerializable): FileBasedImageElement = FileBasedImageElement(f.elementId, UrlOnlyFileDescription(f.getElementAsString("url")))
  }

  case class LanguageMapBasedImageElement(override val elementId: String, languageMapContentId: LanguageMapContentId, copyrightInfo: CopyrightInfo, howToResolveUrl: URL_TYPE) extends ImageElement {
    override def toSerializableType: WorkbookElementSerializable = toFactoryBase.withContentIdAdded("content", languageMapContentId).withElementAdded("urlType", howToResolveUrl.toString)
  }

  object LanguageMapBasedImageElement {
    def fromFactory(f: WorkbookElementSerializable): LanguageMapBasedImageElement = throw new UnsupportedOperationException("Language-map image URL resolver must be supplied by the importing application.")
  }


}
