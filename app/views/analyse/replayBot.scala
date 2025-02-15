package views.html.analyse

import bits.dataPanel
import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.game.Pov

object replayBot {

  def apply(
    pov: Pov,
    initialFen: Option[draughts.format.FEN],
    pdn: String,
    simul: Option[lidraughts.simul.Simul],
    cross: Option[lidraughts.game.Crosstable.WithMatchup]
  )(implicit ctx: Context) = {

    import pov._

    views.html.analyse.bits.layout(
      title = replay titleOf pov,
      moreCss = cssTag("analyse.round"),
      openGraph = povOpenGraph(pov).some
    ) {
        main(cls := "analyse")(
          st.aside(cls := "analyse__side")(
            views.html.game.side(pov, initialFen, none, simul = simul, bookmarked = false)
          ),
          div(cls := "analyse__board main-board")(draughtsgroundBoard),
          div(cls := "analyse__tools")(div(cls := "ceval")),
          div(cls := "analyse__controls"),
          div(cls := "analyse__underboard")(
            div(cls := "analyse__underboard__panels")(
              div(cls := "fen-pdn active")(
                div(
                  strong("FEN"),
                  input(readonly, spellcheck := false, cls := "copyable autoselect analyse__underboard__fen")
                ),
                div(cls := "pdn")(pdn)
              ),
              cross.map { c =>
                div(cls := "ctable active")(
                  views.html.game.crosstable(pov.player.userId.fold(c)(c.fromPov), pov.gameId.some)
                )
              }
            )
          )
        )
      }
  }
}
