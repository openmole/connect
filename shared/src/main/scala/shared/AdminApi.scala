package shared

import shared.Data.UserData

trait AdminApi {

  def users(): Seq[UserData]

  def upserted(userData: UserData): Seq[UserData]

  def delete(userData: UserData): Seq[UserData]
}