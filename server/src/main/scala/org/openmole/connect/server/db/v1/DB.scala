package org.openmole.connect.server.db.v1

import better.files.*
import io.github.arainko.ducktape.*
import org.openmole.connect.server.tool.*
import org.openmole.connect.server.{Authentication, Settings, tool}
import org.openmole.connect.shared.Data
import slick.*
import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api.*
import slick.model.ForeignKey

import java.sql.DriverManager
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
  type Institution = String
  type Version = String
  export Data.EmailStatus
  export Data.Role
  export Data.UserStatus
  type Storage = Int
  type Memory = Int
  type Secret = UUID

  given BaseColumnType[EmailStatus] = MappedColumnType.base[EmailStatus, String] (
    s => s.toString,
    v => EmailStatus.valueOf(v)
  )


  given BaseColumnType[Role] = MappedColumnType.base[Role, String] (
    s => s.toString,
    v => Role.valueOf(v)
  )


  given BaseColumnType[UserStatus] = MappedColumnType.base[UserStatus, String](
    s => s.toString,
    v => UserStatus.valueOf(v)
  )

  object User:
    def isAdmin(u: User) = u.role == Role.Admin

    def toData(u: User): Data.User = u.to[Data.User]

    def fromData(u: Data.User): Option[User] = user(u.email)

    def withDefault(name: String, firstName: String, email: String, password: Password, institution: Institution, role: Role = Role.User, status: UserStatus = UserStatus.Active, uuid: UUID = randomUUID) =
      User(name, firstName, email, password, institution, "17.0-SNAPSHOT", 2048, 2, 1024, now, now, role, status, uuid)

  case class User(
    name: String,
    firstName: String,
    email: Email,
    password: Password,
    institution: Institution,
    omVersion: Version,
    memory: Memory,
    cpu: Double,
    openMOLEMemory: Memory,
    lastAccess: Long,
    created: Long,
    role: Role = Role.User,
    status: Data.UserStatus = Data.UserStatus.Active,
    uuid: UUID = randomUUID)

  object RegisterUser:
    def toData(r: RegisterUser): Data.RegisterUser = r.to[Data.RegisterUser]
    def fromData(r: Data.RegisterUser): Option[RegisterUser] = registerUser(r.email)
    def toUser(r: RegisterUser): User = User.withDefault(r.name, r.firstName, r.email, r.password, r.institution, uuid = r.uuid)

  case class RegisterUser(
    name: String,
    firstName: String,
    email: Email,
    password: Password,
    institution: Institution,
    created: Long = now,
    status: EmailStatus = Data.EmailStatus.Unchecked,
    uuid: UUID = randomUUID,
    validationSecret: Secret = randomUUID)

  class Users(tag: Tag) extends Table[User](tag, "USERS"):
    def uuid = column[UUID]("UUID", O.PrimaryKey)
    def name = column[String]("NAME")
    def firstName = column[String]("FIRST_NAME")
    def email = column[Email]("EMAIL", O.Unique)
    def password = column[Password]("PASSWORD")
    def institution = column[Institution]("INSTITUTION")
    def role = column[Role]("ROLE")
    def status = column[UserStatus]("STATUS")
    def omVersion = column[Version]("OMVERSION")
    def memory = column[Storage]("MEMORY_LIMIT")
    def cpu = column[Double]("CPU_LIMIT")
    def omMemory = column[Storage]("OPENMOLE_MEMORY")
    def lastAccess = column[Long]("LASTACCESS")
    def created = column[Long]("CREATED")

    def * = (name, firstName, email, password, institution, omVersion, memory, cpu, omMemory, lastAccess, created, role, status, uuid).mapTo[User]
    def mailIndex = index("index_mail", email, unique = true)

  val userTable = TableQuery[Users]

  class RegisterUsers(tag: Tag) extends Table[RegisterUser](tag, "REGISTERING_USERS"):
    def uuid = column[UUID]("UUID", O.PrimaryKey)
    def name = column[String]("NAME")
    def firstName = column[String]("FIRST_NAME")
    def email = column[Email]("EMAIL", O.Unique)
    def password = column[Password]("PASSWORD")
    def institution = column[Institution]("INSTITUTION")
    def status = column[EmailStatus]("STATUS")
    def validationSecret = column[Secret]("VALIDATION_SECRET")
    def created = column[Long]("CREATED")


    def * = (name, firstName, email, password, institution, created, status, uuid, validationSecret).mapTo[RegisterUser]

  val registerUserTable = TableQuery[RegisterUsers]

  object DatabaseInfo:
    case class Data(version: Int)

  class DatabaseInfo(tag: Tag) extends Table[DatabaseInfo.Data](tag, "DB_INFO"):
    def version = column[Int]("VERSION")

    def * = (version).mapTo[DatabaseInfo.Data]

  val databaseInfoTable = TableQuery[DatabaseInfo]

  val dbFile = Settings.location.toScala / "db"

  lazy val db: Database =
    DriverManager.registerDriver(new org.h2.Driver())
    Database.forURL(url = s"jdbc:h2:${dbFile.pathAsString}")

  def runTransaction[E <: Effect, T](action: DBIOAction[T, NoStream, E]): T =
    Await.result(db.run(action), Duration.Inf)

  def initDB()(using Salt) =
    val schema = databaseInfoTable.schema ++ userTable.schema ++ registerUserTable.schema
    scala.util.Try:
      runTransaction(schema.createIfNotExists)

    runTransaction:
      val admin = User.withDefault("Admin", "Admin", "admin@admin.com", salted("admin"), "CNRS", Role.Admin)
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


  def addRegisteringUser(registerUser: RegisterUser)(using salt: Salt): RegisterUser =
    runTransaction:
      val q = registerUserTable.filter(r => r.email === registerUser.email)
      for
        e <- q.delete
        _ <- registerUserTable += registerUser
        r <- q.result
      yield r
    .head

  def validateRegistering(uuid: UUID, secret: Secret): Boolean =
    val res =
      runTransaction:
        val q =
          for
            ru <- registerUserTable.filter(r => r.uuid === uuid && r.validationSecret === secret)
          yield ru.status

        q.update(Data.EmailStatus.Checked)

    res >= 1

  def deleteRegistering(uuid: UUID): Int =
    runTransaction:
      registerUserTable.filter(_.uuid === uuid).delete

  def promoteRegistering(uuid: UUID): Unit =
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
