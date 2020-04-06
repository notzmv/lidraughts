package views.html.team

import play.api.data.Form

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object admin {

  import trans.team._

  def changeOwner(t: lidraughts.team.Team, userIds: Iterable[lidraughts.user.User.ID])(implicit ctx: Context) = {

    val title = s"${t.name} - ${appointOwner.txt()}"

    bits.layout(title = title) {
      main(cls := "page-menu page-small")(
        bits.menu(none),
        div(cls := "page-menu__content box box-pad")(
          h1(title),
          p(trans.team.changeOwner()),
          br,
          br,
          postForm(cls := "kick", action := routes.Team.changeOwner(t.id))(
            userIds.toList.sorted.map { userId =>
              button(name := "userId", cls := "button button-empty button-no-upper confirm", value := userId)(
                usernameOrId(userId)
              )
            }
          )
        )
      )
    }
  }

  def kick(t: lidraughts.team.Team, userIds: Iterable[lidraughts.user.User.ID])(implicit ctx: Context) = {

    val title = s"${t.name} - ${kickSomeone.txt()}"

    bits.layout(title = title) {
      main(cls := "page-menu page-small")(
        bits.menu(none),
        div(cls := "page-menu__content box box-pad")(
          h1(title),
          p(whoToKick()),
          br,
          br,
          postForm(cls := "kick", action := routes.Team.kick(t.id))(
            userIds.toList.sorted.map { userId =>
              button(name := "userId", cls := "button button-empty button-no-upper confirm", value := userId)(
                usernameOrId(userId)
              )
            }
          )
        )
      )
    }
  }

  def pmAll(
    t: lidraughts.team.Team,
    form: Form[_],
    tours: List[lidraughts.tournament.Tournament]
  )(implicit ctx: Context) = {

    val title = s"${t.name} - ${messageAllMembers.txt()}"

    views.html.base.layout(
      title = title,
      moreCss = cssTag("team"),
      moreJs = embedJsUnsafe("""
           |$('.copy-url-button').on('click', function(e) {
           |$('#form3-message').val(function(i, x) {return x + $(e.target).data('copyurl') + '\n'})
           |})
           |""".stripMargin),
    ) {
        main(cls := "page-menu page-small")(
          bits.menu(none),
          div(cls := "page-menu__content box box-pad")(
            h1(title),
            p(messageAllMembersLongDescription()),
            tours.nonEmpty option div(cls := "tournaments")(
              p(youMayWantToLinkOneOfTheseTournaments()),
              p(
                ul(
                  tours.map { t =>
                    li(
                      tournamentLink(t),
                      " ",
                      momentFromNow(t.startsAt),
                      " ",
                      a(
                        dataIcon := "z",
                        cls := "text copy-url-button",
                        data.copyurl := s"${netDomain}${routes.Tournament.show(t.id).url}",
                      )
                    )
                  }
                )
              ),
              br
            ),
            postForm(cls := "form3", action := routes.Team.pmAllSubmit(t.id))(
              form3.group(form("message"), trans.message())(form3.textarea(_)(rows := 10)),
              form3.actions(
                a(href := routes.Team.show(t.slug))(trans.cancel()),
                form3.submit(trans.send())
              )
            )
          )
        )
      }
  }
}
