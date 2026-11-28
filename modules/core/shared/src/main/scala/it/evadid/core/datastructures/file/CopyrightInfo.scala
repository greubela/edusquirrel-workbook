package it.evadid.core.datastructures.file

import upickle.default.*

import CopyrightInfo.{AuthorInfo, LicenceInfo}
import it.evadid.core.datastructures.language.LanguageMap

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*

case class CopyrightInfo(licenceInfo: LicenceInfo, authorInfo: AuthorInfo) derives ReadWriter {

}


object CopyrightInfo {

  lazy val fromAi = CopyrightInfo(CC_LICENCE, AiGenerated())

  sealed trait LicenceInfo(val licenceName: String, val licenceDescription: LanguageMap[HumanLanguage]) derives ReadWriter

  @upickle.implicits.key("UnknownLicence")
  case object UnknownLicence extends LicenceInfo("Unknown Licence", LanguageMap.universalMap("[Unknown Licence]"))

  @upickle.implicits.key("CC_LICENCE")
  case object CC_LICENCE extends LicenceInfo("CC Licence", LanguageMap.universalMap("[CC Licence]"))

  sealed trait AuthorInfo() derives ReadWriter {
    def getName: Option[String]
  }

  @upickle.implicits.key("UnknownAuthorInfo")
  case class UnknownAuthorInfo() extends AuthorInfo derives ReadWriter {
    override def getName: Option[String] = None
  }

  @upickle.implicits.key("AuthorNameInfo")
  case class AuthorNameInfo(name: String) extends AuthorInfo derives ReadWriter {
    override def getName: Option[String] = Some(name)
  }

  @upickle.implicits.key("AiGenerated")
  case class AiGenerated() extends AuthorInfo derives ReadWriter {
    override def getName: Option[String] = Some("AiGenerated")
  }
  
  def plattformCopyrightInfo(authorName: String = "André Greubel"): CopyrightInfo = {
    CopyrightInfo(UnknownLicence, AuthorNameInfo(authorName))
  }
  
  val unknownCopyrightInfo: CopyrightInfo = CopyrightInfo(UnknownLicence, UnknownAuthorInfo())
  

}
