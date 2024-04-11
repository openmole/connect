package org.openmole.connect.server

import endpoints4s.http4s.server
import org.openmole.connect.shared.*
import cats.effect._
import org.http4s._

class AdminAPIImpl(k8sService: K8sService):
  def users: Seq[Data.User] = DB.users.map(DB.User.toData)

class AdminAPIRoutes(impl: AdminAPIImpl) extends server.Endpoints[IO] with AdminAPI with server.JsonEntitiesFromCodecs:

  val usersRoute = users.implementedBy { _ =>  impl.users }
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
  val routes: HttpRoutes[IO] = HttpRoutes.of(
    routesFromEndpoints(usersRoute)
  )
//
//}