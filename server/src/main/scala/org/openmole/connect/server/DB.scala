package org.openmole.connect.server

import org.openmole.connect.server.DBQueries.*
import org.openmole.connect.shared.Data
import shared.*
import org.openmole.connect.shared.Data.UserData
import slick.jdbc.H2Profile.api.*
import slick.model.ForeignKey

import java.text.SimpleDateFormat
import java.util
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object DB:
  // USERS

  def randomUUID = java.util.UUID.randomUUID().toString

  type UUID = String
  type Email = String
  type Password = String
  type Role = String
  type Version = String
  type Storage = String

  val admin = "Admin"
  val simpleUser = "User"

  object User:
    def toUserData(u: User): Data.UserData =
      Data.UserData(
        u.name.value,
        u.email.value,
        //u.password.value,
        u.role.value,
        u.omVersion.value,
        u.storage.value,
        u.lastAccess.value)


  case class User(name: String, email: Email, password: Password, omVersion: Version, storage: Storage, lastAccess: Long, role: Role = simpleUser, uuid: UUID = "")



//  implicit def optionUserToOptionUserData(auser: Option[User]): Option[UserData] =
//    auser.flatMap { u => userToUserData(Seq(u)).headOption }

//  def toUser(uuid: UUID, userData: UserData): User = User(
//    userData.name,
//    userData.email,
//    userData.password,
//    userData.omVersion,
//    userData.storage,
//    userData.lastAccess,
//    userData.role,
//    uuid
//  )

  class Users(tag: Tag) extends Table[(UUID, String, Email, Password, Role, Version, Storage, Long)](tag, "USERS"):
    def uuid = column[UUID]("UUID", O.PrimaryKey)
    def name = column[String]("NAME")
    def email = column[Email]("EMAIL")
    def password = column[Password]("PASSWORD")
    def role = column[Role]("ROLE")
    def omVersion = column[Version]("OMVERSION")
    def storage = column[Storage]("STORAGE")
    def lastAccess = column[Long]("LASTACCESS")
    def * = (uuid, name, email, password, role, omVersion, storage, lastAccess)

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

  def initDB(salt: String) =
    runTransaction(userTable.schema.createIfNotExists)
    if DB.users.isEmpty
    then DB.addUser("admin", "admin@admin.com", "admin", Utils.openmoleversion.stable, "0Gi", Utils.now, DB.admin, randomUUID, salt = salt)
//      DB.addUser("foo", DB.Email("foo@foo.com"), DB.Password("foo"), Utils.openmoleversion.stable, JWT.now, DB.simpleUser, UUID("bar-123-567-bar"))
//      DB.addUser("toto", DB.Email("toto@toto.com"), DB.Password("toto"), Utils.openmoleversion.stable, JWT.now, DB.simpleUser, UUID("openmole-toto")


  def addUser(name: String, email: Email, password: Password, omVersion: Version, storage: Storage, lastAccess: Long, role: Role = simpleUser, salt: String): Unit =
    if !exists(email)
    then addUser(name, email, password, omVersion, storage, lastAccess, role, util.UUID.randomUUID().toString)

  def addUser(name: String, email: Email, password: Password, omVersion: Version, storage: Storage, lastAccess: Long, role: Role, uuid: UUID, salt: String): Unit =
    if !exists(email)
    then
      runTransaction(
        userTable += (uuid, name, email, Hash.hash(password, salt), role, omVersion, storage, lastAccess)
      )

  def upsert(user: User, salt: String) =
    runTransaction(
      userTable.insertOrUpdate(user.uuid, user.name, user.email, Hash.hash(user.password, salt), user.role, user.omVersion, user.storage, user.lastAccess)
    )

  def setLastAccess(email: Email, lastAccess: Long) =
    runTransaction {
      getLastAccesQuery(email).update(lastAccess)
    }

  def delete(user: User) =
    runTransaction(
      userTable.filter {
        _.uuid === user.uuid
      }.delete
    )

  //QUERIES
  // val users = Seq(User(Login("foo"), Password("foo"), UUID("foo-123-567-foo")), User(Login("bar"), Password("bar"), UUID("bar-123-567-bar")))


  def uuid(email: Email): Option[UUID] = users.find(_.email == email).map { _.uuid }

  def uuid(email: Email, password: Password, salt: String): Option[UUID] = users.find(u => u.email == email && u.password == Hash.hash(password, salt)).map { _.uuid }

  def uuids = users.map { _.uuid }
  def email(uuid: UUID) = users.find(u => u.uuid == uuid).map { _.email }

  def get(email: Email) =
    runQuery(
      getQuery(email)
    ).headOption

  def users = runQuery(
    for {
      u <- userTable
    } yield u
  )
  def exists(email: Email) = get(email).isDefined

  def isAdmin(email: Email) = get(email).map { _.role }.contains(admin)


