//package org.openmoleconnect.server
//
//import endpoints4s.http4s.server
//import shared.Data.{PodInfo, UserData}
//import DB._
//import cats.effect._
//import org.http4s._
//
//class AdminApiImpl(kubeOff: Boolean) extends server.Endpoints[IO] with shared.AdminAPI with server.JsonEntitiesFromCodecs {
//
//  val usersRoute = users.implementedBy { _ => DB.users }
//
//  val upsertedRoute = upserted.implementedBy { userData => Services.upserted(userData, kubeOff) }
//
//  val deleteRoute = delete.implementedBy { userData =>
//    val id = DB.uuid(Email(userData.email))
//    id.foreach { i =>
//      K8sService.deleteOpenMOLE(i)
//      DB.delete(toUser(i, userData))
//    }
//    DB.users
//  }
//
//  val stopOpenMOLERoute = stopOpenMOLE.implementedBy { userData =>
//    val id = DB.uuid(Email(userData.email))
//    id.foreach { i =>
//      K8sService.stopOpenMOLEPod(i)
//    }
//    DB.users
//  }
//
// val startOpenMOLERoute = stopOpenMOLE.implementedBy { userData =>
//    val id = DB.uuid(Email(userData.email))
//    id.foreach { i =>
//      K8sService.startOpenMOLEPod(i)
//    }
//    DB.users
//  }
//
//  val updateOpenMOLERoute = updateOpenMOLE.implementedBy { userData =>
//    val id = DB.uuid(Email(userData.email))
//    id.foreach { i =>
//      K8sService.updateOpenMOLEPod(i, userData.omVersion)
//    }
//    DB.users
//  }
//
//  val updateOpenMOLEPersistentVolumeStorageRoute = updateOpenMOLEStorage.implementedBy { userData =>
//    val id = DB.uuid(Email(userData.email))
//    id.foreach { i =>
//      K8sService.updateOpenMOLEPersistentVolumeStorage(i, userData.storage)
//    }
//    DB.users
//  }
//
//  //PODS
//  val podInfosRoute = podInfo.implementedBy { _ =>
//    K8sService.podInfos
//  }
//
//  val routes: HttpRoutes[IO] = HttpRoutes.of(
//    routesFromEndpoints(usersRoute, upsertedRoute, deleteRoute, stopOpenMOLERoute, startOpenMOLERoute, updateOpenMOLERoute, updateOpenMOLEPersistentVolumeStorageRoute, podInfosRoute)
//  )
//
//}