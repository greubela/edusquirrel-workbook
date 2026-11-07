package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.chat.MessengerModel
import upickle.default.*

case class AdditionalInfoLlmScaffolding(
                                         completeHistory: MessengerModel
                                       ) derives ReadWriter {

}
