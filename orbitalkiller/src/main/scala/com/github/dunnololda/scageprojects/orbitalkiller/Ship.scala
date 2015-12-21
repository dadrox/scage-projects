package com.github.dunnololda.scageprojects.orbitalkiller

import OrbitalKiller._
import com.github.dunnololda.scage.ScageLibD._
import com.github.dunnololda.scage.support.DVec

import scala.collection.mutable.ArrayBuffer

sealed trait FlightMode

case object Free                    extends FlightMode
case object Killrot                 extends FlightMode
case object AxisAligned             extends FlightMode
case object VelocityAligned         extends FlightMode
case object OppositeVelocityAligned extends FlightMode
case object CirclularOrbit          extends FlightMode
case object NearestShipVelocity     extends FlightMode
case object NearestPlanetVelocity   extends FlightMode
case object AbsoluteStop            extends FlightMode
case object Maneuvering             extends FlightMode

trait Ship {
  def index:String
  var selected_engine:Option[Engine] = None

  def isSelectedEngine(e:Engine):Boolean = {
    selected_engine.exists(x => x == e)
  }

  def switchEngineSelected(engine_code:Int) {
    engines_mapping.get(engine_code).foreach(e => if(isSelectedEngine(e)) selected_engine = None else selected_engine = Some(e))
  }

  def engines:List[Engine]
  def engines_mapping:Map[Int, Engine]
  def switchEngineActive(engine_code:Int) {
    //timeMultiplier = realtime
    engines_mapping.get(engine_code).foreach(e => e.switchActive())
  }

  def selectOrSwitchEngineActive(engine_code:Int) {
    //timeMultiplier = realtime
    engines_mapping.get(engine_code).foreach(e => {
      if(selected_engine.exists(_ == e)) {
        e.switchActive()
      } else {
        selected_engine = Some(e)
      }
    })
  }

  def mass:Double

  def fuelMass:Double
  def fuelMass_=(m:Double):Unit

  def initState:BodyState
  def currentState:MutableBodyState

  def linearAcceleration = currentState.acc

  def linearVelocity = currentState.vel

  def relativeLinearVelocity = {
    linearVelocity - insideSphereOfInfluenceOfCelestialBody(coord, mass, OrbitalKiller.currentPlanetStates).map(_._2.vel).getOrElse(DVec.zero)
  }

  def velocityStr:String = {
    insideSphereOfInfluenceOfCelestialBody(coord, mass, OrbitalKiller.currentPlanetStates) match {
      case Some((planet, planet_state)) =>
        s"${msecOrKmsec((linearVelocity - planet_state.vel).norma)} (${planet.name}), ${msecOrKmsec(linearVelocity.norma)} (абсолютная)"
      case None =>
        s"${msecOrKmsec(linearVelocity.norma)} (абсолютная)"
    }
  }

  def coord = currentState.coord

  def angularAcceleration = currentState.ang_acc

  def angularVelocity = currentState.ang_vel

  def rotation = currentState.ang

