package datastructures.web.file

import datastructures.web.file.CopyrightInfo.{AuthorInfo, LicenceInfo}
import it.evadid.core.datastructures.language.LanguageMap

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*

case class CopyrightInfo(licenceInfo: LicenceInfo, authorInfo: AuthorInfo) {

}


object CopyrightInfo {

  sealed trait LicenceInfo(val licenceName: String, val licenceDescription: LanguageMap[HumanLanguage])

  case object UnknownLicence extends LicenceInfo("Unknown Licence", LanguageMap.universalMap("[Unknown Licence]"))

  case object CC_LICENCE extends LicenceInfo("CC Licence", LanguageMap.universalMap("[CC Licence]"))

  sealed trait AuthorInfo() {
    def getName: Option[String]
  }

  case class UnknownAuthorInfo() extends AuthorInfo {
    override def getName: Option[String] = None
  }

  case class AuthorNameInfo(name: String) extends AuthorInfo {
    override def getName: Option[String] = Some(name)
  }
  
  def plattformCopyrightInfo(authorName: String = "André Greubel"): CopyrightInfo = {
    CopyrightInfo(UnknownLicence, AuthorNameInfo(authorName))
  }
  
  val unknownCopyrightInfo: CopyrightInfo = CopyrightInfo(UnknownLicence, UnknownAuthorInfo())
  

}
