package lidraughts.quote

import play.api.libs.json._
import scala.util.Random

final class Quote(val text: String, val author: String)

object Quote {

  def one = all(Random.nextInt(all.size))

  def one(seed: String) = all(new Random(seed.hashCode).nextInt(all.size))

  val all = Vector(
    new Quote("Chess is about what you see, draughts about what you know. Both are enough to fill a lifetime.", "Harry Pillsbury"), //http://wcdraughts.com/wp-content/uploads/2015/11/FD-1107-Zou-God-dammen.pdf
    new Quote("Draughts is too flat for me.", "Hein Donner"), //https://www.schaakstad-apeldoorn.nl/blog/2017/11/19/denksport/
    new Quote("A draughtsplayer uses his head, not his mouth. If he goes to the baker he just says: 'One bread.'", "Jannes van der Wal"), //https://www.trouw.nl/home/het-spannende-van-dammen~ae2a161a/
    new Quote("It depends on who is your opponent. You could say that chess is easier. You just need to conquer the enemy king. With draughts you have to capture all the pieces.", "Jannes van der Wal"),
    new Quote("A person is born, and a person dies. In between there is the possibility to play draughts.", "Jannes van der Wal"),
    new Quote("Chess- and draughtsplayers are gentle beings.", "Ton Sijbrands"), //https://www.nrc.nl/nieuws/2009/02/10/sijbrands-was-graag-goede-gitarist-geweest-11681641-a191406
    new Quote("Draughts grabbed hold of me when I was eleven and grew into an insane passion. I'm thinking about it day and night.", "Ton Sijbrands"), //https://www.psychologiemagazine.nl/artikel/ton-sijbrands-na-een-zware-wedstrijd-ben-ik-twee-weken-gevloerd/
    new Quote("The beauty of draughts is that you're never finished analysing.", "Ton Sijbrands"), //https://www.psychologiemagazine.nl/artikel/ton-sijbrands-na-een-zware-wedstrijd-ben-ik-twee-weken-gevloerd/
    new Quote("Time is a child playing a game of draughts; the kingship is in the hands of a child.", "Heraclitus"),
    new Quote("I am never proud of anything. That is what I am most proud of.", "Alexander Georgiev"),
    new Quote("The most important element for success is to love what you do and do what you love. ", "Alexander Georgiev"),
    new Quote("I studied economics in Saint Petersburg, but when I became European champion in 1995, I decided to become a professional draughts player. That seemed to be more exciting than a boring life as a bookkeeper.", "Alexander Georgiev"),
    new Quote("Playing a simultaneous exhibition gives me lots of pleasure. I like people liking, that's for me the best!", "Alexander Schwartzman"),
    new Quote("Draughts players complain too much, they shouldn't compare themselves to chess players. We should propagate that we practice the most beautiful game. The endless spaces make draughts fascinating. Its magic lies in the limitless possibilities.", "Roel Boomstra"),
    new Quote("At the risk that I will hit on the nerve of many chess players I say that chess is more difficult to learn than draughts, but draughts is actually much harder than chess.", "V. Cornetz"),
    new Quote("I will, therefore, take occasion to assert that the higher powers of the reflective intellect are more decidedly and more usefully tasked by the unostentatious game of draughts than by the elaborate frivolity of chess.", "Edgar Allan Poe"),
    new Quote("Of all mind sports, draughts is the most beautiful.", "Pierre Ghestem"),
    new Quote("Baba Sy had opened our eyes. Africa was not only good in dancing and football, but also in mindgames, Baba Sy became the ambassador of the continent. In Senegal everyone speaks about him with great respect.", "Macodou N'Diaye"),
    new Quote("Let's play draughts!", "Klaas Leijenaar"),

    // lidraughts facts
    new Quote("All features for free; for everyone; forever.", "lidraughts.org"),
    new Quote("We will never display ads.", "lidraughts.org"),
    new Quote("We do not track you. It's a rare feature, nowadays.", "lidraughts.org"),
    new Quote("Everyone is a premium user.", "lidraughts.org")
  )

  implicit def quoteWriter: OWrites[Quote] = OWrites { q =>
    Json.obj(
      "text" -> q.text,
      "author" -> q.author
    )
  }
}
