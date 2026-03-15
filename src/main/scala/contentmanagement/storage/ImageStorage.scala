package contentmanagement.storage

/*
import com.raquo.airstream.state.Var
import contentmanagement.model.file.{FullImage, ImageDescription, LoadedFile, OldFullImage}
import contentmanagement.model.file.ImageDescription.{UrlImageDescription, SvgImageDescription, UploadImageDescription}
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.html.Image
import util.TypeConversion
import util.web.FileIO

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.util.{Failure, Success}

case class ImageStorage() extends DataStorage[ImageDescription, FullImage]("image storage", false) {
  
  
  
  
  
}
*/

/*
// todo: refactore to use DataStorage (and not automatically load dom images)
object ImageStorage {

  private val debug: Boolean = false

  private val fullImageMap: mutable.HashMap[ImageDescription, FullImage] = new mutable.HashMap(50, 0.25)
  private val currentlyLoading: mutable.HashMap[ImageDescription, (Long, Future[FullImage])] = new mutable.HashMap()

  def allImagesInStorage: List[ImageDescription] = (currentlyLoading.keys.toSet ++ fullImageMap.keys.toSet).toList

  def allLoadedImages: List[ImageDescription] = fullImageMap.keys.toList

  private var cache_hits: Long = 0
  private var cache_misses: Long = 0

  private def log(str: String): Unit = if (debug) {
    println("[IMAGES] " + str
      + "\n    cache: " + cache_hits + " hits, " + cache_misses + " misses"
      + "\n    cache size: " + fullImageMap.size + ", currently loading: " + currentlyLoading.size
      + "\n    currently loading: " + currentlyLoading.keys.toList)
  }

  def deleteFromStorage(toDelete: List[ImageDescription] = List()): Unit = {
    toDelete.foreach(desc => fullImageMap.remove(desc))
  }

  private def calcDataSourceString(imageDescription: ImageDescription): Future[String] = {

    def fileInfoToString(imgData: FileInformation): String = {
      val b64str = TypeConversion.byteArrayToBase64String(imgData.fileData)
      "data:image/" + imgData.fileType + ";base64, " + b64str
    }

    imageDescription match {
      case ServerImageDescription(path) => FileIO.fetchUrl(path).map(fileInfoToString)
      case UploadImageDescription(file) => FileIO.fetchFile(file).map(fileInfoToString)
      case SvgImageDescription(name, svgString) => Future("data:image/svg+xml;charset=utf-8," + js.URIUtils.encodeURIComponent(svgString))
      //case _ => Future.failed(new IllegalArgumentException("Unknown type of imageDescription: " + imageDescription))
    }
  }


  private def loadDomImage(imageDescription: ImageDescription, imgSrc: String): Future[FullImage] = {
    val img: Image = document.createElement("img").asInstanceOf[Image]
    img.setAttribute("src", imgSrc)

    // Base64IO.encode(imgData.fileData)
    val p = Promise[FullImage]
    img.onabort = (e: dom.Event) => p.failure(new Exception("cannot create dom image: " + e.toString))
    img.onload = (e: dom.Event) => p.success(FullImage(imageDescription, imgSrc, img))
    p.future
  }
  
  

  private def executeLoading(imageDescription: ImageDescription)(implicit ec: ExecutionContext): Future[FullImage] = {

    val dataSourceFuture = calcDataSourceString(imageDescription)

    val fullImageFuture: Future[FullImage] = dataSourceFuture.transformWith {
      case Success(imgString) => loadDomImage(imageDescription, imgString)
      case Failure(exception) => Promise[FullImage].failure(exception).future
    }

    currentlyLoading.put(imageDescription, (System.currentTimeMillis(), fullImageFuture))

    fullImageFuture.onComplete {
      case Success(fullImage) => {
        fullImageMap.put(imageDescription, fullImage)

        val timeDiff = System.currentTimeMillis() - currentlyLoading(imageDescription)._1
        log("loaded full image for: " + imageDescription + " in " + timeDiff + "ms")
        currentlyLoading.remove(imageDescription)
      }
      case Failure(err) => {
        currentlyLoading.remove(imageDescription)
        log("failed to load full image for: " + imageDescription + ", reason: " + err)
      }
    }

    fullImageFuture
  }

  def loadFullImageIntoVar(imageDescription: ImageDescription)(implicit ec: ExecutionContext): Var[Option[FullImage]] = {

    val loadedImage: Var[Option[FullImage]] = Var(None)
    
    loadFullImage(imageDescription).onComplete {
      case Success(fullImg) => loadedImage.update(_ => Some(fullImg))
      case Failure(error) => println("ERROR at loading img :-(")
    }(ec)
    
    loadedImage
  }
  
  def loadFullImage(imageDescription: ImageDescription)(implicit ec: ExecutionContext): Future[FullImage] =
    if (fullImageMap.contains(imageDescription)) {
      cache_hits = cache_hits + 1
      log("cache hit :) ! image: " + imageDescription)
      Future(fullImageMap(imageDescription))
    } else if (currentlyLoading.contains(imageDescription)) {
      cache_hits = cache_hits + 1
      log("loading hit :) image: " + imageDescription)
      currentlyLoading(imageDescription)._2
    } else {
      cache_misses = cache_misses + 1
      log("cache miss :( image: " + imageDescription)
      executeLoading(imageDescription)(ec)
    }


  def startLoading(images: List[ImageDescription])(implicit ec: ExecutionContext): Unit = loadBulk(images)(ec)


  def loadBulk(imageList: List[ImageDescription])(implicit ec: ExecutionContext): Future[Map[ImageDescription, FullImage]] = {

    val images: List[ImageDescription] = imageList.toSet.toList

    val promise = Promise[Map[ImageDescription, FullImage]]

    val resMap: mutable.HashMap[ImageDescription, FullImage] = new mutable.HashMap[ImageDescription, FullImage](images.length, 0.25)
    val failureList = mutable.ListBuffer[(ImageDescription, Throwable)]()

    def failureString(): String = failureList.map((desc, err) => {
      val stackTrace: Array[StackTraceElement] = err.getStackTrace
      "    [ERROR] at " + desc + " in method " + stackTrace(0).getMethodName + ": " + err.getMessage
    }).fold("")((x, y) => {
      x + "\n" + y
    })


    def checkFinished(): Unit = {
      if (resMap.size + failureList.size == images.length) {
        if (failureList.nonEmpty) {
          promise.failure(new Exception("Could not load " + failureList.size + " images:\n" + failureString()))
        }
        else {
          promise.success(resMap.toMap)
        }
      }
    }

    images.foreach(imageDesc => {
      val fullImageFuture = loadFullImage(imageDesc)(ec)
      fullImageFuture.onComplete {
        case Success(fullImage) =>
          resMap.put(imageDesc, fullImage)
          checkFinished()
        case Failure(exception) =>
          val element: (ImageDescription, Throwable) = (imageDesc, exception)
          failureList.append(element)
          checkFinished()
      }
    })

    checkFinished()

    promise.future
  }



}*/




