package shared

import shared.Data.{PodInfo, UserData}

trait AdminApi {

  // USERS
  def users(): Seq[UserData]

  def upserted(userData: UserData): Seq[UserData]

  def delete(userData: UserData): Seq[UserData]

  def stopOpenMOLE(userData: UserData): Seq[UserData]

  def startOpenMOLE(userData: UserData): Seq[UserData]

  def updateOpenMOLE(userData: UserData): Seq[UserData]

  def updateOpenMOLEPersistentVolumeStorage(userData: UserData): Seq[UserData]

  // PODS
  def podInfos(): Seq[PodInfo]
}