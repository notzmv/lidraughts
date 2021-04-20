package views.html.board

import draughts.format.{ FEN, Forsyth }
import play.api.libs.json.Json
import scala.concurrent.duration.Duration

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.game.JsonView.boardSizeWriter
import lidraughts.game.Pov

import controllers.routes

object bits {

  private val dataState = attr("data-state")

  def mini(pov: Pov): Tag => Tag =
    mini(FEN(Forsyth.boardAndColor(pov.game.situation)), pov.game.variant.boardSize, pov.color, ~pov.game.lastMoveKeys) _

  def mini(fen: draughts.format.FEN, boardSize: draughts.Board.BoardSize, color: draughts.Color = draughts.White, lastMove: String = "")(tag: Tag): Tag =
    tag(
      cls := s"mini-board mini-board--init cg-wrap is2d is${boardSize.key}",
      dataState := s"${fen.value}|${boardSize.width}x${boardSize.height}|${color.name}|$lastMove"
    )(cgWrapContent)

  def miniSpan(fen: draughts.format.FEN, boardSize: draughts.Board.BoardSize, color: draughts.Color = draughts.White, lastMove: String = "") =
    mini(fen, boardSize, color, lastMove)(span)

  def jsData(
    sit: draughts.Situation,
    fen: String,
    animationDuration: Duration
  )(implicit ctx: Context) = Json.obj(
    "fen" -> fen.split(":").take(3).mkString(":"),
    "coords" -> ctx.pref.coords,
    "variant" -> variantJson(sit.board.variant),
    "variants" -> draughts.variant.Variant.allVariants.map(variantJson),
    "baseUrl" -> s"$netBaseUrl${routes.Editor.parse("")}",
    "color" -> sit.color.letter.toString,
    "animation" -> Json.obj(
      "duration" -> ctx.pref.animationFactor * animationDuration.toMillis
    ),
    "i18n" -> i18nJsObject(translations)(ctxLang(ctx))
  ).add("puzzleEditor" -> isGranted(_.CreatePuzzles).option(true))
    .add("coordSystem" -> (ctx.pref.coordSystem != lidraughts.pref.Pref.CoordSystem.FIELDNUMBERS).option(ctx.pref.coordSystem))

  def variantJson(v: draughts.variant.Variant)(implicit ctx: Context) = Json.obj(
    "key" -> v.key,
    "name" -> v.name,
    "board" -> v.boardSize,
    "initialFen" -> v.initialFen
  ).add("puzzle" -> lidraughts.pref.Pref.puzzleVariants.contains(v).option(true))

  private val translations = List(
    trans.setTheBoard,
    trans.boardEditor,
    trans.startPosition,
    trans.clearBoard,
    trans.flipBoard,
    trans.loadPosition,
    trans.popularOpenings,
    trans.whitePlays,
    trans.blackPlays,
    trans.variant,
    trans.continueFromHere,
    trans.playWithTheMachine,
    trans.playWithAFriend,
    trans.analysis,
    trans.toStudy
  )
}
