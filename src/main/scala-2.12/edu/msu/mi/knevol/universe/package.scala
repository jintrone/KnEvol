package edu.msu.mi.knevol

import akka.actor.ActorRef

import scala.collection.BitSet
import scala.util.Random


/**
 * Created by josh on 8/13/15.
 */
package object universe {

  //World to state vars
  case class Init(num:Int)

  case class Wire(sources:Iterable[ActorRef])

  case object Update

  case object Advance

  case object StateRequest
  case class StateReponse(state:Boolean)

  case object ProvideLogic


  case class Probe(set:Set[BitSet])

  case class SetState(state: Boolean)

  case object GiveMeYourWorld

  def convert(x: Long): BitSet = {
    var result = collection.mutable.BitSet()
    var index = 0
    var toConvert = x
    while (toConvert != 0L) {
      if (toConvert % 2L != 0) {
        result += index
      }
      index += 1
      toConvert = toConvert >>> 1
    }
    BitSet()++result
  }

  def convert(x: BitSet): Long = {
    x.foldLeft(0) {_+Math.pow(2,_).toInt}
  }


  def createRandomBitset(numBits:Int):BitSet = {
    convert(Random.nextInt(Math.pow(2,numBits).toInt))

  }



}
