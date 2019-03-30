package views.html.user.show

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.user.User

import controllers.routes

object claimTitle {

  def apply(u: User)(implicit ctx: Context) =
    div(cls := "claim_title_zone")(
      h2(dataIcon := "C", cls := "text")("Congratulations for breaking the 2400 rating threshold!"),
      p(
        "To ensure honest players aren't falsely accused of cheating, we request titled players ",
        "to identify themselves. For instance, ", a(href := routes.User.show("thunderstorm"))("thunderstorm"), " and ",
        a(href := routes.User.show("simpaticheski"))("simpaticheski"), " are verified IM, and GM. ",
        "You can confirm your title and decide to remain anonymous. We will not reveal your identity."
      ),
      p(
        "To confirm your title, ", a(href := "https://goo.gl/forms/kDsLfBQJNUPtXkfL2")("please fill in this form"), "."
      ),
      p(
        "If you need help or have any question, feel free to contact us by email at ", contactEmailLink, "."
      ),
      p(cls := "actions")(
        a(cls := "gotit button text", href := routes.Pref.saveTag(lidraughts.pref.Pref.Tag.verifyTitle, "1"), dataIcon := "E")("Got it, thanks!"),
        a(cls := "button", href := routes.Pref.saveTag(lidraughts.pref.Pref.Tag.verifyTitle, "0"))("I don't have an official title")
      )
    )
}