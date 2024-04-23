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
  def usedSpace(uuid: String): Option[Double] = K8sService.usedSpace(uuid)

class AdminAPIRoutes(impl: AdminAPIImpl) extends server.Endpoints[IO] with AdminAPI with server.JsonEntitiesFromCodecs:

  val usersRoute = users.implementedBy { _ =>  impl.users }
  
  val registeringUsersRoute = registeringUsers.implementedBy { _ => impl.registeringUsers }

  val promoteRoute = promoteRegisteringUser.implementedBy{ r => impl.promoteRegisterUser(r)}

  val deleteRegisterRoute = deleteRegisteringUser.implementedBy(r=> impl.deleteRegisterUser(r))

  val usedSpaceRoute = usedSpace.implementedBy(impl.usedSpace)

  val routes: HttpRoutes[IO] = HttpRoutes.of(
    routesFromEndpoints(usersRoute, registeringUsersRoute, promoteRoute, deleteRegisterRoute, usedSpaceRoute)
  )
