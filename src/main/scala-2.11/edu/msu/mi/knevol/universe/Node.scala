package edu.msu.mi.knevol.universe

import akka.actor.ActorRef

import scala.collection._
import scala.util.Random

/**
 * Created by josh on 10/20/15.
 */
class Node(val ordinal:Int, universe:Int,var neighbors:mutable.Buffer[Int], var function:mutable.Map[BitSet,Boolean]) {


  def this(other:Node,universe:Int)  {
    this(other.ordinal,universe,other.neighbors.clone(),for ((k:BitSet,v:Boolean)<-other.function) yield (BitSet()++k,v))
  }

  def this(ordinal:Int, universe:Int, degree:Int) {
    this(ordinal,universe,((for (x:Int<-Random.shuffle(0 until universe toList) if x != ordinal) yield x) take degree).to[mutable.Buffer], collection.mutable.Map[BitSet, Boolean]())
  }

  var currentState: Option[Boolean] = None
  var calculatedState: Option[Boolean] = None

  def setState(b: Boolean): Unit = currentState = Option(b)

  def calculate(input:BitSet):Unit={
    if (!function.contains(input)) {
      println(s"$this: not finding $input not in ${function.keySet}")
    }
    calculatedState = Some(function(input))
  }

  def init():Unit= {
    val rand = new Random()
    //println(s"Init with ${neighbors.length} neighbors")
    for (i <- 0 until Math.pow(2, neighbors.length).toInt) {
      function += (convert(i) -> rand.nextBoolean())
    }
  }

  def mutateEverything(rewireProb:Double, flipProb:Double):Unit = {
    rewire(rewireProb)
    flip(flipProb)
  }

  def flip(prob:Double):Unit= {
    function = for ((k,v)<-function) yield (k,if (Random.nextDouble()<prob) !v else v)
  }

  def rewire(prob:Double):Unit= {
    if (Random.nextDouble()<prob) {
      if ((Random.nextBoolean() || neighbors.isEmpty) && neighbors.length < universe-1) {

        val list:Array[Int] =((0 until universe).toBuffer -- (neighbors:+ordinal)).toArray[Int]
        list(Random.nextInt(list.length))+=:neighbors
        function = for ((k,v)<-function) yield (k.map(_+1)(BitSet.canBuildFrom),v)
        function ++= (for ((k,v)<-function) yield (k+0,v))
      } else {

        val toremove:Int = Random.nextInt(neighbors.size)
        neighbors.remove(toremove)
        //we're going to do some damage to the function here, so just
        //remove the column from the truth table and damn the consequences!
        function = for ((k,v)<-function) yield ((for (i<-k) yield if (i > toremove) i - 1 else i)(BitSet.canBuildFrom),v)
      }
    }
  }

  def copy(node:Node): Unit = {
    neighbors = node.neighbors.clone()
    function = for ((k:BitSet,v:Boolean)<-node.function) yield (BitSet()++k,v)
  }


  if (neighbors.nonEmpty && function.isEmpty) init()



}
