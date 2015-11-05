package edu.msu.mi.webmd

/**
 * Created by josh on 8/13/15.
 */
package object simulation {

  //messages
  case class ForumUpdated(posts:Iterable[Post])
  case class DoPost(posts:Iterable[Post])

  case class Init(num:Int)
  case object TimeStep
  case object NoPost

  //post class
  case class Post(author:String, thread:Option[Int]=None, position:Option[Int]= None)


}
