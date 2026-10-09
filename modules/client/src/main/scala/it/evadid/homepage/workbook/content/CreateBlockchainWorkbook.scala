package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.table.*
import it.evadid.workbook.elements.interactionElements.blockchain.*
import it.evadid.workbook.model.blockchain.SquareMiddleHash
import it.evadid.workbook.elements.structureElements.Workbook

/** Partial migration of the entry activities (pp. 1–2) and Hashq (pp. 14–15). */
case class CreateBlockchainWorkbook(fullInfo: FullInfo) extends WorkbookFactory {
  override val workbookId = "workbookBlockchain"
  override def createWorkbook: Workbook = workbook("blockchainworkbook/title", List(
    section("blockchain-introduction", "blockchainworkbook/introTitle", List(container("blockchainworkbook/introTitle", List(
      instructionPlaintext("blockchainworkbook/scope"),
      instructionMarkdown("blockchainworkbook/videoTask"), createTextInput("blockchain-video-summary"),
      instructionPlaintext("blockchainworkbook/videoOpinion"), createTextInput("blockchain-video-opinion"),
      instructionPlaintext("blockchainworkbook/claimsTask"), CreateBlockchainWorkbook.claimsTable
    )))),
    section("blockchain-square-middle", "blockchainworkbook/hashTitle", List(container("blockchainworkbook/hashTitle", List(
      instructionPlaintext("blockchainworkbook/hashExplanation"),
      instructionPlaintext("blockchainworkbook/boundaryTask"), createTextInput("blockchain-hash-boundary"),
      SquareMiddleHashInteraction("blockchain-hash-explore", LanguageMapContentId("blockchainworkbook/exploreTitle"),
        initial = SquareMiddleHashAnswer("57", "23")),
      instructionPlaintext("blockchainworkbook/hashTableTask"), CreateBlockchainWorkbook.hashTable,
      SquareMiddleHashInteraction("blockchain-hash-collision", LanguageMapContentId("blockchainworkbook/collisionTitle"), FindHashCollision()),
      SquareMiddleHashInteraction("blockchain-hash-preimage-22", LanguageMapContentId("blockchainworkbook/preimage22Title"), FindHashPreimage("22")),
      SquareMiddleHashInteraction("blockchain-hash-preimage-77", LanguageMapContentId("blockchainworkbook/preimage77Title"), FindHashPreimage("77")),
      instructionPlaintext("blockchainworkbook/hashReflection"), createTextInput("blockchain-hash-reflection")
    ))))
  ), User("Till Favier", "author-blockchain-till-favier", ""))
}
object CreateBlockchainWorkbook {
  private def id(key: String) = LanguageMapContentId(s"blockchainworkbook/$key")
  val claimsTable = AnswerTableInteraction("blockchain-claims", id("claimsTitle"),
    List(id("claim1"), id("claim2"), id("claim3"), id("own1"), id("own2")),
    List(id("claim"), id("explanation"), id("assessment")),
    List("anonymous", "fast", "decentralized").map(key =>
      List(FixedTableCell(id(key)), EditableTableCell(), EditableTableCell())) ++
      List.fill(2)(List.fill(3)(EditableTableCell())))
  val sourceInputs: List[Int] = List(42, 99, 13)
  val hashTable = AnswerTableInteraction("blockchain-hash-table", id("hashTableTitle"),
    sourceInputs.map(n => id(s"number$n")), List(id("square"), id("hash")),
    sourceInputs.map(n => {
      val result = SquareMiddleHash.calculate(BigInt(n))
      List(EditableTableCell(Some(List(result.square.toString))), EditableTableCell(Some(List(result.hash))))
    }))
}
