package lidraughts.api

import lidraughts.socket.RemoteSocket._

private final class RemoteSocket(
    api: lidraughts.socket.RemoteSocket,
    channelHandlers: Map[Channel, Handler]
) {

  //   def apply = api.subscribe { (path: Path, args: Args) =>
  //     channelHandlers get channel foreach { handler =>
  //       handler(path, args)
  //     }
  //   }
}