  def currentReactiveForce(time:Long, bs:BodyState):DVec = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(DVec.dzero) {
      case (sum, e) => sum + e.force.rotateDeg(bs.ang)
    }
  }

  def currentReactiveForce(time:Long, bs:MutableBodyState):DVec = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(DVec.dzero) {
      case (sum, e) => sum + e.force.rotateDeg(bs.ang)
    }
  }

  def fuelConsumptionPerTact:Double = {
    engines.filter(e => e.active).foldLeft(0.0) {
      case (sum, e) => sum + e.fuelConsumptionPerTact
    }
  }

  def fuelMassWhenEnginesOff:Double = {
    fuelMass - engines.filter(e => e.active).map(e => e.workTimeTacts*e.fuelConsumptionPerTact).sum
  }

  def fuelMassWhenEnginesOffWithoutEngine(ee:Engine):Double = {
    fuelMass - engines.filter(e => e.active && e != ee).map(e => e.workTimeTacts*e.fuelConsumptionPerTact).sum
  }

  def currentMass(time:Long, bs:BodyState):Double = {
    mass - engines.filter(e => e.active).foldLeft(0.0) {
      case (sum, e) =>
        sum + e.fuelConsumptionPerTact * (math.min(time, e.stopMomentTacts) - (e.stopMomentTacts - e.workTimeTacts))
    }
  }

  def currentMass(time:Long):Double = {
    mass - engines.filter(e => e.active).foldLeft(0.0) {
      case (sum, e) =>
        sum + e.fuelConsumptionPerTact * (math.min(time, e.stopMomentTacts) - (e.stopMomentTacts - e.workTimeTacts))
    }
  }

  def currentTorque(time:Long, bs:BodyState):Double = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(0.0) {
      case (sum, e) => sum + e.torque
    }
  }

  def currentTorque(time:Long, bs:MutableBodyState):Double = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(0.0) {
      case (sum, e) => sum + e.torque
    }
  }

  def currentTorque(time:Long):Double = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(0.0) {
      case (sum, e) => sum + e.torque
    }
  }

  def deactivateAllEngines(): Unit = {
    engines.foreach(_.active = false)
  }

  def activateOnlyTheseEngines(engines_to_activate:Engine*) {
    //timeMultiplier = realtime
    engines_to_activate.foreach(_.active = true)
    engines.withFilter(e => e.active && !engines_to_activate.contains(e)).foreach(_.active = false)
  }

  def activateOnlyOneEngine(e:Engine) {
    activateOnlyTheseEngines(e)
  }

  def allEnginesInactive:Boolean = {
    engines.forall(!_.active)
  }

  def engineColor(e:Engine):ScageColor = {
    if(pilot_is_dead || e.active) RED else WHITE
  }

  def engineActiveSize(e:Engine, max_size:Double):Double = {
    max_size*e.power/e.max_power
  }

  def drawEngine(e:Engine, center:DVec, width:Double, height:Double, is_vertical:Boolean) {
    drawRectCentered(center, width, height, color = engineColor(e))
    if(isSelectedEngine(e)) drawRectCentered(center, width*1.5, height*1.5, color = engineColor(e))
    if(e.active && e.power > 0) {
      if(is_vertical) {
        drawFilledRectCentered(center, width, engineActiveSize(e, height), color = engineColor(e))
      } else {
        drawFilledRectCentered(center, engineActiveSize(e, width), height, color = engineColor(e))
      }
    }
  }

  def preserveAngularVelocity(ang_vel_deg:Double)
  def preserveVelocity(vel:DVec)

  /**
   * rotation и angle_deg оба в диапазоне 0 - 360
   * @param angle_deg - угол между ориентацией корабля и вектором DVec(0, 1), который необходимо поддерживать
   */
  def preserveAngle(angle_deg:Double) {
    if(rotation != angle_deg) {
      if(rotation > angle_deg) {
        if(rotation - angle_deg < angle_deg - rotation + 360) {
          val diff = rotation - angle_deg
          if (diff > 50) preserveAngularVelocity(-10)
          else if (diff > 10) preserveAngularVelocity(-5)
          else if (diff > 1) preserveAngularVelocity(-1.8)
          else if (diff > 0.1) preserveAngularVelocity(-0.2)
          else preserveAngularVelocity(0)
        } else {
          val diff = angle_deg - rotation + 360
          if (diff > 50) preserveAngularVelocity(10)
          else if (diff > 10) preserveAngularVelocity(5)
          else if (diff > 1) preserveAngularVelocity(1.8)
          else if (diff > 0.1) preserveAngularVelocity(0.2)
          else preserveAngularVelocity(0)
        }
      } else if(rotation < angle_deg) {
        if(rotation - angle_deg > angle_deg - rotation - 360) {
          val diff = rotation - angle_deg
          if(diff < -50) preserveAngularVelocity(10)
          else if(diff < -10) preserveAngularVelocity(5)
          else if(diff < -1) preserveAngularVelocity(1.8)
          else if(diff < -0.1) preserveAngularVelocity(0.2)
          else preserveAngularVelocity(0)
        } else {
          val diff = angle_deg - rotation - 360
          if(diff < -50) preserveAngularVelocity(-10)
          else if(diff < -10) preserveAngularVelocity(-5)
          else if(diff < -1) preserveAngularVelocity(-1.8)
          else if(diff < -0.1) preserveAngularVelocity(-0.2)
          else preserveAngularVelocity(0)
        }
      }
    }
  }

  protected var last_correction_or_check_moment:Long = 0l

  private var prev_flight_mode_and_engine_states:Option[(FlightMode, List[(Long, Double, Boolean)])] = None
  def haveSavedFlightMode = prev_flight_mode_and_engine_states.nonEmpty
  def saveFlightModeAndEngineStates(prev_flight_mode:FlightMode): Unit = {
    prev_flight_mode_and_engine_states = Some((prev_flight_mode, engines.map(e => (e.workTimeTacts, e.power, e.active))))
  }
  def restoreFlightModeAndEngineStates(): Unit = {
    prev_flight_mode_and_engine_states match {
      case Some((prev_flight_mode, engine_states)) =>
        flightMode = prev_flight_mode
        engines.zip(engine_states).foreach {
          case (e, (tacts, power, active)) =>
            e.active = active
            e.workTimeTacts = tacts
            e.power = power
        }
        prev_flight_mode_and_engine_states = None
      case None =>
    }
  }

  private var flight_mode:FlightMode = Free
  def flightMode:FlightMode = flight_mode
  def flightMode_=(new_flight_mode:FlightMode) {
    val prev_flight_mode = flight_mode
    flight_mode = new_flight_mode
    last_correction_or_check_moment = 0l
    if(flight_mode == Maneuvering) {
      engines.foreach(e => e.power = e.max_power*0.5)
      engines.filterNot(_.active).foreach(e => e.workTimeTacts = 226800)     // 1 hour
      val active_engines = engines.filter(_.active)
      if(active_engines.map(ae => ae.fuelConsumptionPerTact*226800).sum <= fuelMass) {
        active_engines.foreach(e => e.workTimeTacts = 226800)
      } else {
        val fuel_for_every_active_engine = fuelMass / active_engines.length
        active_engines.foreach(e => e.workTimeTacts = (fuel_for_every_active_engine/e.fuelConsumptionPerTact).toLong)
      }
    } else {
      if(flight_mode == Free) {
        engines.foreach(e => e.active = false)
      } else if(flight_mode == Killrot && prev_flight_mode == Maneuvering) {
        saveFlightModeAndEngineStates(prev_flight_mode)
      } else if(flight_mode == NearestPlanetVelocity) {
        vertical_speed_msec = 0
        horizontal_speed_msec = 0
      }
      if(prev_flight_mode == Maneuvering && flight_mode != Maneuvering) {
        engines.foreach(e => e.workTimeTacts = 0)
      }
    }
  }

  def vertical_speed_msec:Int = 0
  def vertical_speed_msec_=(x:Int) {}
  def horizontal_speed_msec:Int = 0
  def horizontal_speed_msec_=(x:Int) {}

  def flightModeStr:String = flight_mode match {
    case Free => "свободный"
    case Killrot => "запрет вращения"
    case AxisAligned => "ориентация по осям"
    case VelocityAligned => "ориентация по траектории"
    case OppositeVelocityAligned => "ориентация против траектории"
    case CirclularOrbit => "выход на круговую орбиту"
    case NearestShipVelocity => "уравнять скорость с кораблем"
    case NearestPlanetVelocity => s"уравнять скорость с ближайшей планетой: ${msecOrKmsec(vertical_speed_msec)}, ${msecOrKmsec(horizontal_speed_msec)}"
    case AbsoluteStop => "остановиться"
    case Maneuvering => "маневрирование"
    case _ => ""
  }

  def otherShipsNear:List[Ship] = ships.filter(s => s.index != ship.index/* && ship.coord.dist(s.coord) < 100000*/).sortBy(s => ship.coord.dist(s.coord))

  protected val pilot_mass = 75
  protected val pilot_position = DVec(0, 8)
  private var pilot_average_g:Double = 0.0
  private val pilot_gs = ArrayBuffer[(Double, Long)]()

  private var pilot_is_dead = false
  private var pilot_death_reason = ""
  def pilotIsDead = pilot_is_dead
  def pilotIsAlive = !pilot_is_dead

  def colorIfAliveOrRed(color: => ScageColor) = if(pilot_is_dead) RED else color

  private var ship_removed = false
  def isRemoved = ship_removed

  def updateShipState(time_msec:Long): Unit = {
    if(!pilot_is_dead) {
      val dvel = currentState.dvel.norma
      if (dvel > 10) { // crash tolerance = 10 m/s
        pilot_is_dead = true
        pilot_death_reason = f"Корабль уничтожен в результате столкновения (${dvel/OrbitalKiller.base_dt/{earth.g}}%.2fg)"
        flightMode = Free
        if(dvel > 100) {
          OrbitalKiller.system_evolution.removeBodyByIndex(index)
          println("removed ship from mutable system")
        }
      } else {
        val reactive_force = currentReactiveForce(0, currentState) + earth.airResistance(currentState, earth.currentState, 28, 0.5)
        val centrifugial_force = if (angularVelocity == 0) DVec.zero else pilot_mass * math.pow(angularVelocity.toRad, 2) * pilot_position.rotateDeg(rotation)
        //val air_resistance =  earth.airResistance(coord, linearVelocity, 10, 0.5)
        val pilot_acc = (reactive_force / mass + centrifugial_force / pilot_mass + currentState.dacc).norma/* - (air_resistance.norma/mass)*/
        pilot_gs += ((pilot_acc / earth.g, time_msec))
        if (time_msec - pilot_gs.head._2 >= 1000) {
          pilot_average_g = pilot_gs.map(_._1).sum / pilot_gs.length
          pilot_gs.clear()
        }
      }
    }
  }

  def pilotStateStr:String = {
    if(!pilot_is_dead) {
      if (pilot_average_g < 0.1) "Пилот в состоянии невесомости"
      else if (pilot_average_g <= 1) f"Пилот испытывает силу тяжести $pilot_average_g%.1fg"
      else f"Пилот испытывает перегрузку $pilot_average_g%.1fg"
    } else {
      pilot_death_reason
    }
  }

  def massStr = f"Масса корабля: $mass%.1f кг. Остаток топлива: $fuelMass%.1f кг"

  def shadowSideStr = {
    val in_shadow_of_planet:Option[String] = {
      val ship_sun_dist = ship.coord.dist(sun.coord)
      currentPlanetStates.filterNot(_._1.index == sun.index).find {
        case (planet, planet_state) =>
          ship_sun_dist > planet.coord.dist(sun.coord) && (tangentsFromCircleToCircle(planet.coord, planet.radius, sun.coord, sun.radius) match {
            case Some((c1, c2, b1, b2)) =>
              val a1 = (c1 - b1).perpendicular * (ship.coord - b1) > 0
              val a2 = (c2 - b2).perpendicular * (ship.coord - b2) < 0
              a1 && a2
            case None => false
          })
      }.map(_._1.name)
    }
    in_shadow_of_planet match {
      case Some(planet_name) =>
        /*planet_name match {
          case moon.name => "Корабль находится в тени Луны"
          case earth.name => "Корабль находится в тени Земли"
          case _ => "Корабль находится в тени"
        }*/
        "Корабль находится в тени"
      case None =>
        "Корабль освещается солнцем"
    }
  }
}
