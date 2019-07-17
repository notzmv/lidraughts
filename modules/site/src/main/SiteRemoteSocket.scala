package lidraughts.site

import play.api.libs.json._

import lidraughts.socket.RemoteSocket._

final class SiteRemoteSocket(
    remoteSocketApi: lidraughts.socket.RemoteSocket
) {

  remoteSocketApi.subscribe("site-in")(remoteSocketApi.defaultHandler)
}
