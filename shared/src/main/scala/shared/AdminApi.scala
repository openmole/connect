package shared

import shared.Data.{PodInfo, UserData}

trait AdminApi {

  // USERS
  def users(): Seq[UserData]

  def upserted(userData: UserData): Seq[UserData]

  def delete(userData: UserData): Seq[UserData]

  // PODS
  def podInfos(): Seq[PodInfo]
}