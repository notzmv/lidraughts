package views.html
package account

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object passwd {

  def apply(form: play.api.data.Form[_])(implicit ctx: Context) = account.layout(
    title = trans.changePassword.txt(),
    active = "password"
  ) {
      div(cls := "account box box-pad")(
        h1(
          trans.changePassword(),
          ctx.req.queryString.contains("ok") option
            frag(" ", i(cls := "is-green", dataIcon := "E"))
        ),
        postForm(cls := "form3", action := routes.Account.passwdApply)(
          form3.passwordModified(form("oldPasswd"), trans.currentPassword())(autofocus, autocomplete := "current-password"),
          form3.passwordModified(form("newPasswd1"), trans.newPassword())(autocomplete := "new-password"),
          form3.passwordModified(form("newPasswd2"), trans.newPasswordAgain())(autocomplete := "new-password"),
          form3.action(form3.submit(trans.apply()))
        )
      )
    }
}
