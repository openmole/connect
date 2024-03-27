//package org.openmoleconnect.server
//
//import shared.Data.{PodInfo, UserData}
//import DB._
//
//object Services {
//
//  def upserted(userData: UserData, kubeOff: Boolean): Seq[UserData] = {
//    val id = DB.uuid (Email (userData.email) ).getOrElse (UUID (java.util.UUID.randomUUID.toString) )
//    upsert (toUser (id, userData) )
//    if (! kubeOff) K8sService.deployIfNotDeployedYet (id, userData.omVersion, userData.storage)
//    users
//  }
//}
