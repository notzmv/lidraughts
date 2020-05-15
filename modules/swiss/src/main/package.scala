package lidraughts

import lidraughts.socket.WithSocket

package object swiss extends PackageObject with WithSocket {

  private[swiss] type SocketMap = lidraughts.hub.TrouperMap[swiss.SwissSocket]

  private[swiss] val logger = lidraughts.log("swiss")

  private[swiss] type Ranking = Map[lidraughts.user.User.ID, Int]
  private[swiss] type RankingSwap = Map[Int, lidraughts.user.User.ID]
}
