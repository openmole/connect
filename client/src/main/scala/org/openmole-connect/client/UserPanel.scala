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

  lazy val rowFlex = Seq(styles.display.flex, flexDirection.row, justifyContent.spaceAround, alignItems.center)
  lazy val columnFlex = Seq(styles.display.flex, flexDirection.column, styles.justifyContent.center)

  lazy val roles = Seq(user, shared.Data.admin)
  lazy val roleFilter = (r: Role) => r == shared.Data.admin

  def editableData(userName: String = "",
                   userEmail: String = "",
                   userPassword: String = "",
                   userRole: Role = "",
                   userStatus: Status = user,
                   expanded: Boolean = false,
                   upserting: (UserData) => Unit): GroupCell = {
    val aVar = Var(expanded)

    def roleStyle(s: Role) =
      if (s == shared.Data.admin) label_success
      else label_default

    val name = TextCell(userName, Some("Name"))
    val email = TextCell(userEmail, Some("Email"))
    val password = PasswordCell(userPassword, Some("Password"))
    val role = LabelCell(userRole, roles, optionStyle = roleStyle, title = Some("Role"))

    val rowEdit = Var(false)

    val buttonStyle: ModifierSeq = Seq(
      fontSize := 22,
      color := "#23527c",
      opacity := 0.8
    )

    lazy val groupCell: GroupCell = GroupCell(
      div(rowFlex, width := "100%")(
        name.build(padding := 10),
        email.build(padding := 10),
        password.build(padding := 10),
        role.build(padding := 10),
        span(
          Rx {
            if (rowEdit()) glyphSpan(glyph_save +++ buttonStyle +++ toClass("actionIcon"), () => {
              val userRole: Role = role.get
              val modifiedUser = UserData(name.get, email.get, password.get, userRole)
              upserting(modifiedUser)
              rowEdit.update(!rowEdit.now)
            })
            else glyphSpan(glyph_edit2 +++ buttonStyle +++ toClass("actionIcon"), () => {
              //button("Edit", btn_default, onclick := { () =>
              rowEdit.update(!rowEdit.now)
              groupCell.switch
            })
          }
        )
      ), name, email, password, role)

    groupCell
  }

}
