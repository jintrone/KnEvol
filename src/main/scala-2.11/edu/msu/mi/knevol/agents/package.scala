package edu.msu.mi.knevol

import scala.collection.{BitSet, mutable}

/**
 * Created by josh on 10/16/15.
 */
package object agents {

  case class Init(brainSize:Int, universeSize:Int, observableSize:Int)
  case object Update
  case object GiveMeYourBrain
  case object WhatAreYourInterests
  case class Learn(round:Int)
  case class AskAbout(state: BitSet)
  case class Go(rounds:Int)



}
