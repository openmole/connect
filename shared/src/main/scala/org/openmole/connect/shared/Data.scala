package org.openmole.connect.shared

object Data:
  val connectionRoute = "connection"
  val validateRoute = "validate"
  val resetPasswordRoute = "password-reset"
  val disconnectRoute = "disconnect"
  val impersonateRoute = "impersonate"
  val downloadUserRoute = "download-users"

  val openAPIRoute = "api"
  val userAPIRoute = "user"
  val adminAPIRoute = "admin"
  val openMOLERoute = "openmole"

  enum UserStatus:
    case Active

  enum Role:
    case Admin, User

  object EmailStatus:
    def hasBeenChecked(s: EmailStatus) = s == EmailStatus.Checked

  enum EmailStatus:
    case Unchecked, Checked

  object PodInfo:
    object Status:
      extension (s: Status)
        def isRunning =
          s match
            case _: Running => true
            case _ => false

        def isStopped =
          s match
            case _: Terminated | Inactive => true
            case _ => false

        def value =
          s match
            case Creating => "Creating"
            case _: Running => "Running"
            case _: Waiting => "Starting"
            case Terminating => "Terminating"
            case _: Terminated => "Terminated"
            case Inactive => "Inactive"

    enum Status:
      case Creating
      case Running(startedAt: Long) extends Status
      case Waiting(message: String) extends Status
      case Terminated(message: String, finishedAt: Long) extends Status
      case Terminating
      case Inactive

  case class PodInfo(
    name: String,
    status: Option[PodInfo.Status],
    restarts: Option[Int],
    createTime: Option[Long],
    podIP: Option[String],
    userUUID: Option[String])

  case class User(
    uuid: String,
    name: String,
    firstName: String,
    email: String,
    emailStatus: EmailStatus,
    institution: String,
    role: Role,
    status: UserStatus,
    omVersion: String,
    memory: Int,
    cpu: Double,
    openMOLEMemory: Int,
    lastAccess: Long, created: Long)
  
  case class UserAndPodInfo(
     user: User, 
     podInfo: Option[PodInfo])
  
  case class RegisterUser(
    uuid: String,
    name: String,
    firstName: String,
    email: String,
    institution: String,
    emailStatus: EmailStatus,
    created: Long)

  case class ConnectRegister(info1: String, info2: String)

enum K8ActionResult:
  case K8Success(message: String) extends K8ActionResult
  case K8Failure(message: String, stackTrace: String) extends K8ActionResult


case class Storage(used: Double, available: Double)
