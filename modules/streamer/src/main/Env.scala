package lidraughts.streamer

import akka.actor._
import com.typesafe.config.Config

import lidraughts.common.Strings

final class Env(
    config: Config,
    system: ActorSystem,
    settingStore: lidraughts.memo.SettingStore.Builder,
    renderer: ActorSelection,
    isOnline: lidraughts.user.User.ID => Boolean,
    asyncCache: lidraughts.memo.AsyncCache.Builder,
    notifyApi: lidraughts.notify.NotifyApi,
    lightUserApi: lidraughts.user.LightUserApi,
    hub: lidraughts.hub.Env,
    db: lidraughts.db.Env
) {

  private val CollectionStreamer = config getString "collection.streamer"
  private val CollectionImage = config getString "collection.image"
  private val MaxPerPage = config getInt "paginator.max_per_page"
  private val Keyword = config getString "streaming.keyword"
  private val GoogleApiKey = config getString "streaming.google.api_key"

  private lazy val streamerColl = db(CollectionStreamer)
  private lazy val imageColl = db(CollectionImage)

  private lazy val photographer = new lidraughts.db.Photographer(imageColl, "streamer")

  lazy val alwaysFeaturedSetting = {
    import lidraughts.memo.SettingStore.Strings._
    settingStore[Strings](
      "streamerAlwaysFeatured",
      default = Strings(Nil),
      text = "Twitch streamers who get featured without the keyword - lidraughts usernames separated by a comma".some
    )
  }

  lazy val twitchCredentialsSetting = settingStore[String](
    "twitchCredentials",
    default = "",
    text = "Twitch API client ID and secret, separated by a space".some
  )

  lazy val api = new StreamerApi(
    coll = streamerColl,
    asyncCache = asyncCache,
    photographer = photographer,
    notifyApi = notifyApi
  )

  lazy val pager = new StreamerPager(
    coll = streamerColl,
    maxPerPage = lidraughts.common.MaxPerPage(MaxPerPage)
  )

  private val streamingActor = system.actorOf(Props(new Streaming(
    renderer = renderer,
    api = api,
    isOnline = isOnline,
    timeline = hub.timeline,
    keyword = Stream.Keyword(Keyword),
    alwaysFeatured = alwaysFeaturedSetting.get,
    googleApiKey = GoogleApiKey,
    twitchCredentials = () =>
      twitchCredentialsSetting.get().split(' ') match {
        case Array(client, secret) => (client, secret)
        case _ => ("", "")
      },
    lightUserApi = lightUserApi
  )))

  lazy val liveStreamApi = new LiveStreamApi(asyncCache, streamingActor)

  system.lidraughtsBus.subscribeFun('userActive, 'adjustCheater) {
    case lidraughts.user.User.Active(user) if !user.seenRecently => api setSeenAt user
    case lidraughts.hub.actorApi.mod.MarkCheater(userId, true) => api demote userId
  }
}

object Env {

  lazy val current: Env = "streamer" boot new Env(
    config = lidraughts.common.PlayApp loadConfig "streamer",
    system = lidraughts.common.PlayApp.system,
    settingStore = lidraughts.memo.Env.current.settingStore,
    renderer = lidraughts.hub.Env.current.renderer,
    isOnline = lidraughts.user.Env.current.isOnline,
    asyncCache = lidraughts.memo.Env.current.asyncCache,
    notifyApi = lidraughts.notify.Env.current.api,
    lightUserApi = lidraughts.user.Env.current.lightUserApi,
    hub = lidraughts.hub.Env.current,
    db = lidraughts.db.Env.current
  )
}
