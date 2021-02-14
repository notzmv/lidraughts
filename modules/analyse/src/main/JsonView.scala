package lidraughts.analyse

import play.api.libs.json._

import lidraughts.game.{ Game, Pov }
import lidraughts.tree.Eval.JsonHandlers._

object JsonView {

  def moves(analysis: Analysis, withGlyph: Boolean = true) = JsArray(analysis.infoAdvices map {
    case ((info, adviceOption)) => Json.obj()
      .add("eval" -> info.cp)
      .add("win" -> info.win)
      .add("best" -> info.best.map(_.uci))
      .add("variation" -> info.variation.nonEmpty.option(info.variation mkString " "))
      .add("judgment" -> adviceOption.map { a =>
        Json.obj(
          "name" -> a.judgment.name,
          "comment" -> a.makeComment(false, true)
        ).add("glyph" -> withGlyph.option(Json.obj(
            "name" -> a.judgment.glyph.name,
            "symbol" -> a.judgment.glyph.symbol
          )))
      })
  })

  import Accuracy.povToPovLike

  def player(pov: Accuracy.PovLike, game: Option[Game] = None, withModStats: Boolean = false)(analysis: Analysis) =
    analysis.summary.find(_._1 == pov.color).map(_._2).map { s =>
      val moveStats = withModStats.??(analysis.notBestPlies.map(_.filter(draughts.Color.fromPly(_) == pov.color)))
      def timeStats(g: Game) = withModStats ?? {
        import Statistics._
        val mt = ~g.moveTimes(pov.color) map (_.roundTenths)
        val mtAvg = listAverage(mt).toInt
        val mtSd = listDeviation(mt).toInt
        val p = Pov(g, pov.color)
        val cmt = List(
          highlyConsistentMoveTimes(p) ?? "high cons.",
          highlyConsistentMoveTimeStreaks(p) ?? "streak",
          moderatelyConsistentMoveTimes(p) ?? "mod. cons.",
          noFastMoves(p) ?? "no fast moves"
        ).filter(_.nonEmpty)
        Json.obj(
          "avg" -> mtAvg,
          "sd" -> mtSd
        ).add("cmt", cmt.nonEmpty.option(cmt.mkString(", "))).some
      }
      JsObject(s map {
        case (nag, nb) => nag.toString.toLowerCase -> JsNumber(nb)
      }).add("acpl" -> lidraughts.analyse.Accuracy.mean(pov, analysis))
        .add("nbm" -> moveStats)
        .add("mt" -> game.flatMap(timeStats))
    }

  def bothPlayers(game: Game, analysis: Analysis, withModStats: Boolean = false) = Json.obj(
    "id" -> analysis.id,
    "white" -> player(game.whitePov, game.some, withModStats)(analysis),
    "black" -> player(game.blackPov, game.some, withModStats)(analysis)
  )

  def bothPlayers(pov: Accuracy.PovLike, analysis: Analysis) = Json.obj(
    "id" -> analysis.id,
    "white" -> player(pov.copy(color = draughts.White))(analysis),
    "black" -> player(pov.copy(color = draughts.Black))(analysis)
  )

  def mobile(game: Game, analysis: Analysis) = Json.obj(
    "summary" -> bothPlayers(game, analysis),
    "moves" -> moves(analysis)
  )
}
