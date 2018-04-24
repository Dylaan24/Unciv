package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.battle.*
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitType
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.disable
import kotlin.math.max

class BattleTable(val worldScreen: WorldScreen): Table() {

    private val battle = Battle(worldScreen.civInfo.gameInfo)
    init{
        skin = CameraStageBaseScreen.skin
    }

    fun update() {
        if (worldScreen.unitTable.selectedUnit == null
                || worldScreen.unitTable.selectedUnit!!.getBaseUnit().unitType == UnitType.Civilian){
            clear()
            return
        } // no attacker

        val attacker = MapUnitCombatant(worldScreen.unitTable.selectedUnit!!)

        if (worldScreen.tileMapHolder.selectedTile == null) return
        val selectedTile = worldScreen.tileMapHolder.selectedTile!!
        val defender: ICombatant
        if (selectedTile.explored && selectedTile.isCityCenter && selectedTile.getOwner() != worldScreen.civInfo)
            defender = CityCombatant(selectedTile.getCity()!!)
        else if (selectedTile.unit != null
                && selectedTile.unit!!.owner != worldScreen.civInfo.civName // enemy unit on selected tile,
                && worldScreen.civInfo.getViewableTiles().contains(selectedTile))
            defender = MapUnitCombatant(selectedTile.unit!!)
        else {
            clear()
            return
        }
        simulateBattle(attacker, defender)

    }

    fun simulateBattle(attacker: MapUnitCombatant, defender: ICombatant){
        clear()

        row().pad(5f)
        val attackerLabel = Label(attacker.getName(), skin)
        attackerLabel.style= Label.LabelStyle(attackerLabel.style)
        attackerLabel.style.fontColor=attacker.getCivilization().getCivilization().getColor()
        add(attackerLabel)

        val defenderLabel = Label(defender.getName(), skin)
        defenderLabel.style= Label.LabelStyle(defenderLabel.style)
        defenderLabel.style.fontColor=defender.getCivilization().getCivilization().getColor()
        add(defenderLabel)

        row().pad(5f)

        add("Strength: "+attacker.getAttackingStrength(defender)/100f)
        add("Strength: "+defender.getDefendingStrength(attacker)/100f)
        row().pad(5f)

        val attackerModifiers = battle.getAttackModifiers(attacker,defender)  .map { it.key+": "+(if(it.value>0)"+" else "")+(it.value*100).toInt()+"%" }
        val defenderModifiers = battle.getDefenceModifiers(attacker, defender).map { it.key+": "+(if(it.value>0)"+" else "")+(it.value*100).toInt()+"%" }

        for(i in 0..max(attackerModifiers.size,defenderModifiers.size)){
            if (attackerModifiers.size > i) add(attackerModifiers[i]) else add()
            if (defenderModifiers.size > i) add(defenderModifiers[i]) else add()
            row().pad(5f)
        }
        add((battle.getAttackingStrength(attacker,defender)/100f).toString())
        add((battle.getDefendingStrength(attacker,defender)/100f).toString())
        row().pad(5f)

        var damageToDefender = battle.calculateDamageToDefender(attacker,defender)
        var damageToAttacker = battle.calculateDamageToAttacker(attacker,defender)


        if (damageToAttacker>attacker.getHealth() && damageToDefender>defender.getHealth() // when damage exceeds health, we don't want to show negative health numbers
        // Also if both parties are supposed to die it's not indicative of who is more likely to win
        // So we "normalize" the damages until one dies
        ) {
            if(damageToDefender/defender.getHealth().toFloat() > damageToAttacker/attacker.getHealth().toFloat()) // defender dies quicker ie first
            {
                // Both damages *= (defender.health/damageToDefender)
                damageToDefender = defender.getHealth()
                damageToAttacker *= (defender.getHealth()/damageToDefender.toFloat()).toInt()
            } else{ // attacker dies first
                // Both damages *= (attacker.health/damageToAttacker)
                damageToAttacker = attacker.getHealth()
                damageToDefender *= (attacker.getHealth()/damageToAttacker.toFloat()).toInt()
            }
        }
        else if (damageToAttacker>attacker.getHealth()) damageToAttacker=attacker.getHealth()
        else if (damageToDefender>defender.getHealth()) damageToDefender=defender.getHealth()


        add("Health: "+attacker.getHealth().toString() + " -> "
                + (attacker.getHealth()- damageToAttacker))

        add("Health: "+defender.getHealth().toString() + " -> "
                + (defender.getHealth()- damageToDefender))

        row().pad(5f)
        val attackButton = TextButton("Attack", skin)

        val attackerDistanceToTiles = attacker.unit.getDistanceToTiles()

        val attackerCanReachDefender:Boolean
        var tileToMoveTo:TileInfo? = null

        if(attacker.getCombatantType() == CombatantType.Melee){
            if(attacker.getTile().neighbors.contains(defender.getTile())){
                attackerCanReachDefender=true
            }
            else {
                val tilesCanAttackFrom = attackerDistanceToTiles.filter {
                    attacker.unit.currentMovement - it.value > 0 // once we reach it we'll still have energy to attack
                            && it.key.unit == null
                            && it.key.neighbors.contains(defender.getTile())
                }

                if (tilesCanAttackFrom.isEmpty()) attackerCanReachDefender=false
                else {
                    tileToMoveTo = tilesCanAttackFrom.minBy { it.value }!!.key // travel least distance
                    attackerCanReachDefender=true
                }
            }
        }

        else { // ranged
            val tilesInRange = UnCivGame.Current.gameInfo.tileMap.getTilesInDistance(attacker.getTile().position, 2)
            attackerCanReachDefender = tilesInRange.contains(defender.getTile())
        }

        if(!attackerCanReachDefender || attacker.unit.currentMovement==0f) attackButton.disable()
        else {
            attackButton.addClickListener {
                if(tileToMoveTo!=null) attacker.unit.moveToTile(tileToMoveTo)
                battle.attack(attacker, defender)
                worldScreen.update()
            }
        }

        add(attackButton).colspan(2)

        pack()
        setPosition(worldScreen.stage.width/2-width/2, 5f)
    }

}