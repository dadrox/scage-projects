package net.scageprojects.td

import net.scage.support.tracer3.Trace
import net.scageprojects.td.TowerDemka._
import net.scage.support.{State, ScageColor, Vec}
import net.scage.ScageLib._

class Bullet(start_coord:Vec, target:Trace with HaveHitPoints, damage_amount:Float, bullet_color:ScageColor) extends SelfRemovable {
  val bullet_speed = 6f   // maybe we need an option for this

  private var lifetime = 500
  private var coord = start_coord


  private val action_id = action(10) {
    if(target.hp <= 0) remove()
    else {
      coord += (target.location - coord).n*3*(bullet_speed/6f)
      if(coord.dist(target.location) < 3) {
        target.changeState(null, State("damage" -> damage_amount))
        remove()
        new FlyingWord(damage_amount.formatted("%.1f"), bullet_color, coord, (target.location - coord))
      } else {
        lifetime -= 1
        if(lifetime <= 0) {
          remove()
        }
      }
    }
  }

  private val render_id = render {
    openglMove(coord)
    openglRotateDeg((target.location - coord).deg(Vec(0,1)))  // TODO: fix rotation!
    drawRectCentered(Vec.zero, 5, /*7*/5, bullet_color)
  }

  def remove() {
    delOperations(action_id, render_id, clear_id)
  }

  private val clear_id = clear {
    remove()
  }
}
