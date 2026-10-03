package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.*
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}

/** Interactive reconstruction of Till Favier's Bitcoin/blockchain workbook. */
case class CreateBitcoinWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {
  override val availableLanguages: List[HumanLanguage] = List(German, English)
  override val workbookId: String = "BitcoinWorkbook"

  private def t(key: String): String = s"BitcoinWorkbook/$key"
  private def exercise(key: String): WorkbookElement =
    container(t(s"${key}Title"), List(instructionHtml(t(key)), createTextInput()))
  private def info(key: String): WorkbookElement =
    container(t(s"${key}Title"), List(instructionHtml(t(key))))

  override lazy val createWorkbook: Workbook = workbook(
    t("workbookTitle"),
    List(entry, trust, ledger, anonymity, hashes, proofOfWork, sustainability),
    User.TillFavier
  )

  private lazy val entry = section("entry", t("sectionEntryTitle"), List(
    info("welcome"), exercise("e1"), exercise("e2")
  ))

  private lazy val trust = section("trust", t("section1Title"), List(
    info("section1Intro"), exercise("s1t1"), exercise("s1t2"), exercise("s1t3"),
    exercise("s1t4"), exercise("s1t5"), exercise("s1Final")
  ))

  private lazy val ledger = section("ledger", t("section2Title"), List(
    info("section2Intro"), exercise("s2t1"), exercise("s2t2"), exercise("s2t3"),
    exercise("s2t4"), exercise("s2t5"), exercise("s2t6"), exercise("s2t7"),
    exercise("s2t8"), exercise("s2Final")
  ))

  private lazy val anonymity = section("anonymity", t("section3Title"), List(
    info("section3Intro"), exercise("s3t1"), exercise("s3t2"), exercise("s3t3"),
    exercise("s3t4"), exercise("s3t5"), exercise("s3t6"), exercise("s3Final")
  ))

  private lazy val hashes = section("hashes", t("section4Title"), List(
    info("section4Intro"), exercise("s4t1"), exercise("s4t2"), exercise("s4t3"),
    exercise("s4t4"), exercise("s4t5"), exercise("s4t6"), exercise("s4Final")
  ))

  private lazy val proofOfWork = section("proof-of-work", t("section5Title"), List(
    info("section5Intro"), exercise("s5t1"), exercise("s5t2"), exercise("s5t3"),
    exercise("s5t4"), exercise("s5t5"), exercise("s5t6"), exercise("s5t7"),
    exercise("s5t8"), exercise("s5t9"), exercise("s5t10"), exercise("s5t11"),
    exercise("s5Final")
  ))

  private lazy val sustainability = section("sustainability", t("section6Title"), List(
    info("section6Intro"), exercise("s6t1"), exercise("s6t2"), exercise("s6t3"),
    exercise("s6t4"), exercise("s6t5"), exercise("s6t6"), exercise("s6t7"),
    exercise("s6t8"), exercise("s6Final"), info("summary")
  ))
}
