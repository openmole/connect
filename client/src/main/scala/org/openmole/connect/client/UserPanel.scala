package org.openmole.connect.client

import com.raquo.airstream.core
import com.raquo.laminar.api.L.*
import org.openmole.connect
import org.openmole.connect.client.ConnectUtils.*
import org.openmole.connect.shared.{AdminAPI, Data, UserAPI}
import org.openmoleconnect.client.*
import org.scalajs.dom
import scaladget.bootstrapnative.*
import scaladget.bootstrapnative.bsn.*
import org.openmole.connect.shared.Data.*

import java.nio.ByteBuffer
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global

object UserPanel {

  lazy val rowFlex = Seq(display.flex, flexDirection.row, justifyContent.spaceAround)
  lazy val columnFlex = Seq(display.flex, flexDirection.column, justifyContent.center, alignItems.flexStart)

  lazy val roles = Seq(Data.user, connect.shared.Data.admin)
  lazy val roleFilter = (r: Role) => r == admin

  @JSExportTopLevel("user")
  def user(): Unit =
    //val currentUser: Var[Option[UserData]] = Var(None)

    def getUser = UserAPIClient.user(()).future

    def upsert(userData: User) = ???
    //      Post[UserApi].upserted(userData).call().foreach { u =>
    //        currentUser() = u
    //      }

    //getUser.foreach(u => println("user " + u))

    lazy val userPanel = div(
      a("disconnect", href := s"/${Data.disconnectRoute}"),
      br(),
      child <-- Signal.fromFuture(getUser).map:
        case Some(uu) =>
          val panel = editableData(
            uu.name,
            uu.email,
            uu.role,
            None,
            uu.omVersion,
            uu.storage,
            uu.lastAccess,
            editableEmail = false,
            editableRole = false,
            upserting = (userData: User) => upsert(userData))

          val podInfo: Var[Option[PodInfo]] = Var(None)

          div(maxWidth := "1000", margin := "40px auto",
            img(src := "img/logo.png", Css.adminLogoStyle),
            ConnectUtils.logoutItem.amend(display.flex, flexDirection.row, justifyContent.flexEnd),
            div(display.flex, flexDirection.row, justifyContent.flexStart, marginLeft := "50", marginBottom := "20", marginTop := "80", panel),
            EventStream.periodic(5000).toObservable -->
              Observer: _ =>
                UserAPIClient.instance(()).future.foreach(podInfo.set),
            child <--
              podInfo.signal.map:
                case None =>
                  div(
                    "Launch OpenMOLE",
                    onClick --> { _ =>
                      UserAPIClient.launch(()).future.foreach(podInfo.set)
                    },
                    cursor.pointer
                  )
                case Some(podInfo) =>
                  podInfo.status match
                    case Some(t: PodInfo.Status.Terminated) =>
                      s"OpenMOLE is stopping since ${t.finishedAt}: ${t.message}"
                    case Some(t: PodInfo.Status.Waiting) =>
                      s"OpenMOLE is waiting ${t.message}"
                    case _ =>
                      podInfo.podIP match
                        case Some(_) =>
                          div(
                            a("Go to OpenMOLE", href := s"/${Data.openMOLERoute}/"),
                            div(
                              "Stop OpenMOLE",
                              onClick --> { _ => UserAPIClient.stop(()).future },
                              cursor.pointer
                            )
                          )
                        case None => "OpenMOLE is launching"

          )
        case None => div()
    )

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, userPanel)


  def editableData(userName: String = "",
                   userEmail: String = "",
                   userRole: Role = "",
                   podInfo: Option[PodInfo] = None,
                   userOMVersion: String,
                   userStorage: String,
                   userLastAccess: Long,
                   editableEmail: Boolean,
                   editableRole: Boolean,
                   expanded: Boolean = false,
                   editing: Boolean = false,
                   upserting: (User) => Unit = (u: User) => ()
                  ) = {

    def roleStyle(s: Role) =
      if (s == admin) badge_success
      else badge_secondary

    div(columnFlex, width := "300",
      div(userName, padding := "10"),
      div(userEmail,padding := "10"),
      div(userRole, padding := "10"),
      span(rowFlex, marginTop := "50",
        //FIXME
        //          child <-- name.editMode.signal.map { em =>
        //            if (em) button(btn_primary, "Save", onclick --> { _ =>
        //              val userRole: Role = role.get
        //              val modifiedUser = UserData(name.get, email.get, password.get, userRole, userOMVersion, userStorage, userLastAccess)
        //              upserting(modifiedUser)
        //            })
        //            else button(btn_default, "Edit", onclick -> { _ =>
        //            })
        //          }
      )
    )
  }

}
