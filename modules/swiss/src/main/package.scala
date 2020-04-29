package lidraughts

import lidraughts.socket.WithSocket

package object swiss extends PackageObject with WithSocket {

  private[swiss] type SocketMap = lidraughts.hub.TrouperMap[swiss.SwissSocket]

  private[swiss] val logger = lidraughts.log("swiss")
}
