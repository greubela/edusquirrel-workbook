package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken

case class AllUserInfo(user: User, token: Option[SignedToken], config: UserConfig) {

}

object AllUserInfo {


}
