package com.v5ent.game.battle;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Timer;
import com.v5ent.game.entities.Monster;
import com.v5ent.game.entities.Role;
import com.v5ent.game.hud.BattleUI;
import com.v5ent.game.inventory.InventoryEvent;

public class BattleState {
    private static final String TAG = BattleState.class.getSimpleName();

    private Monster _currentOpponent;
    private BattleUI battleUI;
    private int _currentZoneLevel = 0;
    private int _currentPlayerAP;
    private int _currentPlayerDP;
    private int _currentPlayerWandAPPoints = 0;
    private final int _chanceOfAttack = 25;
    private final int _chanceOfEscape = 40;
    private final int _criticalChance = 90;
    private Timer.Task _playerAttackCalculations;
    private Timer.Task _opponentAttackCalculations;
    private Timer.Task _checkPlayerMagicUse;

    public BattleState(BattleUI battleUI){
        this.battleUI = battleUI;
        _playerAttackCalculations = getPlayerAttackCalculationTimer();
        _opponentAttackCalculations = getOpponentAttackCalculationTimer(battleUI.player);
        _checkPlayerMagicUse = getPlayerMagicUseCheckTimer(battleUI.player);
    }

    public void resetDefaults(){
        Gdx.app.error(TAG, "Resetting defaults...");
        _currentZoneLevel = 0;
        _currentPlayerAP = 0;
        _currentPlayerDP = 0;
        _currentPlayerWandAPPoints = 0;
        _playerAttackCalculations.cancel();
        _opponentAttackCalculations.cancel();
        _checkPlayerMagicUse.cancel();
    }

    public void setCurrentZoneLevel(int zoneLevel){
        _currentZoneLevel = zoneLevel;
    }

    public int getCurrentZoneLevel(){
        return _currentZoneLevel;
    }

    public boolean isOpponentReady(){
        if( _currentZoneLevel == 0 ) return false;
        int randomVal = MathUtils.random(1,100);

        Gdx.app.error(TAG, "CHANGE OF ATTACK: " + _chanceOfAttack + " random val: " + randomVal);

        if( _chanceOfAttack > randomVal  ){
            setCurrentOpponent();
            return true;
        }else{
            return false;
        }
    }

    public void setCurrentOpponent(){
        Gdx.app.error(TAG, "Entered BATTLE ZONE: " + _currentZoneLevel);
        Monster entity = MonsterFactory.getInstance().getRandomMonster(_currentZoneLevel);
        if( entity == null ) return;
        this._currentOpponent = entity;
        battleUI.updateEvent(entity, BattleEvent.OPPONENT_ADDED);
    }

    public void playerAttacks(Role player){
        if( _currentOpponent == null ){
            return;
        }

        //Check for magic if used in attack; If we don't have enough MP, then return
//        int mpVal = ProfileManager.getInstance().getProperty("currentPlayerMP", Integer.class);
        int mpVal = player.getMagicPoint();
        battleUI.updateEvent(_currentOpponent, BattleEvent.PLAYER_TURN_START);

        if( _currentPlayerWandAPPoints == 0 ){
            if( !_playerAttackCalculations.isScheduled() ){
                Timer.schedule(_playerAttackCalculations, 1);
            }
        }else if(_currentPlayerWandAPPoints > mpVal ){
            BattleState.this.battleUI.updateEvent(_currentOpponent, BattleEvent.PLAYER_TURN_DONE);
            return;
        }else{
            if( !_checkPlayerMagicUse.isScheduled() && !_playerAttackCalculations.isScheduled() ){
                Timer.schedule(_checkPlayerMagicUse, .5f);
                Timer.schedule(_playerAttackCalculations, 1);
            }
        }
    }

    public void opponentAttacks(){
        if( _currentOpponent == null ){
            return;
        }

        if( !_opponentAttackCalculations.isScheduled() ){
            Timer.schedule(_opponentAttackCalculations, 1);
        }
    }

    private Timer.Task getPlayerMagicUseCheckTimer(final Role player){
        return new Timer.Task() {
            @Override
            public void run() {
//                int mpVal = ProfileManager.getInstance().getProperty("currentPlayerMP", Integer.class);
                int mpVal = player.getMagicPoint();
                mpVal -= _currentPlayerWandAPPoints;
//                ProfileManager.getInstance().setProperty("currentPlayerMP", mpVal);
                player.setMagicPoint(mpVal);
                BattleState.this.battleUI.updateEvent(_currentOpponent, BattleEvent.PLAYER_USED_MAGIC);
            }
        };
    }

