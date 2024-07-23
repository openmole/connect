package org.openmole.connect.client

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.openmoleconnect.client.Css
import org.scalajs.dom
import scaladget.bootstrapnative.bsn.*
import org.openmole.connect.shared.Data.*
import org.scalajs.dom.{HTMLFormElement, document}

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global

/*
 * Copyright (C) 11/07/19 // mathieu.leclaire@openmole.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

object Connection:

  enum Form:
    case SignUp, AskPasswordReset
    case SignIn(error: Option[String] = None) extends Form
    case ResetPassword(uuid: String, secret: String) extends Form

  @JSExportTopLevel("connection")
  def connection(error: String) = page(Form.SignIn(Option(error)))

  @JSExportTopLevel("resetPassword")
  def resetPassword(uuid: String, secret: String) = page(Form.ResetPassword(uuid, secret))

  def page(initialForm: Form) =
    val displayedForm: Var[Form] = Var(initialForm)

    def validEmail(email: String): Option[String] =
      if """^[-a-z0-9!#$%&'*+/=?^_`{|}~]+(\.[-a-z0-9!#$%&'*+/=?^_`{|}~]+)*@([a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?\.)*(aero|arpa|asia|biz|cat|com|coop|edu|gov|info|int|jobs|mil|mobi|museum|name|net|org|pro|tel|travel|[a-z][a-z])$""".r.findFirstIn(email) == None
      then Some("Invalid email")
      else None

    def validPassword(password: String, password2: String) =
      if password == password2 && password.nonEmpty then None else Some("Passwords are different")

    def validNotNullString(st: String, field: String): Option[String] =
      if st.isEmpty then Some(s"${field} is mandatory") else None

    def signinForm(error: Option[String]) =
      val connectButton = button("Connect", btn_primary, `type` := "submit", float.right, right := "0")

      div(marginTop := "120", Css.centerColumnFlex, alignItems.flexEnd,
        form(
          Css.centerColumnFlex, alignItems.flexEnd, Css.rowGap10,
          method := "POST",
          action := connectionRoute,
          UIUtils.buildInput("Email"),
          UIUtils.buildInput("Password").amend(`type` := "password"),
          connectButton
          //onMountBind(ctx => ctx.thisNode.ref.elements.namedItem("URL"). := "test")
        ),
        button("Sign Up", cls := "linkLike", onClick --> { _ => displayedForm.set(Form.SignUp) }),
        button("Lost Password", cls := "linkLike", onClick --> { _ => displayedForm.set(Form.AskPasswordReset) }),
        div(error.getOrElse(""), cls := "inputError", minWidth := "0", marginTop := "10")
      )

    lazy val signupForm: ReactiveHtmlElement[HTMLFormElement] =
      val signupError: Var[Seq[Int]] = Var(0 to 4)

      def checkFieldBlock(id: Int, field: String, checker: String => Option[String], inputAttributes: Seq[Modifier[HtmlElement]] = Seq()) =
        val errorMsg: Var[Option[String]] = Var(None)
        lazy val in: Input = UIUtils.buildInput(field).amend(
          onInput --> { _ =>
            errorMsg.set {
              val em = checker(in.ref.value)
              signupError.update: se =>
                (em match
                  case Some(_) => se :+ id
                  case _ => se.filterNot(_ == id)
                  ).distinct

              em
            }
          }).amend(inputAttributes)

        div(Css.centerRowFlex,
          div(cls := "inputError", child <-- errorMsg.signal.map(_.getOrElse(""))),
          in
        )

      def checkPasswordBlock(checker: (String, String) => Option[String]) =
        val errorMsg: Var[Option[String]] = Var(None)
        val in: Input = UIUtils.buildInput("Password").amend(`type` := "password")
        lazy val in2: Input = UIUtils.buildInput("Confirm password").amend(`type` := "password",
          onInput --> { _ =>
            errorMsg.set {
              val em = checker(in.ref.value, in2.ref.value)
              signupError.update { se =>
                (em match
                  case Some(_) => se :+ 4
                  case _ => se.filterNot(_ == 4)
                  ).distinct
              }
              em
            }
          }
        )
        (div(Css.centerRowFlex, div(cls := "inputError", child <-- errorMsg.signal.map(_.getOrElse(""))), in),
          div(Css.centerRowFlex, div(cls := "inputError", in2))
        )


      val passwds = checkPasswordBlock(validPassword)
      form(
        method := "POST",
        action := registerRoute,
        onKeyDown --> { event => if event.keyCode == 13 then event.preventDefault() },
        div(Css.centerColumnFlex, alignItems.flexEnd, Css.rowGap10, marginTop := "40px",
          checkFieldBlock(0, "First name", (s: String) => validNotNullString(s, "First name")),
          checkFieldBlock(1, "Name", (s: String) => validNotNullString(s, "Name")),
          checkFieldBlock(2, "Email", (s: String) => validEmail(s)),
          checkFieldBlock(3, "Institution", (s: String) => validNotNullString(s, "Institution"), inputAttributes = Seq(listId := "institutions")),
          UIUtils.institutionsList,
          passwds._1,
          passwds._2,
          buttonGroup.amend(
            button("Cancel", btn_secondary, onClick --> { _ => displayedForm.set(Form.SignIn()) }),
            button("Sign up", btn_primary,
              cls.toggle("disabled") <-- signupError.signal.map(_.nonEmpty)
            )
          ),
          UIUtils.buildInput("URL").amend(value := document.location.toString, styleAttr := "display:none"),
        )
      )

    lazy val askResetPassword: ReactiveHtmlElement[HTMLFormElement] =
      form(
        method := "POST",
        action := askPasswordResetRoute,
        div(Css.centerColumnFlex, alignItems.flexEnd, Css.rowGap10, marginTop := "120px",
          div(Css.centerRowFlex,
            div(),
            UIUtils.buildInput("Email")
          ),
          buttonGroup.amend(
            button("Cancel", btn_secondary, onClick --> { _ => displayedForm.set(Form.SignIn()) }),
            button("Ok", btn_primary)
          ),
          UIUtils.buildInput("URL").amend(value := document.location.toString, styleAttr := "display:none"),
        )
      )

    def resetPassword(uuid: String, secret: String): ReactiveHtmlElement[HTMLFormElement] =
      val p1 = Var[String]("")
      val p2 = Var[String]("")

      def error =
        (p1.signal combineWith p2.signal).map: (p1, p2) =>
          p1.nonEmpty && p2.nonEmpty && p1 != p2

      val in: Input = UIUtils.buildInput("Password").amend(`type` := "password", onInput.mapToValue --> p1)
      val in2: Input = UIUtils.buildInput("Confirm password").amend(`type` := "password", onInput.mapToValue --> p2)

      form(
        method := "POST",
        action := resetPasswordRoute,
        onKeyDown --> { event => if event.keyCode == 13 then event.preventDefault() },
        div(Css.centerColumnFlex, alignItems.flexEnd, Css.rowGap10, marginTop := "40px",
          in,
          in2,
          buttonGroup.amend(
            button("Cancel", btn_secondary, onClick --> { _ => displayedForm.set(Form.SignIn()) }),
            button("Ok", btn_primary, cls.toggle("disabled") <-- error)
          ),
          child <--
            error.map:
              case true => div("Passwords do not match", cls := "inputError")
              case false => div("", cls := "inputError")
          ,
          UIUtils.buildInput("UUID").amend(value := uuid, styleAttr := "display:none"),
          UIUtils.buildInput("Secret").amend(value := secret, styleAttr := "display:none")
        )
      )

    val renderContent =
      div(
        div(Css.connectionTabOverlay,
          div(
            img(src := "img/logo.png", Css.openmoleLogo),
            div(Css.centerColumnFlex, Css.rowGap10, alignItems.flexEnd,
              child <--
                displayedForm.signal.map:
                  case Form.SignUp => signupForm
                  case Form.AskPasswordReset => askResetPassword
                  case Form.SignIn(m) => signinForm(m)
                  case Form.ResetPassword(uuid, secret) => resetPassword(uuid, secret)
            )
          )
        )
      )

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, renderContent)
