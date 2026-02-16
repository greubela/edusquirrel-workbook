package workbook.model.history.sync

import org.scalajs.dom
import org.scalajs.dom.Storage
import workbook.model.history.sync.ExerciseVariableSyncSource


object LocalStorageSync extends ExerciseVariableSyncSource {

  private val storage: Storage = dom.window.localStorage

  override def syncTo(key: String, value: String): Unit = {
    storage.setItem(key, value)
  }

  override def syncAllFrom(): Map[String, String] = {
    (0 until storage.length)
      .flatMap { i =>
        Option(storage.key(i)).flatMap { key =>
          Option(storage.getItem(key)).map(value => key -> value)
        }
      }
      .toMap
  }

  override def syncKeyFrom(key: String): Option[String] = syncAllFrom().get(key)
}