    private Timer.Task getPlayerAttackCalculationTimer() {
        return new Timer.Task() {
            @Override
            public void run() {
                int currentOpponentHP = _currentOpponent.getHealthPoint();//Integer.parseInt(_currentOpponent.getEntityConfig().getPropertyValue(EntityConfig.EntityProperties.ENTITY_HEALTH_POINTS.toString()));
                int currentOpponentDP = _currentOpponent.getDefensePoint();//Integer.parseInt(_currentOpponent.getEntityConfig().getPropertyValue(EntityConfig.EntityProperties.ENTITY_DEFENSE_POINTS.toString()));

                int damage = MathUtils.clamp(_currentPlayerAP - currentOpponentDP, 0, _currentPlayerAP);

                Gdx.app.error(TAG, "ENEMY HAS " + currentOpponentHP + " hit with damage: " + damage);

                currentOpponentHP = MathUtils.clamp(currentOpponentHP - damage, 0, currentOpponentHP);
//                _currentOpponent.getEntityConfig().setPropertyValue(EntityConfig.EntityProperties.ENTITY_HEALTH_POINTS.toString(), String.valueOf(currentOpponentHP));
                _currentOpponent.setHealthPoint(currentOpponentHP);

                Gdx.app.error(TAG, "Player attacks " + _currentOpponent.getEntityId() + " leaving it with HP: " + currentOpponentHP);

//                _currentOpponent.getEntityConfig().setPropertyValue(EntityConfig.EntityProperties.ENTITY_HIT_DAMAGE_TOTAL.toString(), String.valueOf(damage));
                _currentOpponent.setHitDamageTotal(damage);
                if( damage > 0 ){
                    BattleState.this.battleUI.updateEvent(_currentOpponent, BattleEvent.OPPONENT_HIT_DAMAGE);
                }

                if (currentOpponentHP == 0) {
                    BattleState.this.battleUI.updateEvent(_currentOpponent, BattleEvent.OPPONENT_DEFEATED);
                }

                BattleState.this.battleUI.updateEvent(_currentOpponent, BattleEvent.PLAYER_TURN_DONE);
            }
        };
    }

    private Timer.Task getOpponentAttackCalculationTimer(final Role player) {
        return new Timer.Task() {
            @Override
            public void run() {
//                int currentOpponentHP = Integer.parseInt(_currentOpponent.getEntityConfig().getPropertyValue(EntityConfig.EntityProperties.ENTITY_HEALTH_POINTS.toString()));
                int currentOpponentHP = _currentOpponent.getHealthPoint();
                if (currentOpponentHP <= 0) {
                    BattleState.this.battleUI.updateEvent(_currentOpponent, BattleEvent.OPPONENT_TURN_DONE);
                    return;
                }

//                int currentOpponentAP = Integer.parseInt(_currentOpponent.getEntityConfig().getPropertyValue(EntityConfig.EntityProperties.ENTITY_ATTACK_POINTS.toString()));
                int currentOpponentAP = _currentOpponent.getAttackPoint();
                int damage = MathUtils.clamp(currentOpponentAP - _currentPlayerDP, 0, currentOpponentAP);
//                int hpVal = ProfileManager.getInstance().getProperty("currentPlayerHP", Integer.class);
                int hpVal = player.getHealthPoint();
                hpVal = MathUtils.clamp( hpVal - damage, 0, hpVal);
//                ProfileManager.getInstance().setProperty("currentPlayerHP", hpVal);
                player.setHealthPoint(hpVal);
                if( damage > 0 ) {
                    BattleState.this.battleUI.updateEvent(_currentOpponent, BattleEvent.PLAYER_HIT_DAMAGE);
                }

                Gdx.app.error(TAG, "Player HIT for " + damage + " BY " + _currentOpponent.getEntityId() + " leaving player with HP: " + hpVal);

                BattleState.this.battleUI.updateEvent(_currentOpponent, BattleEvent.OPPONENT_TURN_DONE);
            }
        };
    }

    public void playerRuns(){
        int randomVal = MathUtils.random(1,100);
        if( _chanceOfEscape > randomVal  ) {
            battleUI.updateEvent(_currentOpponent, BattleEvent.PLAYER_RUNNING);
        }else if (randomVal > _criticalChance){
            opponentAttacks();
        }else{
            return;
        }
    }
/********************************************** EVENT **************************************************/

    public void updateForFight(String value, InventoryEvent event) {
        switch(event) {
            case UPDATED_AP:
                int apVal = Integer.valueOf(value);
                _currentPlayerAP = apVal;
                Gdx.app.error(TAG, "APVAL: " + _currentPlayerAP);
                break;
            case UPDATED_DP:
                int dpVal = Integer.valueOf(value);
                _currentPlayerDP = dpVal;
                Gdx.app.error(TAG, "DPVAL: " + _currentPlayerDP);
                break;
            case ADD_WAND_AP:
                int wandAP = Integer.valueOf(value);
                _currentPlayerWandAPPoints += wandAP;
                Gdx.app.error(TAG, "WandAP: " + _currentPlayerWandAPPoints);
                break;
            case REMOVE_WAND_AP:
                int removeWandAP = Integer.valueOf(value);
                _currentPlayerWandAPPoints -= removeWandAP;
                Gdx.app.error(TAG, "WandAP: " + _currentPlayerWandAPPoints);
                break;
            default:
                break;
        }
    }
}
