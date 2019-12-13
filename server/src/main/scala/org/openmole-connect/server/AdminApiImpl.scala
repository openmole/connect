package org.openmoleconnect.server

import shared.Data.{PodInfo, UserData}
import DB._

object AdminApiImpl extends shared.AdminApi {

  def users() = {
    DB.users
  }

  def upserted(userData: UserData): Seq[UserData] = {
    val id = DB.uuid(Email(userData.email)).getOrElse(UUID(java.util.UUID.randomUUID.toString))
    upsert(toUser(id, userData))
    users
  }

  def delete(userData: UserData): Seq[UserData] = {
    val id = DB.uuid(Email(userData.email))
    id.foreach {i=>
       DB.delete(toUser(i, userData))
    }
    users
  }

  //PODS
  def podInfos(): Seq[PodInfo] = K8sService.podInfos

}