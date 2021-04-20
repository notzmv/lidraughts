package views.html.challenge

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.challenge.Challenge.Status

import controllers.routes

object mine {

  def apply(c: lidraughts.challenge.Challenge, json: play.api.libs.json.JsObject, error: Option[String])(implicit ctx: Context) = {

    val cancelForm =
      postForm(action := routes.Challenge.cancel(c.id), cls := "cancel xhr")(
        submitButton(cls := "button button-red text", dataIcon := "L")(trans.cancel())
      )

    views.html.base.layout(
      title = challengeTitle(c),
      openGraph = challengeOpenGraph(c).some,
      moreJs = bits.js(c, json, true),
      moreCss = cssTag("challenge.page")
    ) {
        val challengeLink = s"$netBaseUrl${routes.Round.watcher(c.id, "white")}"
        main(cls := "page-small challenge-page box box-pad")(
          c.status match {
            case Status.Created | Status.External | Status.Offline => div(id := "ping-challenge")(
              h1(trans.challengeToPlay()),
              bits.details(c),
              c.destUserId.map { destId =>
                div(cls := "waiting")(
                  userIdLink(destId.some, cssClass = "target".some),
                  spinner,
                  p(trans.waitingForOpponent())
                )
              } getOrElse div(cls := "invite")(
                div(
                  h2(cls := "ninja-title", trans.toInviteSomeoneToPlayGiveThisUrl(), ": "), br,
                  p(cls := "challenge-id-form")(
                    input(
                      id := "challenge-id",
                      cls := "copyable autoselect",
                      spellcheck := "false",
                      readonly,
                      value := challengeLink,
                      size := challengeLink.size
                    ),
                    button(title := "Copy URL", cls := "copy button", dataRel := "challenge-id", dataIcon := "\"")
                  ),
                  p(trans.theFirstPersonToComeOnThisUrlWillPlayWithYou())
                ),
                ctx.isAuth option div(
                  h2(cls := "ninja-title", "Or invite a lidraughts user:"), br,
                  postForm(cls := "user-invite", action := routes.Challenge.toFriend(c.id))(
                    input(name := "username", cls := "friend-autocomplete", placeholder := trans.search.txt()),
                    error.map { badTag(_) }
                  )
                )
              ),
              c.notableInitialFen.map { fen =>
                frag(
                  br,
                  div(cls := "board-preview", views.html.board.bits.mini(fen, c.variant.boardSize, c.finalColor)(div))
                )
              },
              cancelForm
            )
            case Status.Declined => div(cls := "follow-up")(
              h1(trans.challengeDeclined()),
              bits.details(c),
              a(cls := "button button-fat", href := routes.Lobby.home())(trans.newOpponent())
            )
            case Status.Accepted => div(cls := "follow-up")(
              h1(trans.challengeAccepted()),
              bits.details(c),
              a(id := "challenge-redirect", href := routes.Round.watcher(c.id, "white"), cls := "button-fat")(
                trans.joinTheGame()
              )
            )
            case Status.Canceled => div(cls := "follow-up")(
              h1(trans.challengeCanceled()),
              bits.details(c),
              a(cls := "button button-fat", href := routes.Lobby.home())(trans.newOpponent())
            )
          }
        )
      }
  }
}
