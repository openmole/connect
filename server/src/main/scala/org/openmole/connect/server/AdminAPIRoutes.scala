package org.openmole.connect.server

import endpoints4s.http4s.server
import org.openmole.connect.shared.*
import cats.effect.*
import org.http4s.*
import org.openmole.connect.server.Authentication.AuthenticationCache
import org.openmole.connect.server.db.DB
import org.openmole.connect.server.K8sService.KubeCache
import org.openmole.connect.server.OpenMOLE.DockerHubCache
import org.openmole.connect.shared.Data.UserAndPodInfo

class AdminAPIImpl(using DB.Salt, KubeCache, AuthenticationCache, DockerHubCache, K8sService, Email.Sender):
  def users: Seq[Data.User] = DB.users.map(DB.userToData)
  def registeringUsers: Seq[Data.RegisterUser] = DB.registerUsers.map(DB.registerUserToData)
  def promoteRegisterUser(uuid: String): Unit = DB.promoteRegistering(uuid).foreach(Email.sendValidated)
  def deleteRegisterUser(uuid: String): Unit = DB.deleteRegistering(uuid)
  def usedSpace(uuid: String): Option[Storage] = K8sService.usedSpace(uuid)
  def instance(uuid: String): Option[Data.PodInfo] = K8sService.podInfo(uuid)
  def usersAndPodInfo: Seq[Data.UserAndPodInfo] = users.map(u=> UserAndPodInfo(u, instance(u.uuid)))
  def changePassword(uuid: String, newPassword: String) = DB.updatePassword(uuid, newPassword)
  def setRole(uuid: String, role: Data.Role) = DB.updateRole(uuid, role)

  def deleteUser(uuid: String) =
    DB.deleteUser(uuid)
    K8sService.deleteOpenMOLE(uuid)

  def launch(uuid: String): Unit =
    if K8sService.deploymentExists(uuid)
    then K8sService.startOpenMOLEPod(uuid)
    else
      DB.userFromUUID(uuid).foreach: user =>
        K8sService.deployOpenMOLE(user.uuid, user.omVersion, user.openMOLEMemory, user.memory, user.cpu)

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
  val deleteUserRoute = deleteUser.implementedBy(impl.deleteUser)
  val setRoleRoute = setRole.implementedBy(impl.setRole)

  val routes: HttpRoutes[IO] = HttpRoutes.of(
    routesFromEndpoints(usersRoute, registeringUsersRoute, promoteRoute, deleteRegisterRoute, usedSpaceRoute, instanceRoute, allInstancesRoute, changePasswordRoute, launchRoute, stopRoute, deleteUserRoute, setRoleRoute)
  )
