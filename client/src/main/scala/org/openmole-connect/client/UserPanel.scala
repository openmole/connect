package org.openmoleconnect.client

import rx.Rx
import scaladget.bootstrapnative._
import scaladget.tools.toClass
import scalatags.JsDom.styles
import scaladget.bootstrapnative.bsn._
import scaladget.tools._
import scalatags.JsDom.all._
import shared.Data._
import rx._

object UserPanel {

  lazy val rowFlex = Seq(styles.display.flex, flexDirection.row, justifyContent.spaceAround)
  lazy val columnFlex = Seq(styles.display.flex, flexDirection.column, styles.justifyContent.center, alignItems.flexStart)

  lazy val roles = Seq(user, admin)
  lazy val roleFilter = (r: Role) => r == admin

  def editableData(userName: String = "",
                   userEmail: String = "",
                   userPassword: String = "",
                   userRole: Role = "",
                   podInfo: Option[PodInfo] = None,
                   userOMVersion: String,
                   userLastAccess: Long,
                   expanded: Boolean = false,
                   editing: Boolean = false,
                   upserting: (UserData) => Unit
                  ): GroupCell = {

    def roleStyle(s: Role) =
      if (s == admin) label_success
      else label_default

    val name = TextCell(userName, Some("Name"), editing)
    val email = TextCell(userEmail, Some("Email"), editing)
    val password = PasswordCell(userPassword, Some("Password"), editing)
    val role = LabelCell(userRole, roles, optionStyle = roleStyle, title = Some("Role"), editing = editing)

    lazy val groupCell: GroupCell = GroupCell(
      div(columnFlex, width := "100%")(
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
