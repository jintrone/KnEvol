package edu.msu.mi.webmd.simulation

import java.util

import akka.actor.{Props, Actor}
import akka.actor.Actor.Receive
import akka.pattern.ask

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
 * Created by josh on 8/13/15.
 */
class Forum(maxSteps:Int = 100) extends Actor {

  val forumData = scala.collection.mutable.Map[Int, ArrayBuffer[Post]]()
  val tmpMap = scala.collection.mutable.Map[Int, ArrayBuffer[Post]]()
  var step = 0
  var responses = 0


  override def receive: Receive = {


    case Init(count) => {
      for (i <- 0 to count-1) {
        //does this make sense?  see akka docs: http://doc.akka.io/docs/akka/2.3.12/scala/actors.html
        val name = s"Poster_$i"
        context.actorOf(Forum.createActor(name,Random.nextFloat()), name)
      }
      updatePosters
      checkResponses

    }

    case DoPost(posts: Seq[Post]) => {

      responses += 1
      posts.foreach {p =>

        val (threadid, pos) = p.thread match {
          case Some(idx) =>
            if (!(tmpMap contains idx)) {
              tmpMap += (idx -> ArrayBuffer[Post]())
            }
            (idx, forumData(idx).size)

          case None =>
            val tmp = forumData.size
            forumData += (tmp -> ArrayBuffer[Post]())
            tmpMap += (tmp -> ArrayBuffer[Post]())
            (tmp, 0)
        }
        tmpMap(threadid) += Post(p.author, Some[Int](threadid), Some[Int](pos))

      }
      checkResponses

    }

    case NoPost => {
      responses += 1
      checkResponses

    }


  }

  def checkResponses: Unit = {
    if (responses == context.children.size) {
      println("Recevied responses from all children")
      updatePosters
    }
  }

  def integrate: Unit = {
    tmpMap.foreach {
      case (threadid, posts) => {
        if (forumData contains threadid) {
          println(s"Adding ${posts.size} to ${forumData(threadid).size}")
          forumData(threadid) ++= posts
          println(s"Now have ${forumData(threadid).size}")
        } else {
          forumData += (threadid -> posts)
        }
      }

    }
  }


  def updatePosters: Unit = {
    responses = 0
    step+=1
    println(s"Step $step")


    val postsToSend = tmpMap.values.flatten
    integrate
    tmpMap.clear()
    val m = ForumUpdated(postsToSend)
    context.children.foreach { child =>
      child ! m
    }
    if (step == maxSteps) {
      inspectForum
      context.system.terminate()
    }


  }

  def inspectForum: Unit= {
    println (s"Got ${forumData.size} threads ")
    println (s"Data is ${forumData}")

  }

}

object Forum {

  def createActor(name:String,capacity: Float): Props = Props(new ForumPoster(name,capacity))


}
