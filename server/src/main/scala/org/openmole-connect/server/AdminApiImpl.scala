package org.openmoleconnect.server

import shared.Data.{PodInfo, UserData}
import DB._

class AdminApiImpl(kubeOff: Boolean) extends shared.AdminApi {

  def users() = {
    DB.users
  }

  def upserted(userData: UserData): Seq[UserData] = Services.upserted(userData, kubeOff)

  def delete(userData: UserData): Seq[UserData] = {
    val id = DB.uuid(Email(userData.email))
    id.foreach { i =>
      DB.delete(toUser(i, userData))
    }
    users
  }

  //PODS
  def podInfos(): Seq[PodInfo] = {
    K8sService.podInfos
  }

}