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
    case SignIn(message: Option[String] = None, error: Boolean = true) extends Form
    case ResetPassword(uuid: String, secret: String) extends Form

  @JSExportTopLevel("connection")
  def connection(error: String) = page(Form.SignIn(Option(error)))

  @JSExportTopLevel("resetPassword")
  def resetPassword(uuid: String, secret: String) = page(Form.ResetPassword(uuid, secret))

  def page(initialForm: Form) =
    val interpreter = STTPInterpreter()
    val displayedForm: Var[Form] = Var(initialForm)

    case class FormField(input: Input, error: Signal[Boolean], html: HtmlElement)

    def passwordBlock =
      val p1 = Var[String]("")
      val p2 = Var[String]("")

      val in = UIUtils.buildInput("Password").amend(`type` := "password", onInput.mapToValue --> p1)
      val in2 = UIUtils.buildInput("Confirm password").amend(`type` := "password", onInput.mapToValue --> p2)

      val errorMessage =
        (p1.signal combineWith p2.signal).map: (p1, p2) =>
          if (p1.isEmpty || p2.isEmpty) || p1 == p2
          then None
          else Some("Passwords are different")

      (
        FormField(in, errorMessage.map(_.isDefined), div(Css.centerRowFlex, div(cls := "inputError", child <-- errorMessage.map(_.getOrElse(""))), in)),
        FormField(in2, Signal.fromValue(false), div(Css.centerRowFlex, div(cls := "inputError", in2)))
      )

    def signInForm(message: Option[String], error: Boolean) =
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
        div(message.getOrElse(""), cls := (if error then "inputError" else "inputMessage"), minWidth := "0", marginTop := "10")
      )

    lazy val signupForm: ReactiveHtmlElement[HTMLFormElement] =
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


      def validEmail(email: String): Option[String] =
        val emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$".r
        if emailRegex.findFirstIn(email).isEmpty
        then Some("Invalid email")
        else None

      def validNotNullString(st: String, field: String): Option[String] =
        if st.isEmpty then Some(s"${field} is mandatory") else None


      val (p1, p2) = passwordBlock
      val firstName = checkFieldBlock("First name", s => validNotNullString(s, "First name"))
      val name = checkFieldBlock("Name", s => validNotNullString(s, "Name"))
      val email = checkFieldBlock("Email", s => validEmail(s))
      val institution = checkFieldBlock("Institution", s => validNotNullString(s, "Institution"), inputAttributes = Seq(listId := "institutions"))
      val urlField = UIUtils.buildInput("URL").amend(value := document.location.toString, styleAttr := "display:none")

      val all = Seq(p1, p1, firstName, name, email, institution)

      form(
        method := "POST",
        div(Css.centerColumnFlex, alignItems.flexEnd, Css.rowGap10, marginTop := "40px",
          firstName.html,
          name.html,
          email.html,
          institution.html,
          UIUtils.institutionsList(interpreter),
          p1.html,
          p2.html,
          buttonGroup.amend(
            button("Cancel", btn_secondary, onClick --> { _ => displayedForm.set(Form.SignIn()) }),
            button("Sign up", btn_primary,
              cls.toggle("disabled") <-- Signal.combineSeq(all.map(_.error)).map(errors => errors.contains(true) || all.exists(_.input.ref.value.isEmpty)),
              onClick.preventDefault -->
                interpreter.openAPIRequest(_.signup)(
                  firstName.input.ref.value,
                  name.input.ref.value,
                  email.input.ref.value,
                  p1.input.ref.value,
                  institution.input.ref.value,
                  urlField.ref.value
                ).foreach:
                  case Some(m) => displayedForm.set(Form.SignIn(Some(m)))
                  case None => displayedForm.set(Form.SignIn(Some("You should now validate your email and then wait for the approval of your account"), false))
            )
          ),
          urlField
        )
      )

    lazy val askResetPassword: ReactiveHtmlElement[HTMLFormElement] =
      val email = UIUtils.buildInput("Email")
      val urlField = UIUtils.buildInput("URL").amend(value := document.location.toString, styleAttr := "display:none")

      form(
        method := "POST",
        div(Css.centerColumnFlex, alignItems.flexEnd, Css.rowGap10, marginTop := "120px",
          div(Css.centerRowFlex,
            div(),
            email
          ),
          buttonGroup.amend(
            button("Cancel", btn_secondary, onClick --> { _ => displayedForm.set(Form.SignIn()) }),
            button("Ok", btn_primary,
              onClick.preventDefault -->
                interpreter.openAPIRequest(_.askResetPassword)(
                  email.ref.value,
                  urlField.ref.value
                ).foreach: m =>
                  displayedForm.set(Form.SignIn(m))
            )
          ),
          urlField
        )
      )

    def resetPassword(uuid: String, secret: String): ReactiveHtmlElement[HTMLFormElement] =
      val (p1, p2) = passwordBlock

      form(
        method := "POST",
        action := resetPasswordRoute,
        div(Css.centerColumnFlex, alignItems.flexEnd, Css.rowGap10, marginTop := "40px",
          p1.html,
          p2.html,
          buttonGroup.amend(
            button("Cancel", btn_secondary, onClick --> { _ => displayedForm.set(Form.SignIn()) }),
            button("Ok", btn_primary, cls.toggle("disabled") <-- p1.error,
              onClick.preventDefault -->
                interpreter.openAPIRequest(_.resetPassword)(
                  p1.input.ref.value,
                  uuid,
                  secret
                ).foreach: m =>
                  displayedForm.set(Form.SignIn(m))
            )
          ),
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
                  case Form.SignIn(m, error) => signInForm(m, error)
                  case Form.ResetPassword(uuid, secret) => resetPassword(uuid, secret)
            )
          )
        )
      )

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, renderContent)
