package org.openmole.connect.server

import endpoints4s.http4s.server
import org.openmole.connect.shared.*
import cats.effect.*
import org.http4s.*
import org.openmole.connect.server.Authentication.UserCache
import org.openmole.connect.server.db.DB
import org.openmole.connect.server.KubeService.{KubeCache, getPVCSize}
import org.openmole.connect.server.OpenMOLE.DockerHubCache
import org.openmole.connect.shared.Data.{EmailStatus, UserAndPodInfo}

import scala.concurrent.ExecutionContext

class AdminAPIImpl(using DB.Salt, KubeCache, UserCache, DockerHubCache, KubeService, Email.Sender, DB.Default, ExecutionContext):
  def users: Seq[Data.User] = DB.users.map(DB.userToData)
  def registeringUsers: Seq[Data.RegisterUser] = DB.registerUsers.map(DB.registerUserToData)
  def promoteRegisterUser(uuid: String): Unit = DB.promoteRegistering(uuid).foreach(Email.sendValidated)
  def deleteRegisterUser(uuid: String): Unit = DB.deleteRegistering(uuid)
  def usedSpace(uuid: String): Option[Storage] = KubeService.usedSpace(uuid)
  def instance(uuid: String): Option[Data.PodInfo] = KubeService.podInfo(uuid)
  def usersAndPodInfo: Seq[Data.UserAndPodInfo] =
    val podList = KubeService.listPods.flatMap(p => p.userUUID.map(_ -> p)).toMap
    users.map(u => Data.UserAndPodInfo(u, podList.get(u.uuid)))

  def changePassword(uuid: String, newPassword: String) = DB.updatePassword(uuid, newPassword)
  def setRole(uuid: String, role: Data.Role) = DB.updateRole(uuid, role)
  def setMemory(uuid: String, memory: Int) = DB.updateMemory(uuid, memory)
  def setCPU(uuid: String, cpu: Double) = DB.updateCPU(uuid, cpu)
  def setStorage(uuid: String, space: Int) = KubeService.updateOpenMOLEPersistentVolumeStorage(uuid, space)
  def setInstitution(uuid: String, institution: String) = DB.updateInstitution(uuid, institution)
  def setEmail(uuid: String, email: String) = DB.updateEmail(uuid, email)
  def setFirstName(uuid: String, firstName: String) = DB.updateFirstName(uuid, firstName)
  def setName(uuid: String, name: String) = DB.updateName(uuid, name)
  def setEmailStatus(uuid: String, s: EmailStatus) = DB.updateEmailStatus(uuid, s)
  def setVersion(uuid: String, v: String) = DB.updateOMVersion(uuid, v)

  def deleteUser(uuid: String) =
    DB.deleteUser(uuid)
    KubeService.deleteOpenMOLE(uuid)

  def launch(uuid: String) =
    DB.userFromUUID(uuid).foreach(u => KubeService.launch(u))

  def stop(uuid: String): Unit = KubeService.stopOpenMOLEPod(uuid)
  def pvcSize(uuid: String) = KubeService.getPVCSize(uuid)

class AdminAPIRoutes(impl: AdminAPIImpl) extends server.Endpoints[IO] with AdminAPI with server.JsonEntitiesFromCodecs:

  type Eff = super.Effect

  val routes: HttpRoutes[IO] = HttpRoutes.of:
    routesFromEndpoints(
      users.implementedBy { _ =>  impl.users },
      registeringUsers.implementedBy { _ => impl.registeringUsers },
      promoteRegisteringUser.implementedBy(impl.promoteRegisterUser),
      deleteRegisteringUser.implementedBy(impl.deleteRegisterUser),
      usedSpace.implementedBy(impl.usedSpace),
      allInstances.implementedBy(_=> impl.usersAndPodInfo),
      changePassword.implementedBy(impl.changePassword),
      launch.implementedBy(impl.launch),
      stop.implementedBy(impl.stop),
      deleteUser.implementedBy(impl.deleteUser),
      setRole.implementedBy(impl.setRole),
      setMemory.implementedBy(impl.setMemory),
      setCPU.implementedBy(impl.setCPU),
      instance.implementedBy(impl.instance),
      setStorage.implementedBy(impl.setStorage),
      setName.implementedBy(impl.setName),
      setFirstName.implementedBy(impl.setFirstName),
      setInstitution.implementedBy(impl.setInstitution),
      setEmail.implementedBy(impl.setEmail),
      setEmailStatus.implementedBy(impl.setEmailStatus),
      setVersion.implementedBy(impl.setVersion),
      pvcSize.implementedBy(impl.pvcSize)
    )

