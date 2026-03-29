package alternative_bots_2;
// Alternative Bot 2

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class RobotPlayer {

    static int turnCount = 0;

    static final Direction[] DIRS = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    // Map Related
    static int mapW, mapH, myID, localSpawnCount;
    static MapLocation home, enemyBase, stickyRuin;
    static int laneY = -1;
    static final int LANE_H = 4;

    // SRP Related
    static int knownTowerCount = -1;
    static int lastTowerGainRound = 0;
    static final int SRPDelay = 20;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        home = rc.getLocation();
        mapW = rc.getMapWidth();
        mapH = rc.getMapHeight();
        myID = rc.getID();
        enemyBase = new MapLocation(mapW - 1 - home.x, mapH - 1 - home.y);

        while (true) {
            turnCount++;
            try {
                switch (rc.getType()) {
                    case SOLDIER:
                        runSoldier(rc);
                        break;
                    case MOPPER:
                        runMopper(rc);
                        break;
                    case SPLASHER:
                        runSplasher(rc);
                        break;
                    default:
                        runTower(rc);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void runTower(RobotController rc) throws GameActionException {
        RobotInfo weakest = null;
        for (RobotInfo e : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (!rc.canAttack(e.location))
                continue;
            if (weakest == null || e.health < weakest.health)
                weakest = e;
        }
        if (weakest != null)
            rc.attack(weakest.location);

        if (!rc.isActionReady())
            return;

        if (rc.canUpgradeTower(rc.getLocation())) {
            if ((rc.getRoundNum() > 150 && rc.getChips() > 2200) || (rc.getRoundNum() > 320 && rc.getChips() > 1400)) {
                rc.upgradeTower(rc.getLocation());
                return;
            }
        }

        UnitType want = chooseSpawnType(rc);
        MapLocation spawn = null;
        for (Direction d : DIRS) {
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

    static UnitType chooseSpawnType(RobotController rc) {
        int round = rc.getRoundNum();
        int towers = rc.getNumberTowers();
        int chips = rc.getChips();

        if (round < 80 || towers < 3) {
            return (localSpawnCount % 8 == 7) ? UnitType.MOPPER : UnitType.SOLDIER;
        }

        if (chips < 1800) {
            return (localSpawnCount % 10 == 9) ? UnitType.MOPPER : UnitType.SOLDIER;
        }

        if (chips < 3500) {
            int slot = localSpawnCount % 10;
            if (slot == 8)
                return UnitType.MOPPER;
            if (slot == 9)
                return UnitType.SPLASHER;
            return UnitType.SOLDIER;
        }

        if (chips < 6000) {
            int slot = localSpawnCount % 8;
            if (slot <= 3)
                return UnitType.SPLASHER;
            if (slot == 4)
                return UnitType.MOPPER;
            return UnitType.SOLDIER;
        }

        int slot = localSpawnCount % 10;
        if (slot <= 5)
            return UnitType.SPLASHER;
        if (slot == 6)
            return UnitType.MOPPER;
        return UnitType.SOLDIER;
    }

    static void runSoldier(RobotController rc) throws GameActionException {
        MapLocation here = rc.getLocation();
        updateTowerProgress(rc);

        if (rc.getPaint() < (stickyRuin != null ? 35 : 20)) {
            refill(rc);
            return;
        }

        if (rc.isActionReady() && rc.canAttack(here) && !rc.senseMapInfo(here).getPaint().isAlly()) {
            rc.attack(here);
        }

        if (stickyRuin != null && rc.canSenseRobotAtLocation(stickyRuin)) {
            RobotInfo ri = rc.senseRobotAtLocation(stickyRuin);
            if (ri != null && ri.getType().isTowerType())
                stickyRuin = null;
        }

        if (stickyRuin == null)
            stickyRuin = nearestOpenRuin(rc);

        if (stickyRuin != null) {
            doRuin(rc, stickyRuin);
            return;
        }

        if (rc.getRoundNum() > 300 && rc.getRoundNum() - lastTowerGainRound >= SRPDelay && rc.getNumberTowers() >= 4
                && rc.getPaint() >= 80) {
            MapLocation srp = locateSRP(rc, here);
            if (srp != null) {
                doSRP(rc, srp);
                return;
            }
        }

        MapLocation goal = localExploreTarget(rc);
        if (goal == null)
            goal = laneTarget(rc);
        move(rc, goal);

        if (rc.isActionReady())
            paintBest(rc);
    }

    static void doRuin(RobotController rc, MapLocation ruin) throws GameActionException {
        if (rc.canSenseRobotAtLocation(ruin)) {
            RobotInfo ri = rc.senseRobotAtLocation(ruin);
            if (ri != null && ri.getType().isTowerType()) {
                stickyRuin = null;
                return;
            }
        }

        UnitType towerType = chooseTowerType(rc);

        if (rc.getLocation().distanceSquaredTo(ruin) > 8) {
            move(rc, ruin);
            return;
        }

        boolean hasMarks = false;
        for (MapInfo mi : rc.senseNearbyMapInfos(ruin, 8)) {
            if (mi.getMark() != PaintType.EMPTY) {
                hasMarks = true;
                break;
            }
        }

        if (!hasMarks && rc.canMarkTowerPattern(towerType, ruin)) {
            rc.markTowerPattern(towerType, ruin);
        }

        if (rc.canCompleteTowerPattern(towerType, ruin)) {
            rc.completeTowerPattern(towerType, ruin);
            stickyRuin = null;
            lastTowerGainRound = rc.getRoundNum();
            return;
        }

        MapLocation target = null;
        boolean secondary = false;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo mi : rc.senseNearbyMapInfos(ruin, 8)) {
            if (mi.hasRuin() || mi.isWall() || mi.getMark() == PaintType.EMPTY)
                continue;

            boolean wrongPrimary = mi.getMark() == PaintType.ALLY_PRIMARY && mi.getPaint() != PaintType.ALLY_PRIMARY;
            boolean wrongSecondary = mi.getMark() == PaintType.ALLY_SECONDARY && mi.getPaint() != PaintType.ALLY_SECONDARY;

            if (!wrongPrimary && !wrongSecondary)
                continue;

            int d = rc.getLocation().distanceSquaredTo(mi.getMapLocation());
            if (d < bestDist) {
                bestDist = d;
                target = mi.getMapLocation();
                secondary = wrongSecondary;
            }
        }

        if (target != null) {
            if (rc.canAttack(target))
                rc.attack(target, secondary);
            else
                move(rc, target);
        } else {
            move(rc, ruin);
        }

        if (rc.canCompleteTowerPattern(towerType, ruin)) {
            rc.completeTowerPattern(towerType, ruin);
            stickyRuin = null;
            lastTowerGainRound = rc.getRoundNum();
        }
    }

    static UnitType chooseTowerType(RobotController rc) {
        int towers = rc.getNumberTowers();
        int i = towers % 3;
        if (i == 1 || i == 2)
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once
     * per turn.
     */
    static void runMopper(RobotController rc) throws GameActionException {
        MapLocation here = rc.getLocation();

        if (rc.getPaint() < 20) {
            refill(rc);
            return;
        }

        RobotInfo nearestEnemy = nearestEnemyUnit(rc);
        if (nearestEnemy != null) {
            Direction d = here.directionTo(nearestEnemy.location);
            if (rc.canMopSwing(d)) {
                rc.mopSwing(d);
                return;
            }
            if (rc.canAttack(nearestEnemy.location)) {
                rc.attack(nearestEnemy.location);
                return;
            }
            move(rc, nearestEnemy.location);
            return;
        }

        MapLocation dirty = nearestEnemyPaintLoc(rc);
        if (dirty != null) {
            if (rc.canAttack(dirty))
                rc.attack(dirty);
            else
                move(rc, dirty);
            return;
        }

        move(rc, laneTarget(rc));
    }

    /**
     * Run a single turn for a Splasher.
     * This code is wrapped inside the infinite loop in run(), so it is called once
     * per turn.
     */
    static void runSplasher(RobotController rc) throws GameActionException {
        if (rc.getPaint() < 45) {
            refill(rc);
            return;
        }

        MapLocation best = null;
        int bestScore = 4;

        for (MapInfo t : rc.senseNearbyMapInfos()) {
            MapLocation loc = t.getMapLocation();
            if (!rc.canAttack(loc))
                continue;

            int score = 0;
            for (MapInfo s : rc.senseNearbyMapInfos(loc, 2)) {
                PaintType p = s.getPaint();
                if (p.isEnemy())
                    score += 4;
                else if (p == PaintType.EMPTY)
                    score += 2;
                else
                    score -= 1;
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

        move(rc, enemyBase);
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

    static RobotInfo nearestEnemyUnit(RobotController rc) throws GameActionException {
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo e : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (e.getType().isTowerType())
                continue;
            int d = rc.getLocation().distanceSquaredTo(e.location);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    static MapLocation nearestAllyTower(RobotController rc) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo r : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (!r.getType().isTowerType())
                continue;
            int d = rc.getLocation().distanceSquaredTo(r.location);
            if (d < bestDist) {
                bestDist = d;
                best = r.location;
            }
        }
        return best;
    }

    static MapLocation nearestEnemyPaintLoc(RobotController rc) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo t : rc.senseNearbyMapInfos()) {
            if (!t.getPaint().isEnemy())
                continue;
            int d = rc.getLocation().distanceSquaredTo(t.getMapLocation());
            if (d < bestDist) {
                bestDist = d;
                best = t.getMapLocation();
            }
        }
        return best;
    }

    static void refill(RobotController rc) throws GameActionException {
        MapLocation tower = nearestAllyTower(rc);
        if (tower == null)
            tower = home;

        if (rc.getLocation().distanceSquaredTo(tower) <= 2 && rc.isActionReady()) {
            int need = rc.getType().paintCapacity - rc.getPaint();
            if (need > 0 && rc.canTransferPaint(tower, -need)) {
                rc.transferPaint(tower, -need);
                return;
            }
        }

        move(rc, tower);
    }

    static void paintBest(RobotController rc) throws GameActionException {
        if (!rc.isActionReady() || rc.getPaint() <= 20)
            return;

        MapLocation here = rc.getLocation();
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapInfo t : rc.senseNearbyMapInfos()) {
            MapLocation loc = t.getMapLocation();
            if (!rc.canAttack(loc))
                continue;
            if (t.hasRuin() || t.isWall() || t.getPaint().isAlly())
                continue;

            int score = 0;
            if (t.getPaint().isEnemy())
                score += 30;
            else if (t.getPaint() == PaintType.EMPTY)
                score += 12;
            if (t.getMark() != PaintType.EMPTY)
                score += 8;
            score -= here.distanceSquaredTo(loc);

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        if (best != null)
            rc.attack(best);
    }

    static MapLocation localExploreTarget(RobotController rc) throws GameActionException {
        MapLocation here = rc.getLocation();
        MapLocation bestEnemy = null, bestMarked = null, bestEmpty = null;
        int dEnemy = Integer.MAX_VALUE, dMarked = Integer.MAX_VALUE, dEmpty = Integer.MAX_VALUE;

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            MapLocation loc = tile.getMapLocation();
            if (tile.hasRuin() || tile.isWall() || !tile.isPassable())
                continue;

            int d = here.distanceSquaredTo(loc);

            if (tile.getPaint().isEnemy()) {
                if (d < dEnemy) {
                    dEnemy = d;
                    bestEnemy = loc;
                }
                continue;
            }

            if (tile.getPaint() == PaintType.EMPTY && tile.getMark() != PaintType.EMPTY) {
                if (d < dMarked) {
                    dMarked = d;
                    bestMarked = loc;
                }
                continue;
            }

            if (tile.getPaint() == PaintType.EMPTY && d < dEmpty) {
                dEmpty = d;
                bestEmpty = loc;
            }
        }

        if (bestEnemy != null)
            return bestEnemy;
        if (bestMarked != null)
            return bestMarked;
        return bestEmpty;
    }

    static void updateTowerProgress(RobotController rc) {
        int towers = rc.getNumberTowers();
        if (knownTowerCount == -1) {
            knownTowerCount = towers;
            lastTowerGainRound = rc.getRoundNum();
            return;
        }
        if (towers > knownTowerCount) {
            knownTowerCount = towers;
            lastTowerGainRound = rc.getRoundNum();
        }
    }

    static MapLocation locateSRP(RobotController rc, MapLocation here) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                int x = here.x + dx;
                int y = here.y + dy;

                if (x < 0 || x >= mapW || y < 0 || y >= mapH)
                    continue;
                if (x % 4 != 2 || y % 4 != 2)
                    continue;

                MapLocation c = new MapLocation(x, y);
                if (here.distanceSquaredTo(c) > 20)
                    continue;

                boolean nearRuin = false;
                for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
                    if (c.distanceSquaredTo(ruin) <= 9) {
                        nearRuin = true;
                        break;
                    }
                }
                if (nearRuin)
                    continue;

                int dist = here.distanceSquaredTo(c);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = c;
                }
            }
        }

        return best;
    }

    static void doSRP(RobotController rc, MapLocation srp) throws GameActionException {
        if (rc.getLocation().distanceSquaredTo(srp) > 8) {
            move(rc, srp);
            return;
        }

        if (rc.canMarkResourcePattern(srp)) {
            rc.markResourcePattern(srp);
        }

        if (rc.canCompleteResourcePattern(srp)) {
            rc.completeResourcePattern(srp);
            lastTowerGainRound = rc.getRoundNum();
            return;
        }

        MapLocation target = null;
        boolean secondary = false;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo mi : rc.senseNearbyMapInfos(srp, 8)) {
            if (mi.getMark() == PaintType.EMPTY || mi.isWall() || mi.hasRuin())
                continue;

            boolean wrongPrimary = mi.getMark() == PaintType.ALLY_PRIMARY && mi.getPaint() != PaintType.ALLY_PRIMARY;

            boolean wrongSecondary = mi.getMark() == PaintType.ALLY_SECONDARY
                    && mi.getPaint() != PaintType.ALLY_SECONDARY;

            if (!wrongPrimary && !wrongSecondary)
                continue;

            int dist = rc.getLocation().distanceSquaredTo(mi.getMapLocation());
            if (dist < bestDist) {
                bestDist = dist;
                target = mi.getMapLocation();
                secondary = wrongSecondary;
            }
        }

        if (target != null) {
            if (rc.canAttack(target)) {
                rc.attack(target, secondary);
            } else {
                move(rc, target);
                return;
            }
        }

        if (rc.canCompleteResourcePattern(srp)) {
            rc.completeResourcePattern(srp);
            lastTowerGainRound = rc.getRoundNum();
        }
    }

    static MapLocation laneTarget(RobotController rc) {
        if (laneY == -1) {
            int numLanes = Math.max(1, (mapH + LANE_H - 1) / LANE_H);
            int laneIdx = Math.floorMod(myID, numLanes);
            laneY = Math.min(laneIdx * LANE_H + LANE_H / 2, mapH - 1);
        }

        MapLocation here = rc.getLocation();
        if (Math.abs(here.y - laneY) > 1)
            return new MapLocation(here.x, laneY);

        return new MapLocation(enemyBase.x, laneY);
    }

    static void move(RobotController rc, MapLocation destination) throws GameActionException {
        if (!rc.isMovementReady() || destination == null)
            return;

        MapLocation here = rc.getLocation();
        Direction target = here.directionTo(destination);
        if (target == Direction.CENTER)
            target = DIRS[myID % 8];
        Direction best = null;
        int bestScore = Integer.MAX_VALUE;

        Direction d = target;
        for (int i = 0; i < 8; i++) {
            if (!rc.canMove(d))
            {
                d = d.rotateRight();
                continue;
            }

            MapLocation next = here.add(d);
            int score = next.distanceSquaredTo(destination) * 10;

            PaintType p = rc.senseMapInfo(next).getPaint();
            if (p.isEnemy())
                score += 15;
            else if (p.isAlly())
                score -= 4;

            if (score < bestScore) {
                bestScore = score;
                best = d;
            }

            d = d.rotateRight();
        }

        if (best != null)
            rc.move(best);
    }
}
