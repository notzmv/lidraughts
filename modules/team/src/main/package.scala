package lidraughts

import lidraughts.socket.WithSocket

package object team extends PackageObject with WithSocket {

  private[team] type SocketMap = lidraughts.hub.TrouperMap[team.TeamSocket]

  private[team] def logger = lidraughts.log("team")
}
