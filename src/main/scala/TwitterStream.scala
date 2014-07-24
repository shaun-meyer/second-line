package project
import twitter4j._
import twitter4j.conf._
import akka.actor._
import scala.util.matching.Regex
import scala.collection.mutable._
import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

case class RawTweet(tweetText: Status)
case class TweetDataResults(emoji: List[String], hashtags: List[String], picture: Boolean, domains: List[String])
case class Inquiry(secondsElapsed: Int)

trait TwitterStreamHandling {
  def emojis(tweet: Status): List[String] = for (i <- """[^\u0000-\uFFFF]""".r.findAllIn(tweet.getText).toList) yield i

  def hashtags(tweet: Status): List[String] = for (i <- ("""(#\w+)($|\s)""".r.findAllIn(tweet.getText).toList)) yield i.replaceAll("""\s""", "").replaceAll("#", "")

  def urls(tweet: Status): Array[twitter4j.URLEntity] = tweet.getURLEntities

  def domains(urls: Array[URLEntity]): List[String] = urls.foldLeft(List[String]())((a, b) => """(.*\/+)""".r.findFirstIn(b.getDisplayURL.toString).getOrElse(b.getDisplayURL.toString) :: a)

  def hasPicture(urls: Array[URLEntity]): Boolean = urls.toList match {
    case Nil => false
    case h :: t => """(pic\.twitter\.com\/.*)""".r.findFirstIn(h.getDisplayURL.toString) match {
      case Some(_) => true
      case None => """(instagram\.com\/p\/)""".r.findFirstIn(h.getDisplayURL.toString) match {
        case Some(_) => true
        case None => hasPicture(t.toArray)
      }
    }
  }
}

class TwitterStreamHandler(storage: ActorRef) extends Actor with TwitterStreamHandling {
  def receive = {
    case RawTweet(tweet) => {
      storage ! TweetDataResults(emojis(tweet), hashtags(tweet), hasPicture(urls(tweet)), domains(urls(tweet)))
    }
  }
}

trait Persistence {
  def addTweet
  def addEmoji(emoji: String): Unit
  def addTweetWithEmojis: Unit
  def addHashTag(tag: String): Unit
  def addTweetWithUrl: Unit
  def addDomains(domains: String): Unit
  def addTweetWithPicture(hasPicture: Boolean): Unit
  def store(startTime: Int): Unit
}

object ConsolePersistence {
  private var emojis: Buffer[String] = Buffer()
  private var hashtags: Buffer[String] = Buffer()
  private var domains: Buffer[String] = Buffer()
  private var numberOfEmojis = 0
  private var numberOfTweets = 0
  private var numberOfPictures = 0
  private var numberOfUrls = 0
}

trait ConsolePersistence extends Persistence {
  def addEmoji(emoji: String) = ConsolePersistence.emojis += emoji
  def addTweetWithEmojis = ConsolePersistence.numberOfEmojis += 1
  def addHashTag(tag: String) = ConsolePersistence.hashtags += tag
  def addTweetWithUrl = ConsolePersistence.numberOfUrls += 1
  def addTweet = ConsolePersistence.numberOfTweets += 1
  def addTweetWithPicture(hasPicture: Boolean) = if (hasPicture) ConsolePersistence.numberOfPictures += 1
  def addDomains(domains: String) = ConsolePersistence.domains += domains
  def store(startTime: Int) = {
    def currentTime = (System.currentTimeMillis.toInt - startTime) / 1000
    println("Number of tweets: " + ConsolePersistence.numberOfTweets)
    println("Average tweets per second: " + ConsolePersistence.numberOfTweets.toFloat / currentTime)
    println("Percentage of tweets with emojis: " + ConsolePersistence.numberOfEmojis.toFloat / ConsolePersistence.numberOfTweets.toFloat * 100)
    println("Percentage of tweets with pictures: " + ConsolePersistence.numberOfPictures.toFloat / ConsolePersistence.numberOfTweets.toFloat * 100)
    println("Percentage of tweets with URLs: " + ConsolePersistence.numberOfUrls.toFloat / ConsolePersistence.numberOfTweets.toFloat * 100)
    println("Top Hashtags: " + ConsolePersistence.hashtags.foldLeft(Map.empty[String, Int].withDefaultValue(0)) {
              case (m, v) => m.updated(v, m(v) + 1)
            }.toList.sortBy(_._2).reverse.slice(0, 6).toString)
    println("Top Domains: " + ConsolePersistence.domains.foldLeft(Map.empty[String, Int].withDefaultValue(0)) {
              case (m, v) => m.updated(v, m(v) + 1)
            }.toList.sortBy(_._2).reverse.slice(0, 6).toString)
    println("Top Emojis: " + ConsolePersistence.emojis.foldLeft(Map.empty[String, Int].withDefaultValue(0)) {
              case (m, v) => m.updated(v, m(v) + 1)
            }.toList.sortBy(_._2).reverse.slice(0, 6).toString)
    println("\n\n")
  }
}

class DataStorage extends Actor with ConsolePersistence {
  def receive = {
    case data@TweetDataResults(emojis, hashtag, hasPicture, domains) => {
      addTweet
      emojis.map(addEmoji(_))
      if (emojis != List()) addTweetWithEmojis
      if (domains != List()) addTweetWithUrl
      addTweetWithPicture(hasPicture)
      hashtag.map(addHashTag(_))
      domains.map(addDomains(_))
    }
    case Inquiry(time) => store(time)
  }
}

object Config {
  lazy val config = new twitter4j.conf.ConfigurationBuilder()
      .setOAuthConsumerKey("v4ohBXufX0XmB8kz9LiFTkt53")
      .setOAuthConsumerSecret("PztzzzG83mKLkCnARz77MEvxNjfO4OrWw8UAMjnmAOOylgtNXS")
      .setOAuthAccessToken("77750943-WzKBeRdcjN5v2zgoKfGitaK0alLmxFm3hscT7FuYY")
      .setOAuthAccessTokenSecret("wSeZ7M0cb59TK7qybsdPTkwwpKmwcUFj7Fd8yZ03YQgVG")
      .build
}

object Driver {
  this will break everything
  def main(args: Array[String]) = {
    lazy val system = ActorSystem("twitter")
    val storage = system.actorOf(Props[DataStorage])
    val stream = system.actorOf(Props(new TwitterStreamHandler(storage)))

    def statusListener = new StatusListener() {
      def onStatus(status: Status) {
        stream ! RawTweet(status)
      }
      def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {}
      def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {}
      def onException(ex: Exception) = ex.printStackTrace()
      def onScrubGeo(x1: Long, x2: Long): Unit = {}
      def onStallWarning(x1: twitter4j.StallWarning): Unit = {}
    }
    val startTime = System.currentTimeMillis
    val twitterStream = new TwitterStreamFactory(Config.config).getInstance()
    twitterStream.addListener(statusListener)
    twitterStream.sample
    system.scheduler.schedule(Duration(5, SECONDS), Duration(5, SECONDS), storage, Inquiry(startTime.toInt))
    println("Starting tweet stream read . . . ")
  }
}
