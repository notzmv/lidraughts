package draughts
package variant

case object Brazilian extends Variant(
  id = 12,
  gameType = 26,
  key = "brazilian",
  name = "Brazilian",
  shortName = "Brazilian",
  title = "Same rules as International draughts on 8x8 board.",
  standardInitialPosition = false,
  boardSize = Board.D64
) {

  def pieces = Russian.pieces
  def initialFen = Russian.initialFen
  def startingPosition = Russian.startingPosition

  def captureDirs = Standard.captureDirs
  def moveDirsColor = Standard.moveDirsColor
  def moveDirsAll = Standard.moveDirsAll

  override def finalizeBoard(board: Board, uci: format.Uci.Move, captured: Option[List[Piece]], situationBefore: Situation, finalSquare: Boolean): Board = Russian.finalizeBoard(board, uci, captured, situationBefore, finalSquare)

  def maxDrawingMoves(board: Board): Option[Int] = Russian.maxDrawingMoves(board)
  def updatePositionHashes(board: Board, move: Move, hash: draughts.PositionHash): PositionHash = Russian.updatePositionHashes(board, move, hash)

  override def validSide(board: Board, strict: Boolean)(color: Color) = Russian.validSide(board, strict)(color)
}