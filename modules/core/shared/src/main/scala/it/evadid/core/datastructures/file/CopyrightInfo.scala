package it.evadid.core.datastructures.file

import upickle.default.*

import CopyrightInfo.{AuthorInfo, LicenceInfo}
import it.evadid.core.datastructures.language.LanguageMap

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*

case class CopyrightInfo(licenceInfo: LicenceInfo, authorInfo: AuthorInfo) derives ReadWriter {

}


object CopyrightInfo {



  sealed trait LicenceInfo(val licenceName: String, val licenceDescription: LanguageMap[HumanLanguage]) derives ReadWriter

  case object UnknownLicence extends LicenceInfo("Unknown Licence", LanguageMap.universalMap("[Unknown Licence]"))

  case object CC_LICENCE extends LicenceInfo("CC Licence", LanguageMap.universalMap("[CC Licence]"))

  sealed trait AuthorInfo() derives ReadWriter {
    def getName: Option[String]
  }

  case class UnknownAuthorInfo() extends AuthorInfo derives ReadWriter {
    override def getName: Option[String] = None
  }

  case class AuthorNameInfo(name: String) extends AuthorInfo derives ReadWriter {
    override def getName: Option[String] = Some(name)
  }
  
  def plattformCopyrightInfo(authorName: String = "André Greubel"): CopyrightInfo = {
    CopyrightInfo(UnknownLicence, AuthorNameInfo(authorName))
  }
  
  val unknownCopyrightInfo: CopyrightInfo = CopyrightInfo(UnknownLicence, UnknownAuthorInfo())
  

}
