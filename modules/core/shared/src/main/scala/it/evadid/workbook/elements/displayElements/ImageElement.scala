package it.evadid.workbook.elements.displayElements
import it.evadid.core.datastructures.file.{CopyrightInfo, FileDescription, LoadedFile}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_TYPE
import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import scala.concurrent.Future
sealed trait ImageElement extends WorkbookDisplayElement
object ImageElement {
  def apply(elementId: String, fileDescription: FileDescription): ImageElement = FileBasedImageElement(elementId, fileDescription)
  def apply(elementId: String, languageMapContentId: LanguageMapContentId, copyrightInfo: CopyrightInfo, howToResolveUrl: URL_TYPE): ImageElement = LanguageMapBasedImageElement(elementId, languageMapContentId, copyrightInfo, howToResolveUrl)
  case class FileBasedImageElement(override val elementId: String, location: FileDescription) extends ImageElement { override def toSerializableType = toFactoryBase.withElementAdded("url", location.asUrlString) }
  object FileBasedImageElement { def fromFactory(f: WorkbookElementFactory): FileBasedImageElement = FileBasedImageElement(f.elementId, UrlOnlyFileDescription(f.getElementAsString("url"))) }
  case class LanguageMapBasedImageElement(override val elementId: String, languageMapContentId: LanguageMapContentId, copyrightInfo: CopyrightInfo, howToResolveUrl: URL_TYPE) extends ImageElement { override def toSerializableType = toFactoryBase.withContentIdAdded("content", languageMapContentId).withElementAdded("urlType", howToResolveUrl.toString) }
  object LanguageMapBasedImageElement { def fromFactory(f: WorkbookElementFactory): LanguageMapBasedImageElement = throw new UnsupportedOperationException("Language-map image URL resolver must be supplied by the importing application.") }
  private case class UrlOnlyFileDescription(url: String) extends FileDescription { val copyrightInfo = CopyrightInfo.unknownCopyrightInfo; def asUrlString = url; def loadData(): Future[LoadedFile] = Future.failed(new UnsupportedOperationException("Serialized image data is unavailable.")); def getChildrenFile(childName: String, c: CopyrightInfo) = None }
}
