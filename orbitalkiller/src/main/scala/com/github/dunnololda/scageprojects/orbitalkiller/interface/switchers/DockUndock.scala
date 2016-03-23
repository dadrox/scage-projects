package com.github.dunnololda.scageprojects.orbitalkiller.interface.switchers

import com.github.dunnololda.scageprojects.orbitalkiller.{OrbitalKiller, InterfaceHolder, InterfaceSwitcher}

class DockUndock extends InterfaceSwitcher {
  override def strVariants: Array[String] = Array("Dock", "Undock")

  def setDocked(): Unit = {
    selected_variant = 1
  }

  def needUndock = {
    selected_variant == 0
  }

  def needDock = {
    selected_variant == 1
  }

  override def active:Boolean = {
    InterfaceHolder.dockingSwitcher.dockingEnabled && OrbitalKiller.ship.canDockWithNearestShip || OrbitalKiller.ship.isDocked
  }
}
