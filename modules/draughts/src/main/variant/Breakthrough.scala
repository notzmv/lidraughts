package draughts
package variant

case object Breakthrough extends Variant(
  id = 9,
  gameType = 96,
  key = "breakthrough",
  name = "Breakthrough",
  shortName = "BT",
  title = "The first player who makes a king wins.",
  standardInitialPosition = true,
  boardSize = Board.D100
) {

  def pieces = Standard.pieces
  def initialFen = Standard.initialFen
  def startingPosition = Standard.startingPosition

  def captureDirs = Standard.captureDirs
  def moveDirsColor = Standard.moveDirsColor
  def moveDirsAll = Standard.moveDirsAll

  // Win on promotion
  override def specialEnd(situation: Situation) =
    situation.board.kingPosOf(White).isDefined || situation.board.kingPosOf(Black).isDefined

  override def winner(situation: Situation): Option[Color] =
    if (situation.checkMate) Some(!situation.color)
    else if (situation.board.kingPosOf(White).isDefined) White.some
    else if (situation.board.kingPosOf(Black).isDefined) Black.some
    else None

  def maxDrawingMoves(board: Board): Option[Int] = None

  /**
   * No drawing rules
   */
  def updatePositionHashes(board: Board, move: Move, hash: draughts.PositionHash): PositionHash =
    Hash(Situation(board, !move.piece.color))

}
