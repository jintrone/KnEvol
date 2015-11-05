package edu.msu.mi.webmd.simulation

import akka.actor.{ActorRef, Actor}

import scala.util.Random

/**
 * Created by josh on 8/13/15.
 */
class ForumPoster(name:String, capacity:Float) extends Actor {


  /**
   * Elements - how often do people need to ask for information?  What is included in top posts?
   *
   *
   * Theory - information & emotional support needs are by and large, acute.  They occur with some frequency,
   * only sometimes rising to the level where it exceeds the cost of actually posting
   *
   * Companionship - in the form of friendship - is different, in that people get out what they invest.  There
   * are only a handful of people who can actually invest the time, but in being there, they become motivated to
   * address the needs of others.
   *
   *
   *
   * @return
   */

  override def receive: Receive = {
    case ForumUpdated(posts:Seq[Post]) =>
      decideAndPost(posts,sender())
  }

  def decideAndPost(posts:Seq[Post],sender:ActorRef) = {
     if (Random.nextFloat() <= capacity) {

        if (posts.size<2 || Random.nextFloat()<.3) {

          sender ! DoPost(List(Post(name)))
        } else {
          sender ! DoPost(List(Post(name,posts(Random.nextInt(posts.size)).thread)))
        }
     } else {
       sender ! NoPost
     }
  }



}
