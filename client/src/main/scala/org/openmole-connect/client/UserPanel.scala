package org.openmoleconnect.client

import java.nio.ByteBuffer

import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel
import boopickle.Default._
import shared.{AdminApi, UserApi}
import autowire._
import rx._
import scaladget.bootstrapnative._
import scalatags.JsDom.styles
import shared.Data._

import scala.concurrent.ExecutionContext.Implicits.global
import scaladget.bootstrapnative.bsn._
import scaladget.tools.{ModifierSeq, _}
import scalatags.JsDom.all._

import ConnectUtils._

object UserPanel {

  lazy val rowFlex = Seq(styles.display.flex, flexDirection.row, justifyContent.spaceAround)
  lazy val columnFlex = Seq(styles.display.flex, flexDirection.column, styles.justifyContent.center, alignItems.flexStart)

  lazy val roles = Seq(shared.Data.user, shared.Data.admin)
  lazy val roleFilter = (r: Role) => r == admin

  @JSExportTopLevel("user")
  def user(): Unit = {

    val currentUser: Var[Option[UserData]] = Var(None)

    def getUser =
      Post[UserApi].user().call().foreach { u =>
        currentUser() = u
      }

    def upsert(userData: UserData) =
      Post[UserApi].upserted(userData).call().foreach { u =>
        currentUser() = u
      }

    lazy val userPanel = div(
      Rx {
        currentUser().map { uu =>
          val panel = editableData(
            uu.name,
            uu.email,
            uu.password,
            uu.role,
            None,
            uu.omVersion,
            uu.lastAccess,
            editableEmail = false,
            editableRole = false,
            upserting = (userData: UserData) => upsert(userData)).build

          div(maxWidth := 1000, margin := "40px auto")(
            img(src := "img/logo.png", Css.adminLogoStyle),
            ConnectUtils.logoutItem(styles.display.flex, flexDirection.row, justifyContent.flexEnd),
            div(styles.display.flex, flexDirection.row, justifyContent.flexStart, marginLeft := 50, marginBottom := 20, marginTop := 80)(
              //  div(width := 350, margin.auto, paddingTop := 200 )(
              panel)
          )
        }.getOrElse(div())
      }
    )

    dom.document.body.appendChild(userPanel)
    getUser

  }


  def editableData(userName: String = "",
                   userEmail: String = "",
                   userPassword: String = "",
                   userRole: Role = "",
                   podInfo: Option[PodInfo] = None,
                   userOMVersion: String,
                   userLastAccess: Long,
                   editableEmail: Boolean,
                   editableRole: Boolean,
                   expanded: Boolean = false,
                   editing: Boolean = false,
                   upserting: (UserData) => Unit = (u: UserData) => ()
                  ): GroupCell = {

    def roleStyle(s: Role) =
      if (s == admin) label_success
      else label_default

    val name = TextCell(userName, Some("Name"), editing)
    val email = TextCell(userEmail, Some("Email"), editing, editable = editableEmail)
    val password = PasswordCell(userPassword, Some("Password"), editing)
    val role = LabelCell(userRole, roles, optionStyle = roleStyle, title = Some("Role"), editing = editing, editable = editableRole)

    lazy val groupCell: GroupCell = GroupCell(
      div(columnFlex, width := 300)(
        name.build(padding := 10),
        email.build(padding := 10),
        password.build(padding := 10),
        role.build(padding := 10),
        span(rowFlex, marginTop := 50)(
          Rx {
            if (name.editMode()) button(btn_primary, "Save", onclick := { () =>
              groupCell.switch
              val userRole: Role = role.get
              val modifiedUser = UserData(name.get, email.get, password.get, userRole, userOMVersion, userLastAccess)
              upserting(modifiedUser)
            })
            else button(btn_default, "Edit", onclick := { () =>
              groupCell.switch
            })
          }
        )),
      name, email, password, role
    )

    groupCell
  }

}
