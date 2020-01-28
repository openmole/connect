package shared

import shared.Data.UserData

trait UserApi {

  def user(): Option[UserData]

  def userWithData(connectedUserData: Option[UserData]): Option[UserData]

  def upserted(userData: UserData): Option[UserData]

  def upsertedWithData(userData: UserData, connectedUserData: Option[UserData]): Option[UserData]
}
