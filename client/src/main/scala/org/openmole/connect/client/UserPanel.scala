package org.openmole.connect.client

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

object UserPanel {

  lazy val rowFlex = Seq(display.flex, flexDirection.row, justifyContent.spaceAround)
  lazy val columnFlex = Seq(display.flex, flexDirection.column, justifyContent.center, alignItems.flexStart)

  lazy val roles = Seq(Data.user, connect.shared.Data.admin)
  lazy val roleFilter = (r: Role) => r == admin

  @JSExportTopLevel("user")
  def user(): Unit = {

    val currentUser: Var[Option[UserData]] = Var(None)

    def getUser = ???
    //      Post[UserApi].user().call().foreach { u =>
    //        currentUser() = u
    //      }
    //
    def upsert(userData: UserData) = ???
    //      Post[UserApi].upserted(userData).call().foreach { u =>
    //        currentUser() = u
    //      }

    lazy val userPanel = div(
      child <-- currentUser.signal.map:
        case Some(uu)=>
          val panel = editableData(
            uu.name,
            uu.email,
            uu.password,
            uu.role,
            None,
            uu.omVersion,
            uu.storage,
            uu.lastAccess,
            editableEmail = false,
            editableRole = false,
            upserting = (userData: UserData) => upsert(userData))

          div(maxWidth := "1000", margin := "40px auto",
            img(src := "img/logo.png", Css.adminLogoStyle),
            ConnectUtils.logoutItem.amend(display.flex, flexDirection.row, justifyContent.flexEnd),
            div(display.flex, flexDirection.row, justifyContent.flexStart, marginLeft := "50", marginBottom := "20", marginTop := "80",
              //  div(width := 350, margin.auto, paddingTop := 200 )(
              panel
            )
          )
        case None=> div()
    )

    renderOnDomContentLoaded(dom.document.body, userPanel)
    getUser

  }


  def editableData(userName: String = "",
                   userEmail: String = "",
                   userPassword: String = "",
                   userRole: Role = "",
                   podInfo: Option[PodInfo] = None,
                   userOMVersion: String,
                   userStorage: String,
                   userLastAccess: Long,
                   editableEmail: Boolean,
                   editableRole: Boolean,
                   expanded: Boolean = false,
                   editing: Boolean = false,
                   upserting: (UserData) => Unit = (u: UserData) => ()
                  ) = {

    def roleStyle(s: Role) =
      if (s == admin) badge_success
      else badge_secondary

    div(columnFlex, width := "300",
      div(userName, padding := "10"),
      div(userEmail,padding := "10"),
      div(userPassword, padding := "10"),
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
