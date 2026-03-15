package strat3Refined;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


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
    static int uselessCount = 0;
    static UnitType robotToBuild = UnitType.SOLDIER;
    static UnitType towerToBuild = UnitType.LEVEL_ONE_PAINT_TOWER;
    static MapLocation lastLoc = null;

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
        // You can also use indicators to save debug notes in replays.

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.
            turnCount += 1;  // We have now been alive for one more turn!

            if(rc.getType().isRobotType()) {
                if(turnCount > 1000) rc.disintegrate();
            }

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

                if(rc.getActionCooldownTurns() < 10) uselessCount++;
                else if(uselessCount > 0) uselessCount--;
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
        boolean enemyExist = false;
        for(MapInfo tile : rc.senseNearbyMapInfos()) {
            if(tile.isWall()) continue;
            if(tile.getPaint().isEnemy()) enemyExist = true;
        }
        enemyExist = enemyExist || (rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length > 0);

        if(rc.senseNearbyRobots().length < rc.getRoundNum() / 500 + 2 && !enemyExist) robotToBuild = UnitType.SOLDIER;
        else {
            if(rc.getRoundNum() % 4 == 0) {
                robotToBuild = UnitType.MOPPER;
            } 
            else {
                robotToBuild = UnitType.SPLASHER;
            }
        }

        // Pick a direction to build in.
        Direction dir = bestDirection(rc);
        MapLocation nextLoc = rc.getLocation().add(dir);

        if ((enemyExist && rc.getPaint() > UnitType.SPLASHER.paintCost) || (rc.canBuildRobot(robotToBuild, nextLoc) && rc.getChips() > 1000 + UnitType.SPLASHER.moneyCost && rc.getPaint() > UnitType.SPLASHER.paintCost)){
            rc.buildRobot(robotToBuild, nextLoc);
        }

        // Attack other bots
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for(RobotInfo robot : nearbyRobots) {
            if(!rc.canAttack(robot.getLocation())) break;
            rc.attack(robot.getLocation());
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException{
        // Get paint if paint almost half empty
        if(uselessCount < 5) getPaint(rc);

        soldierObjective(rc);

        // Move and attack if no objective.
        if(rc.isMovementReady()) {
            Direction dir = bestDirection(rc);
            if (rc.canMove(dir)){
                lastLoc = rc.getLocation();
                rc.move(dir);
            }
        }

        // Try to paint beneath us as we walk to avoid paint penalties.
        // Avoiding wasting paint by re-painting our own tiles.
        if(rc.isActionReady()) {
            MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
            if (rc.canAttack(rc.getLocation()) && (currentTile.getPaint() == PaintType.EMPTY || currentTile.getMark() != PaintType.EMPTY && currentTile.getMark() != currentTile.getPaint())){
                rc.attack(rc.getLocation(), currentTile.getMark().isSecondary());
            }
            for (MapInfo nearbyTile : rc.senseNearbyMapInfos()){
                if (!rc.isActionReady()) break;
                if (nearbyTile.hasRuin() || nearbyTile.isWall()) continue;
                if (nearbyTile.getMark() != PaintType.EMPTY && nearbyTile.getMark() != nearbyTile.getPaint() || nearbyTile.getPaint() == PaintType.EMPTY){
                    if (rc.canAttack(nearbyTile.getMapLocation())) {
                        rc.attack(nearbyTile.getMapLocation(), nearbyTile.getMark().isSecondary());
                    }
                }
            }
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException{
        // Get paint if paint almost half empty
        getPaint(rc);

        mopperObjective(rc);

        // Move and attack.
        if(!rc.isActionReady() && !rc.isMovementReady()) return;
        Direction nextDir = bestDirection(rc);
        MapLocation nextLoc = rc.getLocation().add(nextDir);
        if(!rc.senseMapInfo(nextLoc).getPaint().isEnemy()) {
            forceMove(nextLoc, rc);
        }

        MapLocation currentLoc = rc.getLocation();
        MapInfo currentTile = rc.senseMapInfo(currentLoc);
        
        if (rc.canAttack(currentLoc) && currentTile.getPaint().isEnemy()){
            rc.attack(currentLoc);
        }
        for (MapInfo nearbyTile : rc.senseNearbyMapInfos(2)){
            if (!rc.isActionReady()) break;
            if (nearbyTile.hasRuin() || nearbyTile.isWall()) continue;
            if (nearbyTile.getPaint().isEnemy()){
                if (rc.canAttack(nearbyTile.getMapLocation())) {
                    rc.attack(nearbyTile.getMapLocation());
                }
            }
        }
    }

    public static void runSplasher(RobotController rc) throws GameActionException{
        // Get paint if paint almost half empty
        getPaint(rc);

        // Move and attack
        Direction dir = bestDirection(rc);
        MapLocation nextLoc = rc.getLocation().add(dir);
        if (rc.canMove(dir)){
            lastLoc = rc.getLocation();
            rc.move(dir);
        }
        if (rc.canAttack(nextLoc)){
            for (MapInfo nearbyTile : rc.senseNearbyMapInfos(4)){
                if (!rc.isActionReady()) break;
                if (nearbyTile.hasRuin() || nearbyTile.isWall()) continue;
                if (nearbyTile.getPaint().isEnemy() || nearbyTile.getPaint() == PaintType.EMPTY){
                    if (rc.canAttack(nearbyTile.getMapLocation())) {
                        rc.attack(nearbyTile.getMapLocation(), nearbyTile.getPaint().isSecondary());
                    }
                }
            }
        }
    }

    // Objective: build and upgrade towers
    public static void soldierObjective(RobotController rc) throws GameActionException {
        if(rc.getNumberTowers() < 3 + rc.getRoundNum() / 20 && rc.getNumberTowers() < 5 && rc.getNumberTowers() > 2) towerToBuild = UnitType.LEVEL_ONE_MONEY_TOWER;
        else towerToBuild = UnitType.LEVEL_ONE_PAINT_TOWER;

        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        // Search for a nearby ruin to complete.
        MapInfo curRuin = null;
        int curDistance = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles){
            if (tile.hasRuin() && !isTower(tile, rc)){
                int distance = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if(distance < curDistance) {
                    curRuin = tile;
                    curDistance = distance;
                }
            }
            else if(isTower(tile, rc)) {
                if(rc.senseRobotAtLocation(tile.getMapLocation()).getType() == UnitType.LEVEL_ONE_PAINT_TOWER)
                    towerToBuild = UnitType.LEVEL_ONE_MONEY_TOWER;
            }
        }
        if (curRuin != null){
            MapLocation targetLoc = curRuin.getMapLocation();
            if(!isPatternFull(curRuin, rc)) {
                Direction dir = rc.getLocation().directionTo(targetLoc);
                forceMove(targetLoc, rc);
                // Mark the pattern we need to draw to build a tower here if we haven't already.
                MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(dir);
                if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(towerToBuild, targetLoc)){
                    rc.markTowerPattern(towerToBuild, targetLoc);
                }
                // Fill in any spots in the pattern with the appropriate paint.
                for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)){
                    if (!rc.isActionReady()) break;
                    if (patternTile.hasRuin() || patternTile.isWall()) continue;
                    if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY && !patternTile.getPaint().isEnemy()){
                        if (rc.canAttack(patternTile.getMapLocation())) {
                            rc.attack(patternTile.getMapLocation(), patternTile.getMark().isSecondary());
                        }
                    }
                }
            }

            // Complete the ruin if we can.
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc)){
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
            }
            else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
            }
        }
        
        RobotInfo[] robotNearby = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo robot: robotNearby) {
            if(robot.getType().isTowerType()) {
                MapLocation targetLoc = robot.getLocation();
                int treshold;
                if(robot.getType() == UnitType.LEVEL_ONE_PAINT_TOWER || robot.getType() == UnitType.LEVEL_ONE_MONEY_TOWER || robot.getType() == UnitType.LEVEL_ONE_DEFENSE_TOWER)
                    treshold = 3500 + UnitType.SPLASHER.moneyCost * rc.getNumberTowers();
                else treshold = 6000 + UnitType.SPLASHER.moneyCost * rc.getNumberTowers();

                // Upgrade tower if can
                if(rc.canUpgradeTower(targetLoc) && rc.getChips() > treshold) {
                    rc.upgradeTower(targetLoc);
                    rc.setTimelineMarker("Tower upgraded at: " + targetLoc, 255, 255, 0);
                    break;
                }
            }
        }
    }

    // Objective: kill oponent's robots and give paint (to robots other than soldiers)
    public static void mopperObjective(RobotController rc) throws GameActionException {
        if(!rc.isActionReady()) return;

        RobotInfo[] nearbyEnemy = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo target = null;
        int targetDist = Integer.MAX_VALUE;
        for(RobotInfo robot : nearbyEnemy) {
            int dist = rc.getLocation().distanceSquaredTo(robot.getLocation());
            if(dist < targetDist) {
                target = robot;
                targetDist = dist;
            }
        }
        if(target != null) {
            MapLocation targetLoc = target.getLocation();
            if(rc.getLocation().distanceSquaredTo(targetLoc) > 2) {
                forceMove(targetLoc, rc);
            }

            Direction targetDir = rc.getLocation().directionTo(targetLoc);
            if(rc.canMopSwing(targetDir) && rc.getLocation().distanceSquaredTo(targetLoc) < 2) {
                rc.mopSwing(targetDir);
            }
        }

        RobotInfo[] nearbyFriend = rc.senseNearbyRobots(-1, rc.getTeam());
        target = null;
        targetDist = Integer.MAX_VALUE;
        for(RobotInfo robot : nearbyFriend) {
            if(robot.getType() == UnitType.MOPPER || robot.getType() == UnitType.SOLDIER || robot.getType() == UnitType.LEVEL_ONE_PAINT_TOWER || robot.getType() == UnitType.LEVEL_TWO_PAINT_TOWER || robot.getType() == UnitType.LEVEL_THREE_PAINT_TOWER)
                continue;
            int dist = rc.getLocation().distanceSquaredTo(robot.getLocation());
            if(dist < targetDist) {
                target = robot;
                targetDist = dist;
            }
        }
        if(target != null) {
            MapLocation targetLoc = target.getLocation();
            if(rc.getLocation().distanceSquaredTo(targetLoc) > 2) {
                forceMove(targetLoc, rc);
            }

            int giveAmount = rc.getPaint();
            if(target.getType().paintCapacity - target.getPaintAmount() < giveAmount)
                giveAmount = target.getType().paintCapacity - target.getPaintAmount();

            if(rc.canTransferPaint(targetLoc, giveAmount)) {
                rc.transferPaint(targetLoc, giveAmount);
            }
        }
    }

    // Objective: paint as much enemy/empty tiles as possible
    public static void splasherObjective(RobotController rc) throws GameActionException {
        if(!rc.isActionReady() && !rc.isMovementReady()) return;

        if(rc.isMovementReady()) {
            Direction dir = bestDirection(rc);
            MapLocation loc = rc.getLocation().add(dir);
            forceMove(loc, rc);
        }

        Direction dir = bestDirection(rc);
        MapLocation bestLoc;
        if(dir == Direction.NORTH || dir == Direction.EAST || dir == Direction.SOUTH || dir == Direction.WEST)
            bestLoc = rc.getLocation().add(dir).add(dir);
        else bestLoc = rc.getLocation().add(dir);

        if(bestLoc != null && rc.canAttack(bestLoc)) {
            rc.attack(bestLoc, rc.senseMapInfo(bestLoc).getPaint().isSecondary());
        }
    }

    // Check if tile is tower or not
    public static boolean isTower(MapInfo tile, RobotController rc) throws GameActionException{
        MapLocation loc = tile.getMapLocation();
        if (rc.canSenseRobotAtLocation(loc)) {
            return rc.senseRobotAtLocation(loc).getType().isTowerType();
        }
        return false;
    }

    // Check if ruin's pattern completed
    public static boolean isPatternFull(MapInfo tile, RobotController rc) throws GameActionException{
        MapLocation loc = tile.getMapLocation();
        MapInfo[] patternArea = rc.senseNearbyMapInfos(loc, 8);
        boolean markExist = false;
        for (MapInfo t : patternArea) {
            if(t.getMark() == PaintType.EMPTY) continue;
            else markExist = true;
            if(!(t.getMark() == t.getPaint() || t.getPaint().isEnemy())) return false;
        }
        return markExist;
    }

    public static void getPaint(RobotController rc) throws GameActionException{
        if(rc.getPaint() <= rc.getType().paintCapacity / 2 + rc.getType().attackCost) {
            MapInfo[] nearbyTile = rc.senseNearbyMapInfos(8);
            MapInfo towerTile = null;
            for(MapInfo tile : nearbyTile) {
                if(isTower(tile, rc)) {
                    UnitType towerType = rc.senseRobotAtLocation(tile.getMapLocation()).getType();
                    if(towerType == UnitType.LEVEL_ONE_PAINT_TOWER || towerType == UnitType.LEVEL_TWO_PAINT_TOWER || towerType == UnitType.LEVEL_THREE_PAINT_TOWER) {
                        towerTile = tile;
                        break;
                    }
                }
            }
            if(towerTile != null) {
                MapLocation towerLoc = towerTile.getMapLocation(); 
                int paintNeeded = rc.getType().paintCapacity - rc.getPaint();
                if(paintNeeded > rc.senseRobotAtLocation(towerLoc).paintAmount)
                    paintNeeded = rc.senseRobotAtLocation(towerLoc).paintAmount;
                if(rc.canTransferPaint(towerLoc, -paintNeeded)) {
                    rc.transferPaint(towerLoc, -paintNeeded);
                }
            }
        }
    }

    public static void givePaint(RobotController rc) throws GameActionException {
        if(!rc.isMovementReady() && !rc.isActionReady()) return;

        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo target = null;
        int targetDist = Integer.MAX_VALUE;
        for(RobotInfo robot : nearbyRobots) {
            if(robot.getType() == UnitType.SOLDIER) continue;

            int missingPaint = robot.getType().paintCapacity - robot.getPaintAmount();
            if(missingPaint <= 0) continue;

            int dist = rc.getLocation().distanceSquaredTo(robot.getLocation());
            if(dist < targetDist) {
                target = robot;
                targetDist = dist;
            }
        }

        if(target != null) {
            MapLocation targetLoc = target.getLocation();
            if(rc.isMovementReady() && rc.getLocation().distanceSquaredTo(targetLoc) > 2) {
                forceMove(targetLoc, rc);
            }

            int availableToGive = rc.getPaint();
            int giveAmount = target.getType().paintCapacity - target.getPaintAmount();
            if(giveAmount > availableToGive) giveAmount = availableToGive;

            if(giveAmount > 0 && rc.canTransferPaint(targetLoc, giveAmount)) {
                rc.transferPaint(targetLoc, giveAmount);
            }
        }
    }

    // Find best direction for each robots
    public static Direction bestDirection(RobotController rc) throws GameActionException{
        int[] paintScore = new int[8];
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        for(MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if(tile.isWall()) continue;
            Direction dirToTile = rc.getLocation().directionTo(loc);
            if(dirToTile == Direction.CENTER) continue;
            int index = dirToTile.ordinal();

            if(rc.getType() == UnitType.SOLDIER || (rc.getType().isTowerType() && robotToBuild == UnitType.SOLDIER)) {
                if(tile.getPaint() == PaintType.EMPTY) paintScore[index] += 100;
                else if(rc.canSenseRobotAtLocation(loc)) {
                    paintScore[index] -= 200;
                } 
                else if(tile.getPaint().isAlly()) paintScore[index] += 10;
                else if (tile.getPaint().isEnemy()) paintScore[index] += 5;
            }
            if(rc.getType() == UnitType.SPLASHER || rc.getType() == UnitType.MOPPER || (rc.getType().isTowerType() && robotToBuild == UnitType.MOPPER) || (rc.getType().isTowerType() && robotToBuild == UnitType.SPLASHER)) {
                if(rc.canSenseRobotAtLocation(loc)) {
                    if(rc.senseRobotAtLocation(loc).getTeam() == rc.getTeam().opponent())
                        paintScore[index] += 100;
                }
                if(tile.getPaint().isEnemy()) paintScore[index] += 100;
                else if(rc.canSenseRobotAtLocation(loc)) {
                    paintScore[index] -= 200;
                } 
                else if(tile.getPaint() == PaintType.EMPTY) paintScore[index] += 10;
                else if (tile.getPaint().isAlly()) paintScore[index] += 5;
            }
        }

        int bestIndex = 0;
        Direction lastDir = Direction.CENTER;
        if(lastLoc != null) lastDir = rc.getLocation().directionTo(lastLoc);
        if(lastDir == directions[bestIndex]) bestIndex++;
        MapLocation bestLoc = rc.getLocation().add(directions[bestIndex]);
        for(int i = bestIndex + 1; i < 8; i++) {
            if(paintScore[i] >= paintScore[bestIndex] && lastDir != directions[i] && (rc.canMove(directions[i]) || rc.canBuildRobot(robotToBuild, bestLoc))) {
                bestIndex = i;
                bestLoc = rc.getLocation().add(directions[bestIndex]);
            }
        }
        return directions[bestIndex];
    }

    // Ensure robot movement each turn (except when the robot's surrounded)
    public static void forceMove(MapLocation loc, RobotController rc) throws GameActionException {
        if(!rc.isMovementReady()) return;
        Direction dir = rc.getLocation().directionTo(loc);
        for(int i = 0; i < 8 && !rc.canMove(dir); i++) {
            dir = dir.rotateRight();
        } 
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }
}
