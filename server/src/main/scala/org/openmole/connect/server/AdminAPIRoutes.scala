package org.openmole.connect.server

import endpoints4s.http4s.server
import org.openmole.connect.shared.*
import cats.effect.*
import org.http4s.*
import org.openmole.connect.server.DB.{RegisterUser, Salt, registerUsers}

class AdminAPIImpl(k8sService: K8sService)(using salt: Salt):
  def users: Seq[Data.User] = DB.users.map(DB.User.toData)
  def registeringUsers: Seq[Data.RegisterUser] = DB.registerUsers.map(DB.RegisterUser.toData)
  def promoteRegisterUser(uuid: String): Unit = DB.promoteRegistering(uuid)
  def deleteRegisterUser(uuid: String): Unit = DB.deleteRegistering(uuid)

class AdminAPIRoutes(impl: AdminAPIImpl) extends server.Endpoints[IO] with AdminAPI with server.JsonEntitiesFromCodecs:

  val usersRoute = users.implementedBy { _ =>  impl.users }
  
  val registeringUsersRoute = registeringUsers.implementedBy { _ => impl.registeringUsers }

  val promoteRoute = promoteRegisteringUser.implementedBy{ r => impl.promoteRegisterUser(r)}

  val deleteRegisterRoute = deleteRegisteringUser.implementedBy(r=> impl.deleteRegisterUser(r))
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
    routesFromEndpoints(usersRoute, registeringUsersRoute, promoteRoute, deleteRegisterRoute)
  )
//
//}