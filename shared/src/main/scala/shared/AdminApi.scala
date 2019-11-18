package shared

import shared.Data.UserData

trait AdminApi {

  def users(): Seq[UserData]
}