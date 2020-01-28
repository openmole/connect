package org.openmoleconnect.server

import shared.Data.UserData
import shared.UserApi

class UserApiImpl(kubeOff: Boolean) extends UserApi{

  def user(): Option[UserData] = None

  def userWithData(connectedUserData: Option[UserData]): Option[UserData] = connectedUserData

  def upserted(userData: UserData): Option[UserData] = None

  def upsertedWithData(userData: UserData, connectedUserData: Option[UserData]): Option[UserData] = {
    if(Some(userData.email) == connectedUserData.map{_.email})
      Services.upserted(userData, kubeOff)
    Some(userData)
  }
}
