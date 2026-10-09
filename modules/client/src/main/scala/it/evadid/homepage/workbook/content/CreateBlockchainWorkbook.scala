package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.table.*
import it.evadid.workbook.elements.interactionElements.blockchain.*
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.model.blockchain.*
import it.evadid.workbook.elements.structureElements.Workbook

/** Adaptation of learner activities through p. 29; content-equivalence review remains outstanding. */
case class CreateBlockchainWorkbook(fullInfo: FullInfo) extends WorkbookFactory {
  override val workbookId = "workbookBlockchain"
  private def id(key: String) = LanguageMapContentId(s"blockchainworkbook/$key")
  private def reflections(keys: String*) = keys.toList.flatMap(key => List(
    instructionPlaintext(s"blockchainworkbook/$key"), createTextInput(s"blockchain-$key")))
  override def createWorkbook: Workbook = workbook("blockchainworkbook/title", List(
    section("blockchain-introduction", "blockchainworkbook/introTitle", List(container("blockchainworkbook/introTitle", List(
      instructionPlaintext("blockchainworkbook/scope"),
      instructionMarkdown("blockchainworkbook/videoTask"), createTextInput("blockchain-video-summary"),
      instructionPlaintext("blockchainworkbook/videoOpinion"), createTextInput("blockchain-video-opinion"),
      instructionPlaintext("blockchainworkbook/claimsTask"), CreateBlockchainWorkbook.claimsTable
    )))),
    section("blockchain-trust", "blockchainworkbook/trustTitle", List(container("blockchainworkbook/trustTitle",
      reflections("photoStorage", "cloudFailure", "cloudLikelihood", "cloudTrust", "paymentRoute") ++ List(
        instructionPlaintext("blockchainworkbook/paymentComparisonTask"), CreateBlockchainWorkbook.paymentComparison
      ) ++ reflections("thirdPartyTask") ++ List(
        instructionPlaintext("blockchainworkbook/paymentArgumentsTask"), CreateBlockchainWorkbook.paymentArguments
      )))),
    section("blockchain-ledger", "blockchainworkbook/ledgerTitle", List(container("blockchainworkbook/ledgerTitle", List(
      instructionMarkdown("blockchainworkbook/ledgerHistory"),
      instructionPlaintext("blockchainworkbook/balanceTask"), CreateBlockchainWorkbook.balanceTable
    ) ++ reflections("derivedBalances") ++ List(
      ChoiceInteraction("blockchain-ledger-overspend", id("overspendTask"), List(id("transferYes"), id("transferNo")), expected = Some(List(1)))
    ) ++ reflections("overspendReason", "ledgerFraud", "signedLedger") ++ List(
      instructionPlaintext("blockchainworkbook/storageTask"), CreateBlockchainWorkbook.storageComparison
    ) ++ reflections("ledgerTrust", "decentralizedReassessment") ++ List(
      instructionPlaintext("blockchainworkbook/bitcoinLedgerTask"), CreateBlockchainWorkbook.bitcoinLedger
    )))),
    section("blockchain-privacy", "blockchainworkbook/privacyTitle", List(container("blockchainworkbook/privacyTitle", List(
      instructionMarkdown("blockchainworkbook/pseudonymHistory")
    ) ++ reflections("pseudonymOpinion", "timonEntry", "identityLeak") ++ List(
      instructionPlaintext("blockchainworkbook/bitcoinPrivacyTask"), CreateBlockchainWorkbook.bitcoinPrivacy
    ) ++ reflections("bitcoinIdentity", "bankPrivacy", "anonymousReassessment")))),
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
    )))),
    section("blockchain-sha256", "blockchainworkbook/shaTitle", List(container("blockchainworkbook/shaTitle", List(
      instructionPlaintext("blockchainworkbook/shaExplanation"),
      Sha256Interaction("blockchain-sha-compare", id("shaCompareTitle"), initial = Sha256Answer("Informatik", "informatik"))
    ) ++ reflections("shaTextEncoding", "shaHexadecimal", "shaAvalanche") ++ List(
      instructionPlaintext("blockchainworkbook/shaSpaceTask"), CreateBlockchainWorkbook.hashSpaceTable,
      Sha256Interaction("blockchain-sha-prefix-one", id("shaPrefixOneTitle"), FindSha256Prefix(1))
    ) ++ reflections("shaDifficultyReflection") ++ List(
      instructionCollapsibleHint("blockchainworkbook/shaDifficultyHintTitle", "blockchainworkbook/shaDifficultyHint"),
      Sha256Interaction("blockchain-sha-prefix-two", id("shaPrefixTwoTitle"), FindSha256Prefix(2))
    )))),
    section("blockchain-proof-of-work", "blockchainworkbook/powTitle", List(container("blockchainworkbook/powTitle",
      reflections("powConflictingCopies", "powOtherConflict", "powProposals") ++ List(
        instructionPlaintext("blockchainworkbook/powExplanation"), CreateBlockchainWorkbook.sourceChainInteraction
      ) ++ reflections("powBlockCapacity", "powGenesis", "powReadOnlyFields", "powPropagation", "powColors", "powMining", "powPrefixConnection",
        "powTamper", "powRepair") ++ List(
        instructionPlaintext("blockchainworkbook/powExpectedTask"), CreateBlockchainWorkbook.miningArithmetic,
        instructionCollapsibleHint("blockchainworkbook/powExpectedHintTitle", "blockchainworkbook/powExpectedHint"),
        instructionPlaintext("blockchainworkbook/powFourZerosTask"),
        BlockchainInteraction("blockchain-four-zero-lab", id("powFourZerosTitle"), CreateBlockchainWorkbook.sourceChain, 4)
      ) ++ reflections("powAverage", "powLongest", "powRewrite", "powAttackGuess", "powAttackResearch")
    ))),
    section("blockchain-energy", "blockchainworkbook/energyTitle", List(container("blockchainworkbook/energyTitle", List(
      instructionMarkdown("blockchainworkbook/energyExplorerTask"), CreateBlockchainWorkbook.blockResearch
    ) ++ reflections("energyRewardChange", "energyFees", "energyIncentive") ++ List(
      instructionPlaintext("blockchainworkbook/energyHistoricalTask"), CreateBlockchainWorkbook.resourceConversions
    ) ++ reflections("energyElectricityComparison", "energyCarbonComparison", "energyWaterComparison", "energyDifficultyOld",
      "energyPopularity", "energyCostEffect") ++ List(
      instructionPlaintext("blockchainworkbook/energyArithmeticTask"), CreateBlockchainWorkbook.energyArithmetic
    ) ++ reflections("energyThroughputProblem", "energySpeedReassessment", "energyEverydayComparison", "energyPaymentResearch")))),
    section("blockchain-conclusion", "blockchainworkbook/conclusionTitle", List(container("blockchainworkbook/conclusionTitle", List(
      instructionPlaintext("blockchainworkbook/finalClaimsTask"), CreateBlockchainWorkbook.finalClaims
    ) ++ reflections("energyFinalJudgment") ++ List(instructionPlaintext("blockchainworkbook/outlook")))))
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
  val hashSpaceTable = AnswerTableInteraction("blockchain-sha-space", id("shaSpaceTitle"),
    List(id("sha256Bits")), List(id("shaPossibleOutputs")),
    List(List(EditableTableCell(Some(List("2^256", "2**256", "2²⁵⁶", BigInt(2).pow(256).toString))))))
  val participants = List("Anna", "Lukas", "Sara", "Tom")
  val sourceTransfers = List(
    LedgerTransfer("Anna", "Lukas", 4), LedgerTransfer("Lukas", "Sara", 6), LedgerTransfer("Sara", "Tom", 2),
    LedgerTransfer("Anna", "Tom", 5), LedgerTransfer("Anna", "Sara", 1), LedgerTransfer("Tom", "Lukas", 12))
  private val pseudonyms = Map("Anna" -> "Krümelmonster", "Lukas" -> "Elmo", "Sara" -> "Oskar", "Tom" -> "Bert")
  val sourceRecords: List[String] = "00: Krümelmonster, Elmo, Oskar und Bert erhalten je 10 HP." ::
    sourceTransfers.zipWithIndex.map((transfer, index) =>
      s"0${index + 1}: ${pseudonyms(transfer.sender)} → ${pseudonyms(transfer.recipient)}: ${transfer.amount} HP")
  val sourceChain = TeachingChain(sourceRecords.grouped(2).map(records => TeachingBlock(records.mkString("\n"))).toList)
  val sourceChainInteraction = BlockchainInteraction("blockchain-source-chain", id("powSimulatorTitle"), sourceChain)
  val miningArithmetic = AnswerTableInteraction("blockchain-mining-arithmetic", id("powExpectedTitle"),
    List(id("powFourZeroAttempts"), id("powGroupPages")), List(id("powExpectedValue")), List(
      List(EditableTableCell(Some(List("65536", "65,536", "65 536", "16^4", "2^16")))),
      List(EditableTableCell(Some(List("2.44140625", "2,44140625", "160000/65536", "625/256"))))))
  val energyScenario = MiningEnergyEstimate(MiningEnergyEstimate.terawattHoursToKilowattHours(BigDecimal(173)), 4000, 600)
  private def decimalAnswer(value: BigDecimal): EditableTableCell = {
    val rounded = MiningEnergyEstimate.rounded(value)
    EditableTableCell(Some(List(rounded, rounded.replace('.', ','))))
  }
  val resourceConversions = AnswerTableInteraction("blockchain-resource-conversions", id("energyConversionTitle"),
    List(id("energyTwh"), id("energyMegatonnes"), id("energyWaterVolume")), List(id("energyConverted")), List(
      List(EditableTableCell(Some(List(MiningEnergyEstimate.terawattHoursToKilowattHours(BigDecimal(173)).toBigInt.toString)))),
      List(EditableTableCell(Some(List(MiningEnergyEstimate.megatonnesToKilograms(BigDecimal(86)).toBigInt.toString)))),
      List(EditableTableCell(Some(List(MiningEnergyEstimate.cubicKilometresToLitres(BigDecimal("1.65")).toBigInt.toString))))))
  val energyArithmetic = AnswerTableInteraction("blockchain-energy-arithmetic", id("energyArithmeticTitle"),
    List(id("energyBlocksYear"), id("energyTransactionsYear"), id("energyTransactionsSecond"), id("energyAllocation")),
    List(id("energyValue")), List(
      List(EditableTableCell(Some(List(energyScenario.blocksPerYear.toBigInt.toString)))),
      List(EditableTableCell(Some(List(energyScenario.transactionsPerYear.toBigInt.toString)))),
      List(decimalAnswer(energyScenario.transactionsPerSecond)), List(decimalAnswer(energyScenario.allocatedKilowattHoursPerTransaction))))
  val blockResearch = reflectionTable("blockchain-block-research", "energyResearchTitle", List("energyLatest", "energyGenesis"),
    List("energyHeight", "energyHashZeros", "energyReward", "energyTransactions", "energySourceDate"))
  val finalClaims = reflectionTable("blockchain-final-claims", "finalClaimsTitle",
    List("anonymous", "fast", "decentralized", "own1", "own2"), List("finalAssessment", "finalEvidence"))
  val sourceBalances: Map[String, BigInt] = TeachingLedger.calculate(participants.map(_ -> BigInt(10)).toMap, sourceTransfers)
    .fold(error => throw new IllegalArgumentException(s"Invalid source ledger: $error"), identity)
  val balanceTable = AnswerTableInteraction("blockchain-ledger-balances", id("balanceTitle"),
    participants.map(name => id(s"person$name")), List(id("balance")),
    participants.map(name => List(EditableTableCell(Some(List(sourceBalances(name).toString))))))
  private def reflectionTable(elementId: String, caption: String, rows: List[String], columns: List[String]) =
    AnswerTableInteraction(elementId, id(caption), rows.map(id), columns.map(id),
      rows.map(_ => columns.map(_ => EditableTableCell())))
  val paymentComparison = reflectionTable("blockchain-payment-comparison", "paymentComparisonTitle",
    List("cash", "onlinePayment"), List("advantages", "disadvantages"))
  val paymentArguments = reflectionTable("blockchain-payment-arguments", "paymentArgumentsTitle",
    List("buyerProtection", "privacy", "singleFailure", "terms", "forgottenPin"), List("effect", "reason"))
  val storageComparison = reflectionTable("blockchain-ledger-storage", "storageTitle",
    List("centralLedger", "distributedLedger"), List("advantages", "disadvantages"))
  val bitcoinLedger = reflectionTable("blockchain-bitcoin-ledger", "bitcoinLedgerTitle",
    List("signing", "storing"), List("bitcoinExplanation"))
  val bitcoinPrivacy = reflectionTable("blockchain-bitcoin-privacy", "bitcoinPrivacyTitle",
    List("pseudonyms", "acquiring"), List("bitcoinExplanation"))
  val hashTable = AnswerTableInteraction("blockchain-hash-table", id("hashTableTitle"),
    sourceInputs.map(n => id(s"number$n")), List(id("square"), id("hash")),
    sourceInputs.map(n => {
      val result = SquareMiddleHash.calculate(BigInt(n))
      List(EditableTableCell(Some(List(result.square.toString))), EditableTableCell(Some(List(result.hash))))
    }))
}
