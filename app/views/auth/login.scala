package views.html
package auth

import play.api.data.Form

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object login {

  def twoFactorHelp(implicit ctx: Context) = span(dataIcon := "")(
    trans.tfa.openTheAppOnYourDevice()
  )

  def apply(form: Form[_], referrer: Option[String])(implicit ctx: Context) = views.html.base.layout(
    title = trans.signIn.txt(),
    moreJs = jsTag("login.js"),
    moreCss = cssTag("auth")
  ) {
      main(cls := "auth auth-login box box-pad")(
        h1(trans.signIn()),
        postForm(
          cls := "form3",
          action := s"${routes.Auth.authenticate}${referrer.?? { ref => s"?referrer=${urlencode(ref)}" }}"
        )(
            div(cls := "one-factor")(
              form3.globalError(form),
              auth.bits.formFields(form("username"), form("password"), none, register = false),
              form3.submit(trans.signIn(), icon = none)
            ),
            div(cls := "two-factor none")(
              form3.group(form("token"), trans.tfa.authenticationCode(), help = Some(twoFactorHelp))(
                form3.input(_)(autocomplete := "one-time-code", pattern := "[0-9]{6}")
              ),
              p(cls := "error none")(trans.invalidAuthenticationCode()),
              form3.submit(trans.signIn(), icon = none)
            )
          ),
        div(cls := "alternative")(
          a(href := routes.Auth.signup())(trans.signUp()),
          a(href := routes.Auth.passwordReset())(trans.passwordReset())
        )
      )
    }
}
