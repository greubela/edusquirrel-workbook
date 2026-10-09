package it.evadid.homepage.workbook.content

import it.evadid.homepage.control.model.FullInfo

/** Public digital offerings only. Developer demos and PDF-only workbooks are excluded.
  * Native entries also supply the factories used by direct workbook entry pages.
  */
object DigitalWorkbookCatalog {
  case class NativeWorkbook(containerId: String, page: String, factory: FullInfo => WorkbookFactory)
  case class Entry(id: String, author: String, image: String, target: String,
                   native: Option[NativeWorkbook] = None, inProgress: Boolean = false) {
    def titleKey: String = s"workbookSelection/$id-title"
    def descriptionKey: String = s"workbookSelection/$id-description"
    def isExternal: Boolean = target.startsWith("https://")
  }

  private def workbook(id: String, author: String, image: String, container: String,
                       page: String, factory: FullInfo => WorkbookFactory): Entry =
    Entry(id, author, "img/art/mockup/" + image, "../" + page + "/",
      Some(NativeWorkbook(container, page, factory)), inProgress = true)

  val entries: List[Entry] = List(
    workbook("evacuation", "André Greubel", "Evakuierung.jpg", "workbookEvacuation", "evacuationWorkbook", CreateEvacuationWorkbook.apply),
    workbook("plant", "Yanneck Dimitrov", "pflanzengiessen.jpg", "workbookPlantWorkshop", "plantWorkshopWorkbook", info => CreatePlantworkshopWorkbook(info)),
    workbook("blockchain", "Till Favier", "Bitcoin.png", "workbookBlockchain", "blockchainWorkbook", CreateBlockchainWorkbook.apply),
    workbook("images", "Dominic Schattka", "Bilderkennung.png", "workbookImageRecognition", "imageRecognitionWorkbook", CreateImageRecognitionWorkbook.apply),
    workbook("monks", "André Greubel", "monks-workbook.png", "workbookMonks", "monksWorkbook", CreateMonksWorkbook.apply),
    workbook("embroidery", "André Greubel", "Stickmaschine.png", "workbookEmbroidery", "embroideryWorkbook", CreateEmbroideryWorkbook.apply),
    workbook("compression", "Yanneck Dimitrov", "compression-chatgpt.png", "workbookCompression", "compressionWorkbook", CreateCompressionWorkbook.apply),
    workbook("phishing", "Marvin Kretschmer", "Phishing.png", "workbookPhishing", "phishingWorkbook", CreatePhishingWorkbook.apply),
    Entry("scratch", "Hoang Tang Griep", "https://tanghoang.github.io/arbeitsheft-digital-scratch/assets/moving_rohr-B41RzHuV.gif", "https://tanghoang.github.io/arbeitsheft-digital-scratch/chapter/einleitung"),
    Entry("caesar", "Maximilian Droste", "img/art/mockup/caesar-chatgpt.webp", "../../resources/programs/20251113MaxDrostePrimm/index.htm"),
    Entry("qr", "Lucas Reisig", "https://evadid.it/LucasQR/qr_example.png", "https://evadid.it/LucasQR/"),
    Entry("python", "Maximilian Droste", "https://evadid.it/edusquirrel/resources/programs/20260223MaxDrostePrimm/img/turtle-beispielgrafik.jpg", "https://evadid.it/edusquirrel/resources/programs/20260223MaxDrostePrimm/index.htm"),
    Entry("compression-lab", "Yanneck Dimitrov", "img/art/mockup/compression-chatgpt.png", "../../resources/programs/20260907Datenkompression/index.html")
  )

  def nativeWorkbook(containerId: String): Option[NativeWorkbook] =
    entries.flatMap(_.native).find(_.containerId == containerId)
}
