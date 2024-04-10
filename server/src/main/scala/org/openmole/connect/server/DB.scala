package org.openmole.connect.server

import org.openmole.connect.server.DBQueries.*
import org.openmole.connect.shared.Data
import slick.jdbc.H2Profile.api.*
import slick.model.ForeignKey

import java.text.SimpleDateFormat
import java.util
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object DB:
  object Salt:
    def apply(s: String): Salt = s
    def value(s: Salt): String = s

  opaque type Salt = String

  // USERS
  def dbVersion = 1
  def randomUUID = java.util.UUID.randomUUID().toString

  type UUID = String
  type Email = String
  type Password = String
  type Role = String
  type Version = String

  type Storage = Int
  type Memory = Int

  val admin = "Admin"
  val user = "User"

  object User:
    def isAdmin(u: User) = u.role == admin

    def toUserData(u: User): Data.User =
      Data.User(
        u.name,
        u.email,
        u.role,
        u.omVersion,
        u.storage,
        u.memory,
        u.cpu,
        u.openMOLEMemory,
        u.lastAccess,
        u.created)


  case class User(
    name: String,
    email: Email,
    password: Password,
    omVersion: Version,
    storage: Storage,
    memory: Memory,
    cpu: Int,
    openMOLEMemory: Memory,
    lastAccess: Long,
    created: Long,
    role: Role = user,
    uuid: UUID = randomUUID)



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

  class Users(tag: Tag) extends Table[User](tag, "USERS"):
    def uuid = column[UUID]("UUID", O.PrimaryKey)
    def name = column[String]("NAME")
    def email = column[Email]("EMAIL", O.Unique)
    def password = column[Password]("PASSWORD")
    def role = column[Role]("ROLE")
    def omVersion = column[Version]("OMVERSION")

    def storage = column[Storage]("STORAGE_REQUIREMENT")
    def memory = column[Storage]("MEMORY_LIMIT")
    def cpu = column[Int]("CPU_LIMIT")
    def omMemory = column[Storage]("OPENMOLE_MEMORY")

    def lastAccess = column[Long]("LASTACCESS")
    def created = column[Long]("CREATED")
    def * = (name, email, password, omVersion, storage, memory, cpu, omMemory, lastAccess, created, role, uuid).mapTo[User]
    def mailIndex = index("index_mail", email, unique = true)

  val userTable = TableQuery[Users]


  class DatabaseInfo(tag: Tag) extends Table[(Int)](tag, "DATABASEINFO"):
    def version = column[Int]("VERSION")
    def * = (version)

  val databaseInfoTable = TableQuery[DatabaseInfo]

  val db: Database = Database.forDriver(
    driver = new org.h2.Driver,
    url = s"jdbc:h2:/${Settings.location}/db"
  )

  def runTransaction[E <: Effect, T](action: DBIOAction[T, NoStream, E]): T =
    Await.result(db.run(action), Duration.Inf)

  def initDB()(using Salt) =
    val create = DBIO.seq(databaseInfoTable.schema.createIfNotExists, userTable.schema.createIfNotExists)
    runTransaction(create)

    val now = Utils.now
    val admin = User("admin", "admin@admin.com", salted("admin"), "latest", 10240, 2048, 2, 1024, now, now, DB.admin, randomUUID)
    val user = User("user", "user@user.com", salted("user"), "latest", 10240, 2048, 2, 1024, now, now, DB.user, randomUUID)

    runTransaction:
      for
        e <- userTable.result
        if e.isEmpty
        _ <- userTable ++= Seq(admin, user)
      yield ()

    runTransaction:
      for
        e <- databaseInfoTable.result
        if e.isEmpty
        _ <- databaseInfoTable += (dbVersion)
      yield ()


  def addUser(user: User)(using salt: Salt): Unit =
    runTransaction:
      for
        e <- userTable.filter(u => u.email === user.email).result
        if e.isEmpty
        _ <- userTable += user
      yield ()

//  def upsert(user: User, salt: String) =
//    runTransaction(
//      userTable.insertOrUpdate(user.uuid, user.name, user.email, Hash.hash(user.password, salt), user.role, user.omVersion, user.storage, user.lastAccess)
//    )
//
//  def setLastAccess(email: Email, lastAccess: Long) =
//    runTransaction {
//      getLastAccesQuery(email).update(lastAccess)
//    }
//
//  def delete(user: User) =
//    runTransaction(
//      userTable.filter {
//        _.uuid === user.uuid
//      }.delete
//    )

  //QUERIES
  // val users = Seq(User(Login("foo"), Password("foo"), UUID("foo-123-567-foo")), User(Login("bar"), Password("bar"), UUID("bar-123-567-bar")))


  def userFromUUID(uuid: UUID) =
    runUserQuery:
      userTable.filter(u => u.uuid === uuid)
    .headOption

  def user(email: Email, password: Password)(using salt: Salt): Option[User] =
    runUserQuery:
      userTable.filter(u => u.email === email && u.password === salted(password))
    .headOption

  def userFromSaltedPassword(email: Email, salted: Password): Option[User] =
    runUserQuery:
      userTable.filter(u => u.email === email && u.password === salted)
    .headOption

  def updadeLastAccess(uuid: UUID) =
    runTransaction:
      val q = userTable.filter(_.uuid === uuid).map(_.lastAccess)
      q.update(Utils.now)

  def updadeOMVersion(uuid: UUID, version: String) =
    runTransaction:
      val q = userTable.filter(_.uuid === uuid).map(_.omVersion)
      q.update(version)

  def users: Seq[User] = runUserQuery(userTable)

  def updatePassword(uuid: UUID, old: Password, password: Password)(using salt: Salt): Boolean =
    runTransaction:
      val q = for { user <- userTable if user.uuid === uuid && user.password === salted(old) } yield user.password
      q.update(salted(password)).map(_ > 0)


  def salted(password: Password)(using salt: Salt) = Utils.hash(password, Salt.value(salt))

//def isAdmin(email: Email) = users.find(_.email == email).map { _.role }.contains(admin)


