package org.openmole.connect.server

import org.openmole.connect.server.DBQueries.*
import org.openmole.connect.shared.Data
import org.openmole.connect.shared.Data.EmailStatus
import slick.jdbc.H2Profile.api.*
import slick.model.ForeignKey

import java.text.SimpleDateFormat
import java.util
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import org.openmole.connect.server.Utils.*
import io.github.arainko.ducktape.*
import better.files.*

import java.sql.DriverManager

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
  type Institution = String
  type Role = String
  type Version = String
  type EmailStatus = String
  type Storage = Int
  type Memory = Int

  val admin: Role = "Admin"
  val user: Role = "User"

  val checked: EmailStatus = "Checked"
  val unchecked: EmailStatus = "Unchecked"

  object User:
    def isAdmin(u: User) = u.role == admin
    def toData(u: User): Data.User = u.to[Data.User]

  case class User(
   name: String,
   email: Email,
   password: Password,
   institution: Institution,
   omVersion: Version,
   storage: Storage,
   memory: Memory,
   cpu: Double,
   openMOLEMemory: Memory,
   lastAccess: Long,
   created: Long,
   role: Role = user,
   uuid: UUID = randomUUID)

  object RegisteringUser:
    def toData(r: RegisteringUser): Data.Register = r.to[Data.Register]

  case class RegisteringUser(
   name: String,
   email: Email,
   password: Password,
   institution: Institution,
   emailStatus: EmailStatus = unchecked,
   UUID: UUID = randomUUID)

  class Users(tag: Tag) extends Table[User](tag, "USERS"):
    def uuid = column[UUID]("UUID", O.PrimaryKey)
    def name = column[String]("NAME")
    def email = column[Email]("EMAIL", O.Unique)
    def password = column[Password]("PASSWORD")
    def institution = column[Institution]("INSTITUTION")
    def role = column[Role]("ROLE")
    def omVersion = column[Version]("OMVERSION")
    def storage = column[Storage]("STORAGE_REQUIREMENT")
    def memory = column[Storage]("MEMORY_LIMIT")
    def cpu = column[Double]("CPU_LIMIT")
    def omMemory = column[Storage]("OPENMOLE_MEMORY")
    def lastAccess = column[Long]("LASTACCESS")
    def created = column[Long]("CREATED")
    def * = (name, email, password, institution, omVersion, storage, memory, cpu, omMemory, lastAccess, created, role, uuid).mapTo[User]
    def mailIndex = index("index_mail", email, unique = true)

  val userTable = TableQuery[Users]

  class RegisteringUsers(tag: Tag) extends Table[RegisteringUser](tag, "REGISTERING_USERS"):
    def uuid = column[UUID]("UUID", O.PrimaryKey)
    def name = column[String]("NAME")
    def email = column[Email]("EMAIL", O.Unique)
    def password = column[Password]("PASSWORD")
    def institution = column[Institution]("INSTITUTION")
    def emailStatus = column[EmailStatus]("EMAILSTATUS")
    def * = (name, email, password, institution, emailStatus, uuid).mapTo[RegisteringUser]

  val registeringUserTable = TableQuery[RegisteringUsers]

  object DatabaseInfo:
    case class Data(version: Int)

  class DatabaseInfo(tag: Tag) extends Table[DatabaseInfo.Data](tag, "DB_INFO"):
    def version = column[Int]("VERSION")
    def * = (version).mapTo[DatabaseInfo.Data]

  val databaseInfoTable = TableQuery[DatabaseInfo]

  val dbFile = Settings.location.toScala / "db"

  lazy val db: Database =
    DriverManager.registerDriver(new org.h2.Driver())
    Database.forURL(url = s"jdbc:h2:${dbFile.pathAsString};AUTOCOMMIT=TRUE")

  def runTransaction[E <: Effect, T](action: DBIOAction[T, NoStream, E]): T =
    Await.result(db.run(action), Duration.Inf)

//  def runUnitTransaction[E <: Effect, Unit](action: DBIOAction[Unit, NoStream, E]): Unit =
//    Await.result(db.run(action), Duration.Inf)


  def initDB()(using Salt) =
    val schema = databaseInfoTable.schema ++ userTable.schema ++ registeringUserTable.schema
    scala.util.Try:
      runTransaction(schema.createIfNotExists)

    runTransaction:
      val admin = User("admin", "admin@admin.com", salted("admin"), "CNRS", "latest", 10240, 2048, 2, 1024, now, now, DB.admin, randomUUID)
      for
        e <- userTable.result
        _ <- if e.isEmpty then userTable += admin else DBIO.successful(())
      yield ()

    // TODO remove for testing only
    val user = User("user", "user@user.com", salted("user"), "CNRS", "latest", 10240, 2048, 2, 1024, now, now, DB.user, randomUUID)
    val newUser = RegisteringUser("user2", "user2@user2.com", salted("user2"), "CNRS", DB.checked, randomUUID)
    addUser(user)
    addRegisteringUser(newUser)

    runTransaction:
      for
        e <- databaseInfoTable.result
        _ <- if e.isEmpty then databaseInfoTable += DatabaseInfo.Data(dbVersion) else DBIO.successful(())
      yield ()


  def addUser(user: User)(using salt: Salt): Unit =
    runTransaction:
      for
        e <- userTable.filter(u => u.email === user.email).result
        _ <- if e.isEmpty then userTable += user else DBIO.successful(())
      yield ()

  def addRegisteringUser(registeringUser: RegisteringUser)(using salt: Salt): Unit =
    runTransaction:
      for
        e <- registeringUserTable.filter(r => r.email === registeringUser.email).result
        _ = if e.isEmpty then registeringUserTable += registeringUser else DBIO.successful(())
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
      val q = for {user <- userTable if user.uuid === uuid && user.password === salted(old)} yield user.password
      q.update(salted(password)).map(_ > 0)


  def salted(password: Password)(using salt: Salt) = Utils.hash(password, Salt.value(salt))

//def isAdmin(email: Email) = users.find(_.email == email).map { _.role }.contains(admin)