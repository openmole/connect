package org.openmole.connect.server

import endpoints4s.http4s.server
import org.openmole.connect.shared.*
import cats.effect.*
import org.http4s.*
import org.openmole.connect.server.Authentication.AuthenticationCache
import org.openmole.connect.server.db.v1.DB.{RegisterUser, Salt, registerUsers}
import org.openmole.connect.server.K8sService.KubeCache
import org.openmole.connect.server.db.v1.DB
import org.openmole.connect.shared.Data.UserAndPodInfo

class AdminAPIImpl(k8sService: K8sService)(using Salt, KubeCache, AuthenticationCache):
  def users: Seq[Data.User] = DB.users.map(DB.User.toData)
  def registeringUsers: Seq[Data.RegisterUser] = DB.registerUsers.map(DB.RegisterUser.toData)
  def promoteRegisterUser(uuid: String): Unit = DB.promoteRegistering(uuid)
  def deleteRegisterUser(uuid: String): Unit = DB.deleteRegistering(uuid)
  def usedSpace(uuid: String): Option[Storage] = K8sService.usedSpace(uuid)
  def instance(uuid: String): Option[Data.PodInfo] = K8sService.podInfo(uuid)
  def usersAndPodInfo: Seq[Data.UserAndPodInfo] = users.map(u=> UserAndPodInfo(u, instance(u.uuid)))
  def changePassword(uuid: String, newPassword: String) = DB.updatePassword(uuid, newPassword)

  def launch(uuid: String): Unit =
    if K8sService.deploymentExists(uuid)
    then K8sService.startOpenMOLEPod(uuid)
    else
      DB.userFromUUID(uuid).foreach: user =>
        K8sService.deployOpenMOLE(k8sService, user.uuid, user.omVersion, user.openMOLEMemory, user.memory, user.cpu)

  def stop(uuid: String): Unit = K8sService.stopOpenMOLEPod(uuid)

class AdminAPIRoutes(impl: AdminAPIImpl) extends server.Endpoints[IO] with AdminAPI with server.JsonEntitiesFromCodecs:
  val usersRoute = users.implementedBy { _ =>  impl.users }
  val registeringUsersRoute = registeringUsers.implementedBy { _ => impl.registeringUsers }
  val promoteRoute = promoteRegisteringUser.implementedBy{ r => impl.promoteRegisterUser(r)}
  val deleteRegisterRoute = deleteRegisteringUser.implementedBy(impl.deleteRegisterUser)
  val usedSpaceRoute = usedSpace.implementedBy(impl.usedSpace)
  val instanceRoute = instance.implementedBy(impl.instance)
  val allInstancesRoute = allInstances.implementedBy(_=> impl.usersAndPodInfo)
  val changePasswordRoute = changePassword.implementedBy((uuid, p)=> impl.changePassword(uuid, p))
  val launchRoute = launch.implementedBy(impl.launch)
  val stopRoute = stop.implementedBy(impl.stop)

  val routes: HttpRoutes[IO] = HttpRoutes.of(
    routesFromEndpoints(usersRoute, registeringUsersRoute, promoteRoute, deleteRegisterRoute, usedSpaceRoute, instanceRoute, allInstancesRoute, changePasswordRoute, launchRoute, stopRoute)
  )
