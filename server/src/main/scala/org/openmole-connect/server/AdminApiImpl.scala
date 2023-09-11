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
      K8sService.deleteOpenMOLE(i)
      DB.delete(toUser(i, userData))
    }
    users
  }

  def stopOpenMOLE(userData: UserData): Seq[UserData] = {
    val id = DB.uuid(Email(userData.email))
    id.foreach { i =>
      K8sService.stopOpenMOLEPod(i)
    }
    users
  }

 def startOpenMOLE(userData: UserData): Seq[UserData] = {
    val id = DB.uuid(Email(userData.email))
    id.foreach { i =>
      K8sService.startOpenMOLEPod(i)
    }
    users
  }

  def updateOpenMOLE(userData: UserData): Seq[UserData] = {
    val id = DB.uuid(Email(userData.email))
    id.foreach { i =>
      K8sService.updateOpenMOLEPod(i, userData.omVersion)
    }
    users
  }

  def updateOpenMOLEPersistentVolumeStorage(userData: UserData): Seq[UserData] = {
    val id = DB.uuid(Email(userData.email))
    id.foreach { i =>
      K8sService.updateOpenMOLEPersistentVolumeStorage(i, userData.storage)
    }
    users
  }

  //PODS
  def podInfos(): Seq[PodInfo] = {
    K8sService.podInfos
  }

}