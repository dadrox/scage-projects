package com.github.dunnololda.scageprojects.orbitalkiller.interface.switchers

import com.github.dunnololda.scageprojects.orbitalkiller.InterfaceSwitcher

class MSecOrKmH extends InterfaceSwitcher {
  override def strVariants: Array[String] = Array("m/sec", "km/h")
}