package shared

import shared.Data.UserData

trait AdminApi {

  def users(): Seq[UserData]

  def updated(userData: UserData): Seq[UserData]
}