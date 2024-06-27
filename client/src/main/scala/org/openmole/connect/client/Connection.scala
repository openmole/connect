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


  //lazy val cookieButton = tags.button("Cookuie", btn_default, onclick := { () => println("COOKIES: " + dom.document.cookie) }).render


  @JSExportTopLevel("connection")
  def connection(connectionError: Boolean) =

    val displaySignupForm: Var[Boolean] = Var(false)
    val signupError: Var[Seq[Int]] = Var(0 to 4)

      lazy val connectButton = button("Connect", btn_primary, `type` := "submit", float.right, right := "0")

    lazy val connectionForm =
      form(
        Css.centerColumnFlex, alignItems.flexEnd, Css.rowGap10,
        method := "POST",
        action := connectionRoute,
        UIUtils.buildInput("Email"),
        UIUtils.buildInput("Password").amend(`type` := "password"),
        connectButton
        //onMountBind(ctx => ctx.thisNode.ref.elements.namedItem("URL"). := "test")
      )

    // def errorMsgObserver(errorMsg: Var[Option[String]]) =


    def checkFieldBlock(id: Int, field: String, checker: String => Option[String]) =
      val errorMsg: Var[Option[String]] = Var(None)
      lazy val in: Input = UIUtils.buildInput(field).amend(
        onInput --> { _ =>
          errorMsg.set {
            val em = checker(in.ref.value)
            signupError.update { se =>
              (em match
                case Some(_) => se :+ id
                case _ => se.filterNot(_ == id)
                ).distinct
            }
            em
          }
        })
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

    def validEmail(email: String): Option[String] =
      if ("""^[-a-z0-9!#$%&'*+/=?^_`{|}~]+(\.[-a-z0-9!#$%&'*+/=?^_`{|}~]+)*@([a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?\.)*(aero|arpa|asia|biz|cat|com|coop|edu|gov|info|int|jobs|mil|mobi|museum|name|net|org|pro|tel|travel|[a-z][a-z])$""".r.findFirstIn(email) == None)
        Some("Invalid email")
      else None

    def validPassword(password: String, password2: String) =
      if (password == password2 && !password.isEmpty) None
      else Some("Passwords are different")

    def validNotNullString(st: String, field: String): Option[String] =
      if (st.isEmpty) Some(s"${field} is mandatory")
      else None

    lazy val signupForm: ReactiveHtmlElement[HTMLFormElement] =
      val passwds = checkPasswordBlock((pwd: String, pwd2: String) => validPassword(pwd, pwd2))
      form(
        method := "POST",
        action := registerRoute,
        onKeyDown --> {event=> if(event.keyCode == 13) event.preventDefault()},
        div(Css.centerColumnFlex, alignItems.flexEnd, Css.rowGap10, marginTop := "40px",
          checkFieldBlock(0, "First name", (s: String) => validNotNullString(s, "First name")),
          checkFieldBlock(1, "Name", (s: String) => validNotNullString(s, "Name")),
          checkFieldBlock(2, "Email", (s: String) => validEmail(s)),
          checkFieldBlock(3, "Institution", (s: String) => validNotNullString(s, "Institution")),
          passwds._1,
          passwds._2,
          buttonGroup.amend(
            button("Cancel", btn_secondary, onClick --> { _ => displaySignupForm.set(false) }),
            button("Sign up", btn_primary,
              cls.toggle("disabled") <-- signupError.signal.map(se => !se.isEmpty)
            )
          ),
          UIUtils.buildInput("URL").amend(value := document.location.toString, styleAttr := "display:none"),
        )
      )

    val renderContent =
      div(
        div(Css.connectionTabOverlay,
          div(
            img(src := "img/logo.png", Css.openmoleLogo),
            div(Css.centerColumnFlex, Css.rowGap10, alignItems.flexEnd,
              children <-- displaySignupForm.signal.map { su =>
                if (su) Seq(signupForm)
                else
                  Seq(
                    div(marginTop := "120", Css.centerColumnFlex, alignItems.flexEnd,
                      connectionForm,
                      button("Sign up", cls := "linkLike", onClick --> { _ => displaySignupForm.set(true) }),
                      div(if (connectionError) "Incorrect email or password" else "", cls := "inputError")
                    )
                  )
              })
          )
        )
      )

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, renderContent)
