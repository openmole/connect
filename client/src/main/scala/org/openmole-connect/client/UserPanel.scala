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

  lazy val roles = Seq(user, shared.Data.admin)
  lazy val roleFilter = (r: Role) => r == shared.Data.admin

  def editableData(userName: String = "",
                   userEmail: String = "",
                   userPassword: String = "",
                   userRole: Role = "",
                   userStatus: Status = user,
                   userOMVersion: String,
                   userLastAccess: String,
                   expanded: Boolean = false,
                   upserting: (UserData) => Unit
                  ): GroupCell = {

    def roleStyle(s: Role) =
      if (s == shared.Data.admin) label_success
      else label_default

    val name = TextCell(userName, Some("Name"))
    val email = TextCell(userEmail, Some("Email"))
    val password = PasswordCell(userPassword, Some("Password"))
    val role = LabelCell(userRole, roles, optionStyle = roleStyle, title = Some("Role"))

    val rowEdit = Var(false)

    lazy val groupCell: GroupCell = GroupCell(
      div(columnFlex, width := "100%")(
        name.build(padding := 10),
        email.build(padding := 10),
        password.build(padding := 10),
        role.build(padding := 10),
        span(rowFlex, marginTop := 50)(
          Rx {
            if (rowEdit()) button(btn_primary, "Save", onclick := { () =>
              val userRole: Role = role.get
              val modifiedUser = UserData(name.get, email.get, password.get, userRole, userOMVersion, userLastAccess)
              upserting(modifiedUser)
              rowEdit.update(!rowEdit.now)
            })
            else button(btn_default, "Edit", onclick := { () =>
              //button("Edit", btn_default, onclick := { () =>
              rowEdit.update(!rowEdit.now)
              groupCell.switch
            })
          }
        )),
      name, email, password, role
    )

    groupCell
  }

}
