package it.evadid.core.datastructures.chat

import it.evadid.core.datastructures.language.AppLanguage.German
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import munit.FunSuite
import scala.scalajs.js
import upickle.default.*

class MessageTimestampSpec extends FunSuite {
  test("saved feedback retains message timestamps when writing and reading cross a clock tick") {
    val date = js.Dynamic.global.Date
    val originalNow = date.selectDynamic("now")
    val instant = 1792886400000.0
    try {
      date.updateDynamic("now")((() => instant): js.Function0[Double])
      val conversation = MessengerModel(List(Message("Hint", MessengerModel.pAgent)))
      val result = ScaffoldingResult("answer", LanguageMap.mapBasedLanguageMap(Map(German -> "Hinweis")),
        FeedbackEntity.LlmEntity(conversation), Some(conversation))
      val saved = write(result)
      date.updateDynamic("now")((() => instant + 1000): js.Function0[Double])
      assertEquals(read[ScaffoldingResult[String, MessengerModel]](saved), result)
    } finally date.updateDynamic("now")(originalNow)
  }
}
