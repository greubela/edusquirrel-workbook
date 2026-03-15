package contentmanagement.model.file

import contentmanagement.model.language.*

trait ImageTag {

}

object ImageTag {

  /*
  /* --- LICENCE TYPE --- */
  case class CopyrightInfo extends ImageTag

  case object UNKNOWN_INFO extends CopyrightInfo("No Licence", LanguageMap.uni, "Unknown")
  
  case object CC_LICENCE extends LicenceType("CC Licence", "text-type-info")

  case object WIZARDS_IP extends LicenceType("> WotC IP <", "text-type-warning")

  case object USER_CONTENT extends LicenceType("User Content", "text-type-warning")

  case object UNKNOWN_LICENCE extends LicenceType("Unknown Licence", "text-type-warning")
*/


}
