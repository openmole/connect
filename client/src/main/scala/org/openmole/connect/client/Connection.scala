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
      if (password.isEmpty || password2.isEmpty) || password == password2
      then None
      else Some("Passwords are different")

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
        ),
        button("Sign Up", cls := "linkLike", onClick --> { _ => displayedForm.set(Form.SignUp) }),
        button("Lost Password", cls := "linkLike", onClick --> { _ => displayedForm.set(Form.AskPasswordReset) }),
        div(error.getOrElse(""), cls := "inputError", minWidth := "0", marginTop := "10")
      )

    lazy val signupForm: ReactiveHtmlElement[HTMLFormElement] =
      case class FormField(input: Input, error: Signal[Boolean], html: HtmlElement)

      def checkFieldBlock(field: String, checker: String => Option[String], inputAttributes: Seq[Modifier[HtmlElement]] = Seq()) =
        val error: Var[Option[String]] = Var(None)

        lazy val in: Input =
          UIUtils.buildInput(field).amend(
            onInput --> { _ =>
              error.set(checker(in.ref.value))
            }
          ).amend(inputAttributes)

        val html =
          div(Css.centerRowFlex,
            div(cls := "inputError", child <-- error.signal.map(_.getOrElse(""))),
            in
          )

        FormField(in, error.signal.map(_.isDefined), html)

      def checkPasswordBlock(checker: (String, String) => Option[String]) =
        val errorMsg: Var[Option[String]] = Var(None)
        val in: Input = UIUtils.buildInput("Password").amend(`type` := "password")
        lazy val in2: Input = UIUtils.buildInput("Confirm password").amend(`type` := "password",
          onInput --> { _ =>
            errorMsg.set(checker(in.ref.value, in2.ref.value))
          }
        )

        (
          FormField(in, errorMsg.signal.map(_.isDefined), div(Css.centerRowFlex, div(cls := "inputError", child <-- errorMsg.signal.map(_.getOrElse(""))), in)),
          FormField(in2, Signal.fromValue(false),  div(Css.centerRowFlex, div(cls := "inputError", in2)))
        )


      val (p1, p2) = checkPasswordBlock(validPassword)
      val firstName = checkFieldBlock("First name", s => validNotNullString(s, "First name"))
      val name = checkFieldBlock("Name", s => validNotNullString(s, "Name"))
      val email = checkFieldBlock("Email", s => validEmail(s))
      val institution = checkFieldBlock("Institution", s => validNotNullString(s, "Institution"), inputAttributes = Seq(listId := "institutions"))
      val urlField = UIUtils.buildInput("URL").amend(value := document.location.toString, styleAttr := "display:none")

      val all = Seq(p1, p1, firstName, name, email, institution)

      form(
        method := "POST",
        onKeyDown --> { event => if event.keyCode == 13 then event.preventDefault() },
        div(Css.centerColumnFlex, alignItems.flexEnd, Css.rowGap10, marginTop := "40px",
          firstName.html,
          name.html,
          email.html,
          institution.html,
          UIUtils.institutionsList,
          p1.html,
          p2.html,
          buttonGroup.amend(
            button("Cancel", btn_secondary, onClick --> { _ => displayedForm.set(Form.SignIn()) }),
            button("Sign up", btn_primary,
              cls.toggle("disabled") <-- Signal.combineSeq(all.map(_.error)).map(errors => errors.contains(true) || all.exists(_.input.ref.value.isEmpty)),
              onClick.preventDefault -->
                APIClient.signup(
                  firstName.input.ref.value,
                  name.input.ref.value,
                  email.input.ref.value,
                  p1.input.ref.value,
                  institution.input.ref.value,
                  urlField.ref.value
                ).future.foreach: m =>
                  displayedForm.set(Form.SignIn(m))
            )
          ),
          urlField
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
