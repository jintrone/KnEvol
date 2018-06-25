package edu.msu.mi.knevol.universe

import akka.actor.{ActorRef, Actor}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable.ListBuffer
import scala.collection.{mutable, BitSet}
import scala.concurrent.Future
import concurrent.duration._
import scala.util.Random

/**
 * Created by josh on 10/1/15.
 */
class StateVar(node:Node) extends Actor {

  implicit val timeout = Timeout(5 seconds)
  import context.dispatcher

  var neighbors:Array[ActorRef] = Array.empty



  override def receive: Receive = {

    case Wire(actors:Seq[ActorRef]) =>
      neighbors = actors.toArray
      if (neighbors.length != node.neighbors.length) {
        println ("Problem on creation!")
      }
      sender ! "OK"


    case SetState(state: Boolean) =>
      node.currentState = Some(state)
      node.calculatedState = None
      sender ! "OK"

    case Advance =>
      //println(s"${this} Node advance")
      node.currentState = Some(node.calculatedState.get)
      sender ! "OK"

    case Update =>
      //println(s"${this} Node update")
      //capture this here
      val s = sender()
      val f = Future.sequence(for (a<-neighbors.toList) yield a? StateRequest )
      f onSuccess   {

        case results =>

          var result = mutable.BitSet()
          for ((bit:Boolean, count) <- results.zipWithIndex if bit) {
            result += count
          }
          node.calculate(BitSet()++result)
          s ! node.calculatedState.get



      }


    case StateRequest =>
      sender ! node.currentState.get
  }





}



