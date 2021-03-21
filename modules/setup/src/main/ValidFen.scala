package lidraughts.setup

import draughts.format.{ FEN, Forsyth }
import draughts.variant.Variant

case class ValidFen(
    fen: FEN,
    situation: draughts.Situation
) {

  def color = situation.color
  def boardSize = situation.board.boardSize
  def tooManyKings = Forsyth.countKings(fen.value) > 30
}

object ValidFen {
  def apply(variant: Variant, strict: Boolean)(fen: String): Option[ValidFen] = for {
    parsed ← Forsyth.<<<@(variant, fen)
    if (parsed.situation playable strict)
    validated = Forsyth >> parsed
  } yield ValidFen(FEN(validated), parsed.situation)
}
