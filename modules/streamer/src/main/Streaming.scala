package lidraughts.streamer

import akka.actor._
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.Play.current
import scala.concurrent.duration._

import lidraughts.db.dsl._
import lidraughts.user.User

private final class Streaming(
    renderer: ActorSelection,
    api: StreamerApi,
    isOnline: User.ID => Boolean,
    timeline: ActorSelection,
    keyword: Stream.Keyword,
    alwaysFeatured: () => lidraughts.common.Strings,
    googleApiKey: String,
    twitchClientId: String,
    lightUserApi: lidraughts.user.LightUserApi
) extends Actor {

  import Stream._
  import Twitch.Reads._
  import YouTube.Reads._
  import BsonHandlers._

  private case object Tick

  private var liveStreams = LiveStreams(Nil)

  def receive = {

    case Streaming.Get => sender ! liveStreams

    case Tick => updateStreams addEffectAnyway scheduleTick
  }

  private def scheduleTick = context.system.scheduler.scheduleOnce(30 seconds, self, Tick)

  self ! Tick

  def updateStreams: Funit = for {
    streamerIds <- api.allListedIds
    activeIds = streamerIds.filter { id =>
      liveStreams.has(id) || isOnline(id.value)
    }
    streamers <- api byIds activeIds
    (twitchStreams, youTubeStreams) <- fetchTwitchStreams(streamers) zip fetchYouTubeStreams(streamers)
    streams = LiveStreams {
      scala.util.Random.shuffle {
        (twitchStreams ::: youTubeStreams) |> dedupStreamers
      }
    }
    _ <- api.setLiveNow(streamers.filter(streams.has).map(_.id))
  } yield publishStreams(streamers, streams)

  def publishStreams(streamers: List[Streamer], newStreams: LiveStreams) = {
    import makeTimeout.short
    import akka.pattern.ask
    if (newStreams != liveStreams) {
      renderer ? newStreams.autoFeatured.withTitles(lightUserApi) foreach {
        case html: String =>
          context.system.lidraughtsBus.publish(lidraughts.hub.actorApi.streamer.StreamsOnAir(html), 'streams)
      }
      newStreams.streams filterNot { s =>
        liveStreams has s.streamer
      } foreach { s =>
        timeline ! {
          import lidraughts.hub.actorApi.timeline.{ Propagate, StreamStart }
          Propagate(StreamStart(s.streamer.userId, s.streamer.name.value)) toFollowersOf s.streamer.userId
        }
        context.system.lidraughtsBus.publish(
          lidraughts.hub.actorApi.streamer.StreamStart(s.streamer.userId),
          'streamStart
        )
      }
    }
    liveStreams = newStreams
    streamers foreach { streamer =>
      streamer.twitch.foreach { t =>
        lidraughts.mon.tv.stream.name(s"${t.userId}@twitch") {
          if (liveStreams.streams.exists(s => s.serviceName == "twitch" && s.is(streamer))) 1 else 0
        }
      }
      streamer.youTube.foreach { t =>
        lidraughts.mon.tv.stream.name(s"${t.channelId}@youtube") {
          if (liveStreams.streams.exists(s => s.serviceName == "youTube" && s.is(streamer))) 1 else 0
        }
      }
    }
  }

  def fetchTwitchStreams(streamers: List[Streamer]): Fu[List[Twitch.Stream]] = {
    val maxIds = 100
    val allTwitchStreamers = streamers.flatMap { s =>
      s.twitch map (s.id -> _)
    }
    val futureTwitchStreamers: Fu[List[Streamer.Twitch]] =
      if (allTwitchStreamers.size > maxIds)
        api.mostRecentlySeenIds(allTwitchStreamers.map(_._1), maxIds) map { ids =>
          allTwitchStreamers collect {
            case (streamerId, twitch) if ids(streamerId) => twitch
          }
        }
      else fuccess(allTwitchStreamers.map(_._2))
    futureTwitchStreamers flatMap { twitchStreamers =>
      twitchStreamers.nonEmpty ?? {
        val twitchUserIds = twitchStreamers.map(_.userId)
        val url = WS.url("https://api.twitch.tv/helix/streams")
          .withQueryString(
            (("first" -> maxIds.toString) :: twitchUserIds.map("user_login" -> _)): _*
          )
          .withHeaders(
            "Client-ID" -> twitchClientId
          )
        if (twitchUserIds.size > 1) logger.info(url.uri.toString)
        url.get().flatMap { res =>
          res.json.validate[Twitch.Result](twitchResultReads) match {
            case JsSuccess(data, _) =>
              fuccess(
                data.streams(
                  keyword,
                  streamers,
                  alwaysFeatured().value.map(_.toLowerCase)
                )
              )
            case JsError(err) =>
              fufail(s"twitch ${res.status} $err ${~res.body.lines.toList.headOption}")
          }
        }.recover {
          case e: Exception =>
            logger.warn(e.getMessage)
            Nil
        }
      }
    }
  }

  private var prevYouTubeStreams = YouTube.StreamsFetched(Nil, DateTime.now)

  def fetchYouTubeStreams(streamers: List[Streamer]): Fu[List[YouTube.Stream]] = {
    val youtubeStreamers = streamers.filter(_.youTube.isDefined)
    (youtubeStreamers.nonEmpty && googleApiKey.nonEmpty) ?? {
      val now = DateTime.now
      val res =
        if (prevYouTubeStreams.list.isEmpty && prevYouTubeStreams.at.isAfter(now minusMinutes 2))
          fuccess(prevYouTubeStreams)
        else {
          WS.url("https://www.googleapis.com/youtube/v3/search").withQueryString(
            "part" -> "snippet",
            "type" -> "video",
            "eventType" -> "live",
            "q" -> keyword.value,
            "key" -> googleApiKey
          ).get().flatMap { res =>
              res.json.validate[YouTube.Result](youtubeResultReads) match {
                case JsSuccess(data, _) =>
                  fuccess(YouTube.StreamsFetched(data.streams(keyword, youtubeStreamers), now))
                case JsError(err) =>
                  fufail(s"youtube ${res.status} $err ${res.body.take(500)}")
              }
            }.recover {
              case e: Exception =>
                logger.warn(e.getMessage)
                YouTube.StreamsFetched(Nil, now)
            }
        }
      res dmap { r =>
        prevYouTubeStreams = r
        r.list
      }
    }
  }

  def dedupStreamers(streams: List[Stream]): List[Stream] = streams.foldLeft((Set.empty[Streamer.Id], List.empty[Stream])) {
    case ((streamerIds, streams), stream) if streamerIds(stream.streamer.id) => (streamerIds, streams)
    case ((streamerIds, streams), stream) => (streamerIds + stream.streamer.id, stream :: streams)
  }._2
}

object Streaming {

  case object Get
}
