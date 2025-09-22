package contentmanagement.storage

import contentmanagement.model.image.ImageTag.{LicenceType, UNKNOWN_LICENCE}
import contentmanagement.model.image.{ImageDescription, ImageTag}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object MtgImageTagging {


  private val imageTags: mutable.HashMap[ImageTag, mutable.Set[ImageDescription]] = new mutable.HashMap()

  private def ensureTagSet(tag: ImageTag): Unit = if (!imageTags.contains(tag)) {
    imageTags.put(tag, new mutable.HashSet())
  }

  def removeImages(descriptions: Iterable[ImageDescription]): Unit = {
    descriptions.foreach(removeImage)
  }

  private def removeImage(desc: ImageDescription): Unit = {
    imageTags.keys.foreach(imgTag => {
      if (imageTags(imgTag).contains(desc)) {
        imageTags(imgTag).remove(desc)
      }
    })
  }
  def getAllTagsForImage(image: ImageDescription): List[ImageTag] = {
    val keyList: mutable.ListBuffer[ImageTag] = new ListBuffer[ImageTag]()
    imageTags.keys.foreach(key => {
      if (imageTags(key).contains(image)) {
        keyList += key
      }
    })
    keyList.toList
  }

  def addTagToImages(tag: ImageTag, images: Iterable[ImageDescription]): Unit = {
    images.foreach(curImg => addTagToImage(tag, curImg))
  }

  def addTagsToImages(tags: Iterable[ImageTag], images: Iterable[ImageDescription]): Unit = {
    tags.foreach(curTag => addTagToImages(curTag, images))
  }

  def getLicenceInformation(image: ImageDescription): LicenceType = {
    getAllTagsForImage(image).filter(tag => tag.isInstanceOf[LicenceType]).map(_.asInstanceOf[LicenceType]).headOption.getOrElse(UNKNOWN_LICENCE)
  }

  def addTagToImage(tag: ImageTag, image: ImageDescription): Unit = {
    ensureTagSet(tag)
    imageTags(tag) += image
  }

  def getAllImagesWithTag(tag: ImageTag): Set[ImageDescription] = {
    ensureTagSet(tag)
    imageTags(tag).toSet
  }


}
