package org.openmoleconnect.adminclient

import java.nio.ByteBuffer

import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel
import boopickle.Default._
import shared.AdminApi
import autowire._
import rx._
import scaladget.bootstrapnative.Table.StaticSubRow
import scaladget.bootstrapnative._
import scaladget.bootstrapnative.bsn.{glyph_edit2, glyph_save}
import scalatags.JsDom.styles
import shared.Data._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}



import scaladget.bootstrapnative.bsn._
import scaladget.tools._
import scalatags.JsDom.all._

object AdminPanel {

  @JSExportTopLevel("admin")
  def admin() = {

   // val users: Var[Seq[UserData]] = Var(Seq())

    println("admin ...")
    lazy val rows: Var[Seq[ExpandableRow]] = Var(Seq())


    def save(expandableRow: ExpandableRow, name: TextCell, email: TextCell, password: PasswordCell, role: LabelCell, status: Status) = {
      //SET USER
      //rows() = rows.now.updated(rows.now.indexOf(expandableRow), buildExpandable(name.get, email.get, password.get, role.get, status, true))
    }

    def closeAll(except: ExpandableRow) = rows.now.filterNot{_ == except}.foreach{_.subRow.trigger() = false}


    lazy val rowFlex = Seq(styles.display.flex, flexDirection.row, justifyContent.spaceAround, alignItems.center)
    lazy val columnFlex = Seq(styles.display.flex, flexDirection.column, styles.justifyContent.center)


    val roles = Seq(user, shared.Data.admin)
    val roleFilter = (r: Role) => r == shared.Data.admin


    def buildExpandable(userName: String, userEmail: String, userPassword: String, userRole: Role, userStatus: Status, expanded: Boolean = false): ExpandableRow = {
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

      lazy val aSubRow: StaticSubRow = StaticSubRow({
        div(height := 120, rowFlex)(
          groupCell.build(margin := 25),
        )
      }, aVar)

      def statusStyle(s: Status) =
        if (s == running) label_success
        else if (s == off) label_default
        else label_danger

      lazy val expandableRow: ExpandableRow = ExpandableRow(EditableRow(Seq(
        TriggerCell(a(userName, onclick := { () =>
          closeAll(expandableRow)
          aVar() = !aVar.now
        })),
        LabelCell(userStatus, Seq(), optionStyle = statusStyle),
      )), aSubRow)

      lazy val groupCell: GroupCell = GroupCell(
        div(rowFlex, width := "100%")(
          name.build(padding := 10),
          email.build(padding := 10),
          password.build(padding := 10),
          role.build(padding := 10),
          span(
            Rx {
              if (rowEdit()) glyphSpan(glyph_save +++ buttonStyle +++ toClass("actionIcon"), () => {
                rowEdit.update(!rowEdit.now)
                save(expandableRow, name, email, password, role, userStatus)
              })
              else glyphSpan(glyph_edit2 +++ buttonStyle +++ toClass("actionIcon"), () => {
                //button("Edit", btn_default, onclick := { () =>
                rowEdit.update(!rowEdit.now)
                groupCell.switch
              })
            }
          )
        ), name, email, password, role)

      expandableRow
    }



    Post[AdminApi].users().call().foreach { us =>
      rows() = us.map{u=>
        buildExpandable(u.name, u.email, u.password, u.role, running)
      }
    }


//    lazy val rows = Var(Seq(
//      buildExpandable("Bobi", "bobi@me.com", "mypass", admin, running),
//      buildExpandable("Barbara", "barb@gmail.com", "toto", user, off)
//    ))


    val headerStyle: ModifierSeq = Seq(
      height := 40.85
    )

    val editablePanel = div(
      Rx {
        div(styles.display.flex, flexDirection.row, styles.justifyContent.center)(
          EdiTable(Seq("Name", "Status"), rows()).render(width := "90%")
        )
      }
    )







//    val table = div(
//      Rx {
//        val t = dataTable.
//          addHeaders("Email", "Password", "Role")
//        users().foreach { u =>
//          t.addRow(u.email, u.password, u.role)
//        }
//        t.render
//      }
//    ).render


    dom.document.body.appendChild(editablePanel.render)
  }


}


object Post extends autowire.Client[ByteBuffer, Pickler, Pickler] {

  override def doCall(req: Request): Future[ByteBuffer] = {
    dom.ext.Ajax.post(
      url = req.path.mkString("/"),
      data = Pickle.intoBytes(req.args),
      responseType = "arraybuffer",
      headers = Map("Content-Type" -> "application/octet-stream")
    ).map(r => TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer]))
  }

  override def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)

  override def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)

}

//}