package examplefuncsplayer;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;


/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    // Simple defense-focused tuning knobs
    static final int PAINT_LOW_SOLDIER = 60;
    static final int PAINT_LOW_MOPPER = 40;
    static final int UPGRADE_CHIPS_SOON = 1800;
    static final int UPGRADE_ROUND_SOON = 150;
    static final int UPGRADE_CHIPS_LATE = 1200;
    static final int UPGRADE_ROUND_LATE = 280;
    static int localSpawnCount = 0;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break;
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{
        // Attack weakest enemy
        RobotInfo weakest = null;
        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (!rc.canAttack(enemy.getLocation())) continue;
            if (weakest == null || enemy.getHealth() < weakest.getHealth()) weakest = enemy;
        }
        if (weakest != null) rc.attack(weakest.getLocation());

        if (rc.isActionReady() && rc.canUpgradeTower(rc.getLocation())) {
            if ((rc.getRoundNum() > UPGRADE_ROUND_SOON && rc.getChips() > UPGRADE_CHIPS_SOON)
                || (rc.getRoundNum() > UPGRADE_ROUND_LATE && rc.getChips() > UPGRADE_CHIPS_LATE)) {
                rc.upgradeTower(rc.getLocation());
                return;
            }
        }

        UnitType want = chooseSpawnType(rc);
        MapLocation spawn = null;
        for (Direction d : directions) {
            MapLocation loc = rc.getLocation().add(d);
            if (rc.canBuildRobot(want, loc)) {
                spawn = loc;
                break;
            }
        }
        if (spawn != null && rc.canBuildRobot(want, spawn)) {
            rc.buildRobot(want, spawn);
            localSpawnCount++;
        }
    }

    private static UnitType chooseSpawnType(RobotController rc) {
        int chips = rc.getChips();
        int round = rc.getRoundNum();

        // awal awal banyakin soldier dulu
        if (round < 100 || chips < 1200) {
            return (localSpawnCount % 6 == 5) ? UnitType.MOPPER : UnitType.SOLDIER;
        }

        // mid game kasih splasher
        if (chips < 2500) {
            int slot = localSpawnCount % 8;
            if (slot == 6) return UnitType.MOPPER;
            if (slot == 7) return UnitType.SPLASHER;
            return UnitType.SOLDIER;
        }

        // kalo kaya banyakin splasher
        int slot = localSpawnCount % 10;
        if (slot <= 4) return UnitType.SPLASHER;
        if (slot == 5) return UnitType.MOPPER;
        return UnitType.SOLDIER;
    }


    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException{
        MapLocation here = rc.getLocation();

        // Refill paint when low
        if (rc.getPaint() < PAINT_LOW_SOLDIER) {
            MapLocation tower = nearestAllyTower(rc);
            if (tower != null && rc.getLocation().distanceSquaredTo(tower) <= 2 && rc.canTransferPaint(tower, -10)) {
                int need = rc.getType().paintCapacity - rc.getPaint();
                rc.transferPaint(tower, -need);
            } else {
                moveToward(rc, tower);
            }
            return;
        }

        // fokus bangun tower cuy
        MapLocation ruin = nearestOpenRuin(rc);
        if (ruin != null) {
            handleRuin(rc, ruin, UnitType.LEVEL_ONE_DEFENSE_TOWER);
            return;
        }

        // gelud lawan musuh
        RobotInfo targetEnemy = weakestEnemy(rc);
        if (targetEnemy != null) {
            if (rc.canAttack(targetEnemy.getLocation())) {
                rc.attack(targetEnemy.getLocation());
            } else {
                moveToward(rc, targetEnemy.getLocation());
            }
            return;
        }

        //jaga tower as default
        MapLocation allyTower = nearestAllyTower(rc);
        if (allyTower != null) {
            if (here.distanceSquaredTo(allyTower) > 8) moveToward(rc, allyTower);
            paintPriorityTile(rc);
            return;
        }

        
    }


    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runMopper(RobotController rc) throws GameActionException{
        // Refill paint
        if (rc.getPaint() < PAINT_LOW_MOPPER) {
            MapLocation tower = nearestAllyTower(rc);
            if (tower != null) moveToward(rc, tower);
            return;
        }

        // fokus jaga tower dan lawan musuh
        RobotInfo enemy = weakestEnemy(rc);
        if (enemy != null) {
            Direction d = rc.getLocation().directionTo(enemy.getLocation());
            if (rc.canMopSwing(d)) {
                rc.mopSwing(d);
            } else if (rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
            } else {
                moveToward(rc, enemy.getLocation());
            }
            return;
        }

        MapLocation enemyPaint = nearestEnemyPaint(rc);
        if (enemyPaint != null) {
            if (rc.canAttack(enemyPaint)) rc.attack(enemyPaint);
            else moveToward(rc, enemyPaint);
            return;
        }

        MapLocation tower = nearestAllyTower(rc);
        if (tower != null) moveToward(rc, tower);
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        if (rc.getPaint() < 80) {
            MapLocation tower = nearestAllyTower(rc);
            moveToward(rc, tower);
            return;
        }

        // Splash di tempat padet musuh
        MapLocation best = null;
        int bestScore = 0;
        for (MapInfo t : rc.senseNearbyMapInfos()) {
            MapLocation loc = t.getMapLocation();
            if (!rc.canAttack(loc)) continue;
            int score = 0;
            for (MapInfo a : rc.senseNearbyMapInfos(loc, 2)) {
                PaintType p = a.getPaint();
                if (p.isEnemy()) score += 4;
                else if (p == PaintType.EMPTY) score += 2;
                else score -= 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        if (best != null) {
            rc.attack(best);
            return;
        }

        // jaga tower
        MapLocation tower = nearestAllyTower(rc);
        if (tower != null) moveToward(rc, tower);
    }

    private static void handleRuin(RobotController rc, MapLocation ruin, UnitType towerType) throws GameActionException {
        MapLocation here = rc.getLocation();
        if (here.distanceSquaredTo(ruin) > 8) {
            moveToward(rc, ruin);
            return;
        }

        if (rc.canMarkTowerPattern(towerType, ruin) && rc.senseMapInfo(ruin).getMark() == PaintType.EMPTY) {
            rc.markTowerPattern(towerType, ruin);
        }

        for (MapInfo patternTile : rc.senseNearbyMapInfos(ruin, 8)) {
            if (patternTile.getMark() == PaintType.EMPTY) continue;
            if (patternTile.getMark() == patternTile.getPaint()) continue;
            boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
            if (rc.canAttack(patternTile.getMapLocation())) {
                rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                return;
            }
        }

        if (rc.canCompleteTowerPattern(towerType, ruin)) {
            rc.completeTowerPattern(towerType, ruin);
            rc.setTimelineMarker("Defense tower built", 0, 255, 0);
        }
    }

    private static void paintPriorityTile(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation here = rc.getLocation();
        for (MapInfo t : rc.senseNearbyMapInfos()) {
            MapLocation loc = t.getMapLocation();
            if (!rc.canAttack(loc)) continue;
            if (t.hasRuin() || t.isWall()) continue;
            int score = 0;
            if (t.getPaint().isEnemy()) score += 20;
            else if (t.getPaint() == PaintType.EMPTY) score += 10;
            score -= here.distanceSquaredTo(loc);
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        if (best != null) rc.attack(best);
    }

    private static RobotInfo weakestEnemy(RobotController rc) throws GameActionException {
        RobotInfo weakest = null;
        for (RobotInfo e : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (weakest == null || e.getHealth() < weakest.getHealth()) weakest = e;
        }
        return weakest;
    }

    private static MapLocation nearestAllyTower(RobotController rc) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo r : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (!r.getType().isTowerType()) continue;
            int d = rc.getLocation().distanceSquaredTo(r.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = r.getLocation();
            }
        }
        return best;
    }

    static MapLocation nearestOpenRuin(RobotController rc) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
            if (rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo ri = rc.senseRobotAtLocation(ruin);
                if (ri != null && ri.getType().isTowerType())
                    continue;
            }
            int d = rc.getLocation().distanceSquaredTo(ruin);
            if (d < bestDist) {
                bestDist = d;
                best = ruin;
            }
        }
        return best;
    }

    private static MapLocation nearestEnemyPaint(RobotController rc) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo t : rc.senseNearbyMapInfos()) {
            if (!t.getPaint().isEnemy()) continue;
            int d = rc.getLocation().distanceSquaredTo(t.getMapLocation());
            if (d < bestDist) {
                bestDist = d;
                best = t.getMapLocation();
            }
        }
        return best;
    }

    private static void moveToward(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null || !rc.isMovementReady()) return;
        Direction dir = rc.getLocation().directionTo(target);
        if (dir == Direction.CENTER) dir = directions[rng.nextInt(directions.length)];
        for (int i = 0; i < directions.length; i++) {
            Direction tryDir = dir;
            if (i > 0) tryDir = directions[(dir.ordinal() + i) % directions.length];
            if (rc.canMove(tryDir)) {
                rc.move(tryDir);
                return;
            }
        }
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots!");
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            if (rc.getRoundNum() % 20 == 0){
                for (RobotInfo ally : allyRobots){
                    if (rc.canSendMessage(ally.location, enemyRobots.length)){
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
        }
    }
}
