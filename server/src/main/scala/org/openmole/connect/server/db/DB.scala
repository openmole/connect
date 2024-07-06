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

  import DBSchemaV1.{*, given}

  export DBSchemaV1.User
  export DBSchemaV1.RegisterUser


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
    Await.result(db.run(action), Duration.Inf)


  def initDB()(using Salt, DockerHubCache) =
    val schema = databaseInfoTable.schema ++ userTable.schema ++ registerUserTable.schema
    scala.util.Try:
      runTransaction(schema.createIfNotExists)

    runTransaction:
      val admin = User.withDefault("Admin", "Admin", "admin@openmole.org", salted("admin"), "OpenMOLE", role = Role.Admin)
      for
        e <- userTable.result
        _ <- if e.isEmpty then userTable += admin else DBIO.successful(())
      yield ()

    // TODO remove for testing only
    //val user = User.withDefault("user", "Ali", "user@user.com", salted("user"), "CNRS")
    // val newUser = RegisterUser("user2", "Sarah","user2@user2.com", salted("user2"), "CNRS", DB.checked)
    //addUser(user)
    //addRegisteringUser(newUser)

    runTransaction:
      for
        e <- databaseInfoTable.result
        _ <- if e.isEmpty then databaseInfoTable += DatabaseInfo.Data(dbVersion) else DBIO.successful(())
      yield ()


  def addUser(user: User): Unit =
    runTransaction:
      addUserTransaction(user)

  private def addUserTransaction(user: User) =
    for
      e <- userTable.filter(u => u.email === user.email).result
      _ <- if e.isEmpty then userTable += user else DBIO.successful(())
    yield ()


  def addRegisteringUser(registerUser: RegisterUser)(using salt: Salt): Option[RegisterUser] =
    runTransaction:
      val r = userTable.filter(u => u.email === registerUser.email)
      val q = registerUserTable.filter(r => r.email === registerUser.email)

      val insert =
        for
          _ <- q.delete
          _ <- registerUserTable += registerUser
          r <- q.result
        yield r

      for
        ex <- r.result
        r <- if ex.isEmpty then insert else DBIO.successful(Seq())
      yield r

    .headOption

  def validateRegistering(uuid: UUID, secret: Secret): Boolean =
    val res =
      runTransaction:
        val q =
          for
            ru <- registerUserTable.filter(r => r.uuid === uuid && r.validationSecret === secret)
          yield ru.emailStatus

        q.update(Data.EmailStatus.Checked)

    res >= 1

  def deleteRegistering(uuid: UUID): Int =
    runTransaction:
      registerUserTable.filter(_.uuid === uuid).delete

  def promoteRegistering(uuid: UUID)(using DockerHubCache): Unit =
    runTransaction:
      for
        ru <- registerUserTable.filter(_.uuid === uuid).result
        user = ru.map(RegisterUser.toUser)
        _ <- DBIO.sequence(user.map(addUserTransaction))
        _ <- registerUserTable.filter(_.uuid === uuid).delete
      yield ()


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

  def updadeOMVersion(uuid: UUID, version: String) =
    runTransaction:
      val q = userTable.filter(_.uuid === uuid).map(_.omVersion)
      q.update(version)

  def users: Seq[User] = runTransaction(userTable.result)

  def registerUsers: Seq[RegisterUser] = runTransaction(registerUserTable.result)

  def updatePassword(uuid: UUID, password: Password, old: Option[Password] = None)(using Salt, Authentication.AuthenticationCache): Boolean =
    summon[Authentication.AuthenticationCache].user.invalidate(uuid)
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

  def deleteUser(uuid: UUID)(using Authentication.AuthenticationCache) =
    summon[Authentication.AuthenticationCache].user.invalidate(uuid)
    runTransaction:
      userTable.filter(_.uuid === uuid).delete

  def salted(password: Password)(using salt: Salt) = tool.hash(password, Salt.value(salt))
