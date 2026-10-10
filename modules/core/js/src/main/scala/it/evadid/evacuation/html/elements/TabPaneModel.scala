package it.evadid.evacuation.html.elements

import it.evadid.evacuation.core.datastructures.utility.ObservableVar
import it.evadid.evacuation.html.elements.TabPaneModel.TabProperty

case class TabPaneModel(tabs: List[TabProperty]) derives upickle.default.ReadWriter {

  val currentTab: ObservableVar[TabProperty] = new ObservableVar[TabProperty](tabs.head)

}

object TabPaneModel {

  case class TabProperty(id: String, description: String) derives upickle.default.ReadWriter

}

