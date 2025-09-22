package contentmanagement.model.image

trait ImageTag {

}

object ImageTag {

  /* --- LICENCE TYPE --- */
  sealed trait LicenceType(val desc: String, val cssClass: String) extends ImageTag

  case object CC_LICENCE extends LicenceType("CC Licence", "text-type-info")

  case object WIZARDS_IP extends LicenceType("> WotC IP <", "text-type-warning")

  case object USER_CONTENT extends LicenceType("User Content", "text-type-warning")

  case object UNKNOWN_LICENCE extends LicenceType("Unknown Licence", "text-type-warning")



}
