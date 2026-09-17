package it.evadid.core.datastructures.user

import it.evadid.core.datastructures.user.User
import it.evadid.core.datastructures.user.User.UserToken

case class AllUserInfo(user: User, token: UserToken, config: UserConfig) {

}

object AllUserInfo {


}
