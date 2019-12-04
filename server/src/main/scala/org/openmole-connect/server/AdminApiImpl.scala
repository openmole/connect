package org.openmoleconnect.server

import shared.Data.UserData
import DB._

object AdminApiImpl extends shared.AdminApi {

  def users() = {
    DB.users
  }

  def updated(userData: UserData): Seq[UserData] = {
    DB.uuid(Email(userData.email)).foreach { id =>
      update(toUser(id, userData))
    }
    users
  }

}