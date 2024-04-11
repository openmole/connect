package org.openmole.connect.shared

object Data:
  val connectionRoute = "connection"
  val userAPIRoute = "user"
  val adminAPIRoute = "admin"
  val openMOLERoute = "openmole"

  type Role = String
  val admin: Role = "Admin"
  val user: Role = "User"

  type EmailStatus = String
  val checked: EmailStatus = "Checked"
  val unchecked: EmailStatus = "Unchecked"

  object PodInfo:
    object Status:
      extension (s: Status)
        def value =
          s match
            case _: Running => "Running"
            case _: Waiting => "Waiting"
            case _: Terminated => "Terminated"

    enum Status:
      case Running(startedAt: Long) extends Status
      case Waiting(message: String) extends Status
      case Terminated(message: String, finishedAt: Long) extends Status

  case class PodInfo(
    name: String,
    status: Option[PodInfo.Status],
    restarts: Option[Int],
    createTime: Option[Long],
    podIP: Option[String],
    userEmail: Option[String])
    
  case class User(name: String, email: String, institution: String, role: Role, omVersion: String, storage: String, lastAccess: Long)
  case class Register(ame: String, email: String, institution: String, emailStatus: EmailStatus)
  
  trait K8ActionResult

  case class K8Success(message: String) extends K8ActionResult
  case class K8Failure(message: String, stackTrace: String) extends K8ActionResult
