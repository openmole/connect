package org.openmoleconnect.server

import java.text.SimpleDateFormat
import java.util

import shared.Data.UserData

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import slick.jdbc.H2Profile.api._
import DBQueries._
import shared._
import slick.model.ForeignKey

object DB {


  // USERS

  case class UUID(value: String) extends MappedTo[String]

  case class Email(value: String) extends MappedTo[String]

  case class Password(value: String) extends MappedTo[String]

  case class Role(value: String) extends MappedTo[String]

  case class Version(value: String) extends MappedTo[String]

  val admin = Role("Admin")
  val simpleUser = Role("User")

  case class User(name: String, email: Email, password: Password, omVersion: Version, lastAccess: Long, role: Role = simpleUser, uuid: UUID = UUID(""))

  implicit def userToUserData(users: Seq[User]): Seq[Data.UserData] = users.map { u =>
    Data.UserData(
      u.name.value,
      u.email.value,
      u.password.value,
      u.role.value,
      u.omVersion.value,
      u.lastAccess.value)
  }

  def toUser(uuid: UUID, userData: UserData): User = User(
    userData.name,
    Email(userData.email),
    Password(userData.password),
    Version(userData.omVersion),
    userData.lastAccess,
    Role(userData.role),
    uuid
  )

  class Users(tag: Tag) extends Table[(UUID, String, Email, Password, Role, Version, Long)](tag, "USERS") {
    def uuid = column[UUID]("UUID", O.PrimaryKey)

    def name = column[String]("NAME")

    def email = column[Email]("EMAIL")

    def password = column[Password]("PASSWORD")

    def role = column[Role]("ROLE")

    def omVersion = column[Version]("OMVERSION")

    def lastAccess = column[Long]("LASTACCESS")


    def * = (uuid, name, email, password, role, omVersion, lastAccess)
  }

  val userTable = TableQuery[Users]

  val db: Database = Database.forDriver(
    driver = new org.h2.Driver,
    url = s"jdbc:h2:/${Settings.location}/db"
  )

  // TRANSACTIONS
  def runTransaction[E <: Effect](actions: DBIOAction[_, NoStream, E]*) =
    Await.result(
      db.run(
        DBIO.seq(
          actions: _*
        ).transactionally), Duration.Inf)

  def initDB = {
    runTransaction(userTable.schema.createIfNotExists)
    if (DB.users.isEmpty) {
      DB.addUser("admin", DB.Email("admin@admin.com"), DB.Password("admin"), Utils.openmoleversion.stable, JWT.now, DB.admin, UUID("foo-123-567-foo"))
    }
  }

  def addUser(name: String, email: Email, password: Password, omVersion: Version, lastAccess: Long, role: Role = simpleUser): Unit = {
    if (!exists(email)) {
      addUser(name, email, password, omVersion, lastAccess, role, UUID(util.UUID.randomUUID().toString))
    }
  }

  def addUser(name: String, email: Email, password: Password, omVersion: Version, lastAccess: Long, role: Role, uuid: UUID): Unit = {
    if (!exists(email)) {
      runTransaction(
        userTable += (uuid, name, email, password, role, omVersion, lastAccess)
      )
    }
  }

  def upsert(user: User) = {
    runTransaction(
      userTable.insertOrUpdate(user.uuid, user.name, user.email, user.password, user.role, user.omVersion, user.lastAccess)
    )
  }

  def setLastAccess(email: Email, lastAccess: Long) =
    runTransaction {
      getLastAccesQuery(email).update(lastAccess)
    }

  def delete(user: User) = {
    runTransaction(
      userTable.filter {
        _.uuid === user.uuid
      }.delete
    )
  }

  //QUERIES
  // val users = Seq(User(Login("foo"), Password("foo"), UUID("foo-123-567-foo")), User(Login("bar"), Password("bar"), UUID("bar-123-567-bar")))


  def uuid(email: Email): Option[UUID] = users.find(_.email == email).map {
    _.uuid
  }

  def uuid(email: Email, password: Password): Option[UUID] = users.find(u => u.email == email && u.password == password).map {
    _.uuid
  }

  def get(email: Email) = {
    runQuery(
      getQuery(email)
    ).headOption
  }

  def users = runQuery(
    for {
      u <- userTable
    } yield (u)
  )

  def exists(email: Email) = get(email).isDefined

  def isAdmin(email: Email) = get(email).map {
    _.role
  } == Some(admin)
}
