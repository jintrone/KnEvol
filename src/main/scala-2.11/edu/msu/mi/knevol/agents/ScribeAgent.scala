package edu.msu.mi.knevol.agents

import java.sql.Timestamp
import java.util.Date

import akka.actor.Actor
import akka.actor.Actor.Receive

import scala.collection.BitSet
import slick.driver.MySQLDriver.api._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration._

import edu.msu.mi.knevol.universe._

import scala.util.{Failure, Success}

/**
 * Created by josh on 10/29/15.
 */
case class InitRun(universeSize: Int,
                   universeDegree: Int,
                   observableState: Int,
                   population: Int,
                   brainSize: Int,
                   contextSize: Int,
                   numInterests: Int,
                   socialDegree: Int,
                    notes:String="")

case class AgentUpdate(simId:Int,
                       round: Int,
                       name: String,
                       interests: Set[BitSet],
                       fitness: Float,
                       fitnessChange: Float,
                       updateBy: Option[String])

case object Shutdown


class ScribeAgent extends Actor {


  val db = Database.forURL("jdbc:mysql://localhost:3306/knevol2", driver = "com.mysql.jdbc.Driver", user = "knevol", password = "knevol",
    executor = AsyncExecutor("test1", numThreads=10, queueSize=100000))

  class SimulationRun(tag: Tag) extends Table[(Int, Timestamp, Int, Int, Int, Int, Int, Int, Int, Int, String)](tag, "SIMULATION_RUN") {
    def id = column[Int]("SIM_ID", O.PrimaryKey, O.AutoInc)

    // This is the primary key column
    def date = column[Timestamp]("SIM_DATE")

    def universeSize = column[Int]("UNIV_SIZE")

    def universeDegree = column[Int]("UNIV_DEGREE")

    def observableState = column[Int]("OBS_STATE")

    def population = column[Int]("POP")

    def brainSize = column[Int]("BRAINS")

    def contextSize = column[Int]("CONTEXT")

    def numInterests = column[Int]("NUM_INTERESTS")

    def socialDegree = column[Int]("SOC_DEGREE")

    def notes = column[String]("NOTES")

    // Every table needs a * projection with the same type as the table's type parameter
    def * = (id, date, universeSize, universeDegree, observableState, population, brainSize, contextSize, numInterests, socialDegree, notes)
  }

  val simrun = TableQuery[SimulationRun]

  class AgentData(tag: Tag) extends Table[(Int, Int, String, Int, String, Float, Float, String)](tag, "AGENT_DATA") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    // This is the primary key column
    def simId = column[Int]("SIM_ID")

    // This is the primary key column
    def agentsName = column[String]("NAME")

    def iteration = column[Int]("ITERATION")

    def probe = column[String]("PROBE")

    def fitness = column[Float]("FITNESS")

    def fitnessChange = column[Float]("FITNESS_DELTA")

    def updatedBy = column[String]("UPDATE_BY")

    def * = (id, simId, agentsName, iteration, probe, fitness, fitnessChange, updatedBy)

    def simulationRun = foreignKey("SIM_FK", simId, simrun)(_.id)

    // Every table needs a * projection with the same type as the table's type parameter

  }

  val agentData = TableQuery[AgentData]



  db.run(DBIO.seq((simrun.schema ++ agentData.schema).create)) onComplete {
    case x =>
      println(s"Initialized $x")
  }

  override def receive: Receive = {

    case InitRun(universeSize: Int,
    universeDegree: Int,
    observableState: Int,
    population: Int,
    brainSize: Int,
    contextSize: Int,
    numInterests:Int,
    socialDegree: Int,
    notes:String ) =>
      val returnTo= sender()
      println("Trying to update sim table")
      db.run {
        (simrun returning simrun.map(_.id)) +=(0, new Timestamp(System.currentTimeMillis()), universeSize, universeDegree, observableState, population, brainSize, contextSize, numInterests, socialDegree,notes)
      } onComplete {
        case Success(x) =>
          println(s"Received simulation init $x")
          returnTo ! x

        case Failure(x)=>
          println(s"Failed $x")
          x.printStackTrace()

      }




    //so something

    case AgentUpdate(simId:Int,
    round: Int,
    name: String,
    probe: Set[BitSet],
    fitness: Float,
    fitnessChange: Float,
    updateBy: Option[String]) =>

      db.run {
       agentData+= (0,simId,name,round,probe.map(convert(_).toInt).toString(),fitness,fitnessChange,updateBy.getOrElse("None"))
      }

  }
}





