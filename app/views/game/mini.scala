package views.html.game

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.game.Pov

import controllers.routes
import draughts.format.Forsyth

object mini {

  private val dataLive = attr("data-live")
  private val dataState = attr("data-state")
  private val dataTime = attr("data-time")

  def apply(
    pov: Pov,
    ownerLink: Boolean = false,
    tv: Boolean = false,
    withTitle: Boolean = true,
    withLink: Boolean = true,
    withLive: Boolean = true
  )(implicit ctx: Context): Tag = {
    val game = pov.game
    val isLive = withLive && game.isBeingPlayed
    val tag = if (withLink) a else span
    val boardSize = game.variant.boardSize
    tag(
      href := withLink.option(gameLink(game, pov.color, ownerLink, tv)),
      title := withTitle.option(gameTitle(game, pov.color)),
      cls := s"mini-game mini-game-${game.id} mini-game--init ${game.variant.key} is2d is${boardSize.key}",
      dataLive := isLive.option(game.id),
      dataBoard := s"${boardSize.width}x${boardSize.height}",
      renderState(pov)
    )(
        renderPlayer(!pov),
        span(cls := "cg-wrap")(cgWrapContent),
        renderPlayer(pov)
      )
  }

  def noCtx(pov: Pov, tv: Boolean = false, blank: Boolean = false): Frag = {
    val game = pov.game
    val isLive = game.isBeingPlayed
    val boardSize = game.variant.boardSize
    a(
      href := (if (tv) routes.Tv.index() else routes.Round.watcher(pov.gameId, pov.color.name)),
      title := gameTitle(pov.game, pov.color),
      cls := s"mini-game mini-game-${game.id} is2d is${boardSize.key} ${isLive ?? "mini-game--live"} ${game.variant.key}",
      dataLive := isLive.option(game.id),
      renderState(pov)
    )(
        renderPlayer(!pov),
        span(cls := "cg-wrap")(cgWrapContent),
        renderPlayer(pov)
      )
  }

  private def renderState(pov: Pov) = {
    val boardSize = pov.game.variant.boardSize
    dataState := s"${Forsyth.exportBoard(pov.game.board)}|${boardSize.width}x${boardSize.height}|${pov.color.name}|${~pov.game.lastMoveKeys}"
  }

  private def renderPlayer(pov: Pov) =
    span(cls := "mini-game__player")(
      span(cls := "mini-game__user")(
        playerUsername(pov.player, withRating = false, withTitle = true),
        span(cls := "rating")(lidraughts.game.Namer ratingString pov.player)
      ),
      pov.game.clock.map { renderClock(_, pov.color) }
    )

  private def renderClock(clock: draughts.Clock, color: draughts.Color) = {
    val s = clock.remainingTime(color).roundSeconds
    span(
      cls := s"mini-game__clock mini-game__clock--${color.name}",
      dataTime := s
    )(
        f"${s / 60}%02d:${s % 60}%02d"
      )
  }
}
