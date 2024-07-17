package org.openmole.connect.server.db

import org.openmole.connect.server.Settings

import java.sql.DriverManager
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import better.files.*
import org.openmole.connect.server.*
import org.openmole.connect.server.OpenMOLE.DockerHubCache
import slick.*
import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api.*
import slick.model.ForeignKey
import org.openmole.connect.shared.Data

import scala.concurrent.ExecutionContext.Implicits.global
import org.openmole.connect.server.tool.*
import io.github.arainko.ducktape.*
import org.http4s.headers.Upgrade
import slick.jdbc.meta.MTable

/*
 * Copyright (C) 2024 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

object DB:

  import DBSchemaV2.{*, given}
  export DBSchemaV2.{User, RegisterUser, ValidationSecret}
  val version = dbVersion

  def userIsAdmin(u: User) = u.role == Role.Admin
  def userToData(u: User): Data.User = u.to[Data.User]
  def userFromData(u: Data.User): Option[User] = user(u.email)

  def userWithDefault(name: String, firstName: String, email: String, password: Password, institution: Institution, emailStatus: EmailStatus = EmailStatus.Unchecked, role: Role = Role.User, status: UserStatus = UserStatus.Active, uuid: UUID = randomUUID)(using DockerHubCache) =
    val defaultVersion =
      OpenMOLE.availableVersions(withSnapshot = false).headOption.orElse:
        OpenMOLE.availableVersions(withSnapshot = true).headOption
      .getOrElse("latest")

    User(name, firstName, email, emailStatus, password, institution, defaultVersion, 2048, 2, 1024, now, now, role, status, uuid)

  def registerUserToData(r: RegisterUser): Data.RegisterUser = r.to[Data.RegisterUser]
  def registerUserFromData(r: Data.RegisterUser): Option[RegisterUser] = registerUser(r.email)
  def registerUserToUser(r: RegisterUser)(using DockerHubCache): User = userWithDefault(r.name, r.firstName, r.email, r.password, r.institution, uuid = r.uuid, emailStatus = r.emailStatus)

  def randomUUID = java.util.UUID.randomUUID().toString


  object Salt:
    def apply(s: String): Salt = s

    def value(s: Salt): String = s

  opaque type Salt = String
  type UUID = String
  type Email = String
  type Password = String
  type Institution = String
  type Version = String
  export Data.EmailStatus
  export Data.Role
  export Data.UserStatus
  type Storage = Int
  type Memory = Int
  type Secret = UUID

  val dbFile = Settings.location.toScala / "db"

  lazy val db: Database =
    DriverManager.registerDriver(new org.h2.Driver())
    Database.forURL(url = s"jdbc:h2:${dbFile.pathAsString}")

  def runTransaction[E <: Effect, T](action: DBIOAction[T, NoStream, E]): T =
    Await.result(db.run(action.transactionally), Duration.Inf)

  def addUser(user: User): Unit =
    runTransaction:
      addUserTransaction(user)

  private def addUserTransaction(user: User) =
    for
      e <- userTable.filter(u => u.email === user.email).result
      _ <- if e.isEmpty then userTable += user else DBIO.successful(())
    yield ()

  private def checkAtLeastOneAdmin =
    for
      e <- userTable.filter(_.role === Role.Admin).result
      _ <- if e.isEmpty then DBIO.failed(new RuntimeException("Should be at least one admin account")) else DBIO.successful(())
    yield ()

  def addRegisteringUser(registerUser: RegisterUser)(using salt: Salt): Option[(RegisterUser, Secret)] =
    val secret = randomUUID

    runTransaction:
      val r = userTable.filter(u => u.email === registerUser.email)
      val q = registerUserTable.filter(r => r.email === registerUser.email)

      val createSecret =
        for
          _ <- validationSecretTable.filter(_.uuid === registerUser.uuid).delete
          _ <- validationSecretTable += DB.ValidationSecret(registerUser.uuid, secret)
        yield secret

      val insert =
        for
          _ <- q.delete
          _ <- registerUserTable += registerUser
          r <- q.result
          s <- createSecret
        yield r


      for
        ex <- r.result
        r <- if ex.isEmpty then insert else DBIO.successful(Seq())
      yield r

    .headOption.map: u =>
      (u, secret)

  def validateUserEmail(uuid: UUID, secret: Secret): Boolean =
    val res =
      runTransaction:
        val q1 = registerUserTable.filter(r => r.uuid === uuid).map(_.emailStatus)
        val q2 = userTable.filter(r => r.uuid === uuid).map(_.emailStatus)
        val secretQuery = validationSecretTable.filter(s => s.uuid === uuid && s.validationSecret === secret)

        secretQuery.result.flatMap: s =>
          if s.nonEmpty
          then
            for
              u1 <- q1.update(Data.EmailStatus.Checked)
              u2 <- q2.update(Data.EmailStatus.Checked)
              _ <- secretQuery.delete
            yield Seq(u1, u2)
          else DBIO.successful(Seq())

    res.exists(_ >= 1)

  def deleteRegistering(uuid: UUID): Int =
    runTransaction:
      for
        r <- registerUserTable.filter(_.uuid === uuid).delete
        _ <- validationSecretTable.filter(_.uuid === uuid).delete
      yield r

  def promoteRegistering(uuid: UUID)(using DockerHubCache): Option[User] =
    runTransaction:
      for
        ru <- registerUserTable.filter(_.uuid === uuid).result
        user = ru.map(registerUserToUser)
        _ <- DBIO.sequence(user.map(addUserTransaction))
        _ <- registerUserTable.filter(_.uuid === uuid).delete
      yield user
    .headOption

  def userFromUUID(uuid: UUID): Option[User] =
    runTransaction:
      userTable.filter(u => u.uuid === uuid).result
    .headOption

  def user(email: Email): Option[User] =
    runTransaction:
      userTable.filter(u => u.email === email).result
    .headOption

  def user(email: Email, password: Password)(using salt: Salt): Option[User] =
    runTransaction:
      userTable.filter(u => u.email === email && u.password === salted(password)).result
    .headOption

  def userFromSaltedPassword(uuid: UUID, salted: Password): Option[User] =
    runTransaction:
      userTable.filter(u => u.uuid === uuid && u.password === salted).result
    .headOption

  def registerUser(email: Email): Option[RegisterUser] =
    runTransaction:
      registerUserTable.filter(u => u.email === email).result
    .headOption

  def updadeLastAccess(uuid: UUID) =
    runTransaction:
      val q = userTable.filter(_.uuid === uuid).map(_.lastAccess)
      q.update(tool.now)

  def updateOMVersion(uuid: UUID, version: String)(using Authentication.UserCache)  =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction:
      val q = userTable.filter(_.uuid === uuid).map(_.omVersion)
      q.update(version)


  def updateOMMemory(uuid: UUID, memory: Int)(using Authentication.UserCache)  =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction:
      val q = userTable.filter(_.uuid === uuid).map(_.omMemory)
      q.update(memory)

  def updateMemory(uuid: UUID, memory: Int)(using Authentication.UserCache) =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction:
      val q = userTable.filter(_.uuid === uuid).map(_.memory)
      q.update(memory)

  def updateCPU(uuid: UUID, cpu: Double)(using Authentication.UserCache) =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction(userTable.filter(_.uuid === uuid).map(_.cpu).update(cpu))

  def updateName(uuid: UUID, name: String)(using Authentication.UserCache) =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction(userTable.filter(_.uuid === uuid).map(_.name).update(name))

  def updateFirstName(uuid: UUID, name: String)(using Authentication.UserCache) =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction(userTable.filter(_.uuid === uuid).map(_.firstName).update(name))

  def updateInstitution(uuid: UUID, institution: String)(using Authentication.UserCache) =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction(userTable.filter(_.uuid === uuid).map(_.institution).update(institution))

  def updateRole(uuid: UUID, role: Role)(using Authentication.UserCache) =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction:
      val q = userTable.filter(_.uuid === uuid).map(_.role)
      for
        _ <- q.update(role)
        _ <- checkAtLeastOneAdmin
      yield ()

  def updateEmailStatus(uuid: UUID, emailStatus: EmailStatus)(using Authentication.UserCache) =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction(userTable.filter(_.uuid === uuid).map(_.emailStatus).update(emailStatus))

  def users: Seq[User] = runTransaction(userTable.result)
  def admins: Seq[User] =
    runTransaction:
      userTable.filter(_.role === Role.Admin).result

  def institutions: Seq[Institution] = runTransaction(userTable.map(_.institution).distinct.result)

  def registerUsers: Seq[RegisterUser] = runTransaction(registerUserTable.result)

  def updatePassword(uuid: UUID, password: Password, old: Option[Password] = None)(using Salt, Authentication.UserCache): Boolean =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction:
      val q =
        for
          user <- userTable
          if user.uuid === uuid &&
            (old match
              case Some(pwd) => user.password === salted(pwd)
              case None => true)
        yield user.password
      q.update(salted(password)).map(_ > 0)

  def deleteUser(uuid: UUID)(using Authentication.UserCache) =
    summon[Authentication.UserCache].user.invalidate(uuid)
    runTransaction:
      for
        _ <- userTable.filter(_.uuid === uuid).delete
        _ <- checkAtLeastOneAdmin
        _ <- registerUserTable.filter(_.uuid === uuid).delete
        _ <- validationSecretTable.filter(_.uuid === uuid).delete
      yield ()


  def salted(password: Password)(using salt: Salt) = tool.hash(password, Salt.value(salt))


  /* Initialize database */

  object DatabaseInfo:
    case class Data(version: Int)

  class DatabaseInfo(tag: Tag) extends Table[DatabaseInfo.Data](tag, "DB_INFO"):
    def version = column[Int]("VERSION")

    def * = version.mapTo[DatabaseInfo.Data]

  val databaseInfoTable = TableQuery[DatabaseInfo]

  case class Upgrade(upgrade: DBIO[Unit], version: Int)
  def upgrades: Seq[Upgrade] = Seq(DBSchemaV1.upgrade, DBSchemaV2.upgrade)

  def initDB()(using Salt, DockerHubCache) =
    runTransaction:
      def createDBInfo: DBIO[Int] =
        for
          _ <- databaseInfoTable.schema.createIfNotExists
          v <- databaseInfoTable.map(_.version).result
        yield v.headOption.getOrElse(0)

      def updateVersion =
        for
          _ <- databaseInfoTable.delete
          _ <- databaseInfoTable += DatabaseInfo.Data(DB.version)
        yield ()

      def upgradesValue(version: Int) =
        for
          u <- upgrades.dropWhile(_.version <= version)
          _ = tool.log(s"upgrade db to version ${u.version}")
        yield u.upgrade

      def create =
        for
          v <- createDBInfo
          _ = tool.log(s"found db version $v")
          _ <- if v > DB.version then DBIO.failed(new RuntimeException(s"Can't downgrade DB (version ${v} to ${DB.version})")) else DBIO.successful(())
          ups <- DBIO.sequence(upgradesValue(v))
          _ <- updateVersion
        yield ()

      create

    runTransaction:
      val admin = userWithDefault("Admin", "Admin", "admin@openmole.org", salted("admin"), "OpenMOLE", role = Role.Admin)
      for
        e <- userTable.result
        _ <- if e.isEmpty then userTable += admin else DBIO.successful(())
      yield ()

    // TODO remove for testing only
    //val user = User.withDefault("user", "Ali", "user@user.com", salted("user"), "CNRS")
    // val newUser = RegisterUser("user2", "Sarah","user2@user2.com", salted("user2"), "CNRS", DB.checked)
    //addUser(user)
    //addRegisteringUser(newUser)

