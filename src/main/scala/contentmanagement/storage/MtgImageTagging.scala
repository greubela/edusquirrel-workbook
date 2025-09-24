package contentmanagement.storage

import contentmanagement.model.image.ImageTag.{LicenceType, UNKNOWN_LICENCE}
import contentmanagement.model.image.{ImageDescription, ImageTag}

import scala.collection.mutable

object MtgImageTagging {

  private val imageTags: mutable.HashMap[ImageTag, mutable.Set[ImageDescription]] = mutable.HashMap.empty

  private def ensureTagSet(tag: ImageTag): mutable.Set[ImageDescription] =
    imageTags.getOrElseUpdate(tag, mutable.HashSet.empty)

  def removeImages(descriptions: Iterable[ImageDescription]): Unit = {
    descriptions.foreach(removeImage)
  }

  private def removeImage(desc: ImageDescription): Unit =
    imageTags.foreach { case (_, descriptions) => descriptions -= desc }

  def getAllTagsForImage(image: ImageDescription): List[ImageTag] =
    imageTags.collect { case (key, descriptions) if descriptions.contains(image) => key }.toList

  def addTagToImages(tag: ImageTag, images: Iterable[ImageDescription]): Unit =
    images.foreach(addTagToImage(tag, _))

  def addTagsToImages(tags: Iterable[ImageTag], images: Iterable[ImageDescription]): Unit =
    tags.foreach(addTagToImages(_, images))

  def getLicenceInformation(image: ImageDescription): LicenceType =
    getAllTagsForImage(image).collectFirst { case tag: LicenceType => tag }.getOrElse(UNKNOWN_LICENCE)

  def addTagToImage(tag: ImageTag, image: ImageDescription): Unit = {
    ensureTagSet(tag) += image
  }

  def getAllImagesWithTag(tag: ImageTag): Set[ImageDescription] = ensureTagSet(tag).toSet


}
