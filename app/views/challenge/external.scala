package views.html.challenge

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.challenge.Challenge.Status

import controllers.routes

object external {

  def apply(
    c: lidraughts.challenge.Challenge,
    json: play.api.libs.json.JsObject,
    player: Boolean
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = challengeTitle(c),
      openGraph = challengeOpenGraph(c).some,
      moreJs = bits.js(c, json, false),
      moreCss = cssTag("challenge.page")
    ) {
        val startsAt = c.external.flatMap(_.startsAt).filter(_.isAfterNow)
        main(cls := "page-small challenge-page challenge-external box box-pad")(
          c.status match {
            case Status.Created | Status.External | Status.Offline =>
              frag(
                h1(
                  userIdLink(c.finalColor.fold(c.challengerUserId, c.destUserId)),
                  " vs ",
                  userIdLink(c.finalColor.fold(c.destUserId, c.challengerUserId))
                ),
                bits.details(c),
                c.notableInitialFen.map { fen =>
                  div(cls := "board-preview", views.html.game.bits.miniBoard(fen, color = !c.finalColor, boardSize = c.variant.boardSize))
                },
                if (startsAt.isDefined) startsAt.map { dt =>
                  div(cls := "starts-at")(
                    trans.startsAtX(absClientDateTime(dt)),
                    ul(cls := "countdown")(
                      List("Days", "Hours", "Minutes", "Seconds") map { t =>
                        li(cls := t.toLowerCase)(span, t)
                      }
                    )
                  )
                }
                else if (player) {
                  if (!c.mode.rated || ctx.isAuth) frag(
                    (c.mode.rated && c.unlimited) option
                      badTag(trans.bewareTheGameIsRatedButHasNoClock()),
                    if (c.hasAcceptedExternal(ctx.me)) frag(
                      p(cls := "player-accepted")(trans.challengeAcceptedAndWaiting()),
                      p(cls := "accepting-message")(trans.youWillBeRedirectedToTheGame())
                    )
                    else postForm(cls := "accept", action := routes.Challenge.accept(c.id))(
                      submitButton(cls := "text button button-fat", dataIcon := "G")(trans.joinTheGame())
                    )
                  )
                  else frag(
                    hr,
                    badTag(
                      p("This game is rated"),
                      p(
                        "You must ",
                        a(cls := "button", href := s"${routes.Auth.login}?referrer=${routes.Round.watcher(c.id, "white")}")(trans.signIn()),
                        " to join it."
                      )
                    )
                  )
                } else c.external map { e =>
                  val accepted = div(cls := "status")(span(dataIcon := "E"), trans.challengeAccepted())
                  val waiting = div(cls := "status")(trans.waitingForPlayer())
                  frag(
                    div(cls := "accepting")(
                      div(cls := "players")(
                        div(
                          div(cls := "player color-icon is white")(userIdLink(c.finalColor.fold(c.challengerUserId, c.destUserId), withOnline = false)),
                          if (c.finalColor.fold(e.challengerAccepted, e.destUserAccepted)) accepted
                          else waiting
                        ),
                        div(
                          div(cls := "player color-icon is black")(userIdLink(c.finalColor.fold(c.destUserId, c.challengerUserId), withOnline = false)),
                          if (c.finalColor.fold(e.destUserAccepted, e.challengerAccepted)) accepted
                          else waiting
                        )
                      )
                    ),
                    p(cls := "accepting-message")(
                      trans.youWillBeRedirectedToTheGame()
                    )
                  )
                }
              )
            case Status.Declined => div(cls := "follow-up")(
              h1(trans.challengeDeclined()),
              bits.details(c)
            )
            case Status.Accepted => div(cls := "follow-up")(
              h1(trans.challengeAccepted()),
              bits.details(c),
              a(id := "challenge-redirect", href := routes.Round.watcher(c.id, "white"), cls := "button button-fat")(
                if (player) trans.joinTheGame()
                else trans.watch()
              )
            )
            case Status.Canceled => div(cls := "follow-up")(
              h1(trans.challengeCanceled()),
              bits.details(c)
            )
          }
        )
      }
}
