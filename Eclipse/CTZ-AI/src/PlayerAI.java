import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

import com.orbischallenge.ctz.objects.ControlPoint;
import com.orbischallenge.ctz.Constants;
import com.orbischallenge.ctz.objects.EnemyUnit;
import com.orbischallenge.ctz.objects.FriendlyUnit;
import com.orbischallenge.ctz.objects.Pickup;
import com.orbischallenge.ctz.objects.UnitClient;
import com.orbischallenge.ctz.objects.World;
import com.orbischallenge.ctz.objects.enums.Direction;
import com.orbischallenge.ctz.objects.enums.Team;
import com.orbischallenge.ctz.objects.enums.UnitCallSign;
import com.orbischallenge.ctz.objects.enums.WeaponType;
import com.orbischallenge.game.engine.Point;

public class PlayerAI {

	private static class Objective {

		public static Objective makePickupObjective(Pickup p) {
			if (p == null) {
				return makeDoNothingObjective();
			} else {
				return new Objective(Type.PICKUP, p.getPosition(), UnitCallSign.ALPHA);
			}
		}

		public static Objective makeCaptureObjective(ControlPoint cp) {
			if (cp == null) {
				return makeDoNothingObjective();
			} else {
				return new Objective(Type.CAPTURE, cp.getPosition(), UnitCallSign.ALPHA);
			}
		}

		public static Objective makeShootObjective(EnemyUnit eu) {
			if (eu == null) {
				return makeDoNothingObjective();
			} else {
				return new Objective(Type.SHOOT, new Point(-1,-1), eu.getCallSign());
			}
		}

		public static Objective makeDoNothingObjective() {
			return new Objective(Type.NONE, new Point(-1,-1), UnitCallSign.ALPHA);
		}

		private Objective(Type t, Point p, UnitCallSign cs) {
			type = t;
			location = p;
			target_call_sign = cs;
		}

		enum Type {
			PICKUP, CAPTURE, SHOOT, NONE;
		}

		final private Type type;
		final private Point location;
		final private UnitCallSign target_call_sign;

		public boolean isDoable(UnitClient me, World world, EnemyUnit[] e_units) {
			switch(type) {
				case PICKUP:
					return world.getPickupAtPosition(location) != null;
				case CAPTURE:
					return world.getNearestControlPoint(location).getControllingTeam() == me.getTeam();
				case SHOOT:
					return findByCallsign(target_call_sign, e_units) != null;
				default:
					return false;
			}
		}

		public Pickup getPickup(World w) {
			if (type == Type.PICKUP) {
				return w.getPickupAtPosition(location);
			} else {
				return null; // assert?
			}
		}

		public ControlPoint getControlPoint(World w) {
			if (type == Type.CAPTURE) {
				return w.getNearestControlPoint(location);
			} else {
				return null; // assert?
			}
		}

		public EnemyUnit getEnemy(EnemyUnit[] eunits) {
			if (type == Type.SHOOT) {
				return findByCallsign(target_call_sign, eunits);
			} else {
				return null; // assert?
			}
		}

		public Type getType() { return type; }

		public boolean isNone() {
			return getType() == Type.NONE;
		}

	}

	final static boolean DEBUG_PRINTS = true;

	final static double DANGER_VAL = 10.0;
	final static double CAUTION_VAL = 5.0;

	public static final int MAX_NUM_TEAM_MEMBERS = 4;

	// state vars
	ObjectiveSet last_objectives = new ObjectiveSet();

    public PlayerAI() {
		//Any initialization code goes here.
    }

	/**
	 * This method will get called every turn.
	 *
	 * @param world The latest state of the world.
	 * @param enemyUnits An array of all 4 units on the enemy team. Their order won't change.
	 * @param friendlyUnits An array of all 4 units on your team. Their order won't change.
	 */
    public void doMove(World world, EnemyUnit[] may_be_dead_enemy_units, FriendlyUnit[] may_be_dead_friendly_units) {
		final Pickup[] all_pickups = world.getPickups();
		final FriendlyUnit[] friendly_units = getAliveUnits(may_be_dead_friendly_units).toArray(new FriendlyUnit[0]);
		final EnemyUnit[] enemy_units = getAliveUnits(may_be_dead_enemy_units).toArray(new EnemyUnit[0]);

		ObjectiveSet objectives_this_turn = new ObjectiveSet(friendly_units); // inits to all NONE

		// prefer by distance.
		// if tie,
		//     prefer pickups over mainframes
		//     over neutral capture points
		//     over enemy capture points
		//     over killing
		// always shoot if in range
		//     unless standing on a more useful weapon?
		ArrayList<Objective> pickup_objectives = makeObjectivesFromPickups(IndexesToObjects(
			assignOnePointToEach(
				getLocationsOf(all_pickups), friendly_units, world
			),
		all_pickups));

		for (int iunit = 0; iunit < friendly_units.length; ++iunit) {
			FriendlyUnit me = friendly_units[iunit];
			Objective pickup_objective = pickup_objectives.get(iunit);
			if (pickup_objective.getType() == Objective.Type.NONE) {
				continue;
			}

			Pickup p = pickup_objective.getPickup(world);

			Point my_pos = me.getPosition();

			if (p.getPosition().equals(my_pos)) {
				objectives_this_turn.setObjective(me, pickup_objective);
				me.pickupItemAtPosition();
			} else {
				Point target = p.getPosition();
				Direction direction = world.getNextDirectionInPath(my_pos, target);

				Point next_point = direction.movePoint(my_pos);

				if (getSquareSafety(next_point, enemy_units, world) <= CAUTION_VAL) {

					objectives_this_turn = moveMeAndSetObjective(me, next_point, pickup_objective, objectives_this_turn);
				}
				else {
					Point rerouted_point = reRoute(my_pos, target, enemy_units, world);

					if (rerouted_point != null) {
						objectives_this_turn = moveMeAndSetObjective(me, rerouted_point, pickup_objective, objectives_this_turn);
					}
				}
			}
		}

    	/* if (ATTACK_MODE) */ {
			for (FriendlyUnit me : friendly_units) {
				for (EnemyUnit enemy : enemy_units) {
					objectives_this_turn = canShootDoShoot(me, enemy, world, objectives_this_turn);
					// TODO do something more intelligent that isn't order dependent...
				}
			}
    	}

    	for (FriendlyUnit me : friendly_units) {
    		if (objectives_this_turn.getObjective(me).isNone()) {
    			// if nothing to do, kill, kill, kill!
    			Point[] enemy_locations = getLocationsOf(enemy_units);
    			Integer closest_index = closestPointDjkstra(me.getPosition(), enemy_locations, world);
    			if (closest_index != null) {
					EnemyUnit target = enemy_units[closest_index];
					objectives_this_turn = canShootDoShoot(me, target, world, objectives_this_turn);
					if (objectives_this_turn.getObjective(me).isNone() == true) {
						// couldn't shoot, so move toward target
						// TODO move in line-of-sight, taking into account weapon ranges.
						objectives_this_turn = moveMeAndSetObjective(me, target.getPosition(), Objective.makeShootObjective(target), objectives_this_turn);
					}
    			}
    		}
    	}

    	// TODO check here if shoot objective is same as last time, and we wanted to move, and we didn't move
    	// it's either a 2 bots trying to got the same square or a bot is in the way. Either case, renegotiate.

    	// TODO resolve team blocking by having storing all requests to move, and arbitrating here (ie. right at the end)

    	// will print, if DEBUG_PRINTS is true, and should be guaranteed to not do so by the "kill, kill, kill" block
    	// other reasons you might not move:
    	//     another robot is in the way (if this mutually happens in a corridor... nothing happens sometimes...)
    	//     another robot (might be on your team!) tried to move to the same place
    	checkForNoneObjectives(objectives_this_turn, friendly_units);

    	// done making moves - save new objectives as the last ones
    	last_objectives = objectives_this_turn;
    }

    // Return a safety value of the provided square/point
    // Lower the value the better.
    // Lowest value returned is 0.0 -> Safe
    // CAUTION_VAL returned -> Enemy can move to a square to be in line of sight in one turn
    // DANGER_VAL returned -> Currently in enemy's line of sight
    static double getSquareSafety(Point point, EnemyUnit[] enemyUnits, World world) {
    	double safety = 0.0;

    	for (EnemyUnit unit : enemyUnits) {
    		WeaponType weapon = unit.getCurrentWeapon();
    		int range = weapon.getRange();

			Point position = unit.getPosition();

			boolean in_range = world.canShooterShootTarget(position, point, range);

			if (in_range) {
				safety = Math.max(safety, DANGER_VAL);
				break;
    		}
    		else {
    			if (safety < CAUTION_VAL) {
					Point[] adjacent_points = getAdjacentPoints(position);

					for (Point p : adjacent_points) {
						if (world.canShooterShootTarget(p, point, range)) {
							// Can be dangerous next turn
							safety = Math.max(safety, CAUTION_VAL);
							break;
						}
					}
    			}
    		}
    	}

    	return safety;
    }

    // Return a safe next move (Point to move to) to advance from src towards dst
    // Return null if there is not safe next move or the best safe move is to standby or move away from the dst
    static Point reRoute(Point src, Point dst, EnemyUnit[] enemy_units, World world) {
		Direction direction = world.getNextDirectionInPath(src, dst);
		Point next_point = direction.movePoint(src);

		int current_distance = getPathLengthWrapper(world, src, dst);
		int optimal_distance = getPathLengthWrapper(world, next_point, dst);

		Point[] adjacent_points = getAdjacentPoints(src);

		int min_distance = current_distance;
		Point rerouted_point = null;
		for (Point p : adjacent_points) {
			if (getSquareSafety(p, enemy_units, world) <= CAUTION_VAL) {
				int distance = getPathLengthWrapper(world, p, dst);
				if (distance < min_distance) {
					min_distance = distance;
					rerouted_point = p;

					if (distance == optimal_distance) {
						break;
					}
				}
			}
		}

		return rerouted_point;
    }

    static Point[] getAdjacentPoints(Point point) {
    	int x = point.getX();
    	int y = point.getY();

    	Point[] adjacent_points = new Point[8];

    	adjacent_points[0] = new Point(x-1, y-1);
    	adjacent_points[1] = new Point(x-1, y);
    	adjacent_points[2] = new Point(x-1, y+1);
    	adjacent_points[3] = new Point(x, y+1);
    	adjacent_points[4] = new Point(x+1, y+1);
    	adjacent_points[5] = new Point(x+1, y);
    	adjacent_points[6] = new Point(x+1, y-1);
    	adjacent_points[7] = new Point(x, y-1);

    	return adjacent_points;
    }

    static int getPathLengthWrapper(World world, Point start, Point end) {
    	if (start.equals(end)) {
    		return 0;
    	}

    	int distance = world.getPathLength(start, end);

    	if (distance == 0) { // world.getPathLength returns 0 is path doesn't exist
    		distance = Integer.MAX_VALUE;
    	}

    	return distance;
    }

    static boolean hasGoodWeapon(UnitClient unit) {
    	return unit.getCurrentWeapon() != WeaponType.MINI_BLASTER;
    }

    static Integer[] assignOnePointToEach(Point[] points, UnitClient[] units, World world) {
    	Integer[][] wanted_points = new Integer[units.length][units.length]; // index 0 is closest, 1 is farther, etc.

		for (int iunit = 0; iunit < units.length; ++iunit) {
			UnitClient u = units[iunit];
			final int distances[] = getPathingDistancesTo(u.getPosition(), points, world);

			PriorityQueue<Integer> best_n = new PriorityQueue<Integer>(units.length, new Comparator<Integer>() {
				@Override
				public int compare(Integer o1, Integer o2) {
					if (distances[o1] == distances[o2]) {
						return 0;
					} else {
						if (distances[o1] < distances[o2]) {
							return 1;
						} else {
							return -1;
						}
					}
				}
			});

			for (int ipoint = 0; ipoint < points.length; ++ipoint) {
				best_n.add(ipoint);
				if (best_n.size() > units.length) {
					best_n.poll();
				}
			}

			for (int i = 0; i < units.length; ++i) {
				wanted_points[iunit][units.length-i-1] = best_n.poll();
			}
		}


		int lowest_total_distance = Integer.MAX_VALUE;
		int best_index = 0;

		int comb_index_limit = 1;
		for (int i = 0; i < units.length; ++i) {
			comb_index_limit *= units.length;
		}
		for (int comb_index = 0; comb_index < comb_index_limit; ++comb_index) {

			boolean in_use[] = new boolean[points.length];
			int num_nopickups = 0;
			boolean use_conflict = false;
			int total_distance = 0;
			for (int iunit = 0, mod = units.length; iunit < units.length; ++iunit, mod*=units.length) {
				int sub_index = (comb_index%mod)/(mod/units.length);
				Integer point_index = wanted_points[iunit][sub_index];
				// if one is null, then assume it's not there, and count it;
				if (point_index == null) { ++num_nopickups; continue; }

				// check for use conflict
				if (in_use[point_index]) {
					use_conflict = true;
					break;
				} else {
					in_use[point_index] = true;
				}

				// add to total
				total_distance += getPathLengthWrapper(world, units[iunit].getPosition(), points[point_index]);
			}
			if (use_conflict || num_nopickups > (units.length - points.length)) {
				continue;
			}

			if (total_distance < lowest_total_distance) {
				lowest_total_distance = total_distance;
				best_index = comb_index;
			}
		}

		// put comb at best_comb_index in final_targets
		Integer[] final_targets = new Integer[units.length];
		for (int iunit = 0, mod = units.length; iunit < units.length; ++iunit, mod*=units.length) {
			int sub_index = (best_index%mod)/(mod/units.length);
			Integer point_index = wanted_points[iunit][sub_index];
			final_targets[iunit] = point_index;
		}

		return final_targets;
    }

    public static Point[] getLocationsOf(Pickup[] pickups) {
    	Point[] result = new Point[pickups.length];
    	for (int i = 0; i < pickups.length; ++i) {
    		result[i] = pickups[i].getPosition();
    	}
    	return result;
    }

    public static Point[] getLocationsOf(ControlPoint[] cpoints) {
    	Point[] result = new Point[cpoints.length];
    	for (int i = 0; i < cpoints.length; ++i) {
    		result[i] = cpoints[i].getPosition();
    	}
    	return result;
    }

    public static Point[] getLocationsOf(UnitClient[] units) {
    	Point[] result = new Point[units.length];
    	for (int i = 0; i < units.length; ++i) {
    		result[i] = units[i].getPosition();
    	}
    	return result;
    }

    public static int[] getPathingDistancesTo(Point src, Point[] points, World world) {
		int distances[] = new int[points.length];

		for (int ipoint = 0; ipoint < points.length; ++ipoint) {
			distances[ipoint] = getPathLengthWrapper(world, src, points[ipoint]);
		}

		return distances;
    }

	public static <U extends UnitClient> U findByCallsign(UnitCallSign cs, U[] units) {
		for (int i = 0; i < units.length; ++i) {
			if (units[i].getCallSign() == cs) {
				return units[i];
			}
		}
		return null;
	}

	public static <T extends Object> ArrayList<T> IndexesToObjects(Integer[] indexes, T[] objects) {
		ArrayList<T> result = new ArrayList<>(objects.length);
		for (int i = 0; i < indexes.length; ++i) {
			if (indexes[i] == null) {
				result.add(null);
			} else {
				result.add(objects[indexes[i]]);
			}
		}
		return result;
	}

	public static ArrayList<Objective> makeObjectivesFromPickups(ArrayList<Pickup> pickups) {
		ArrayList<Objective> result = new ArrayList<Objective>(pickups.size());
		for (int i = 0; i < pickups.size(); ++i) {
			result.add(Objective.makePickupObjective(pickups.get(i)));
		}
		return result;
	}

	public static <U extends UnitClient> ArrayList<U> getAliveUnits(U[] units) {
		ArrayList<U> result = new ArrayList<U>(units.length);
		for (int i = 0; i < units.length; ++i) {
			if (units[i].getHealth() > 0) {
				result.add(units[i]);
			}
		}

		return result;
	}

	private static class ObjectiveSet {
		EnumMap<UnitCallSign, Objective> objectives = new EnumMap<UnitCallSign, Objective>(UnitCallSign.class);


		public ObjectiveSet() { }
		public ObjectiveSet(FriendlyUnit[] funits) {
			for (int i = 0; i < funits.length; ++i) {
				setObjective(funits[i], Objective.makeDoNothingObjective());
			}
		}

		public Objective getObjective(UnitClient uc) { return getObjective(uc.getCallSign()); }
		public Objective getObjective(UnitCallSign ucs) {
			Objective result = objectives.get(ucs);
			if (result == null) {
				return Objective.makeDoNothingObjective();
			} else {
				return result;
			}
		}

		public Objective setObjective(UnitClient uc, Objective obj) { return setObjective(uc.getCallSign(), obj); }
		public Objective setObjective(UnitCallSign ucs, Objective obj) {
			return objectives.put(ucs, obj);
		}

		public void clear() { objectives.clear(); }

		public void resetTo(UnitClient[] units, Objective[] new_objectives) {
			clear();
			for (int i = 0; i < units.length; ++i) {
				setObjective(units[i], new_objectives[i]);
			}

		}
	}

	private static boolean canXShootY(UnitClient friendly, UnitClient enemy, World w) {
		return w.canShooterShootTarget(friendly.getPosition(), enemy.getPosition(), friendly.getCurrentWeapon().getRange());
	}

	private static ObjectiveSet moveMeAndSetObjective(FriendlyUnit me, Point p, Objective obj, ObjectiveSet objectives) {
		objectives.setObjective(me, obj);
		me.move(p);
		return objectives;
	}

	private static ObjectiveSet moveMeAndSetObjective(FriendlyUnit me, Direction d, Objective obj, ObjectiveSet objectives) {
		objectives.setObjective(me, obj);
		me.move(d);
		return objectives;
	}

	private static ObjectiveSet canShootDoShoot(FriendlyUnit me, EnemyUnit enemy, World w, ObjectiveSet objectives) {
		boolean can_shoot = canXShootY(me, enemy, w);

		if (can_shoot) {
			objectives.setObjective(me, Objective.makeShootObjective(enemy));
			me.shootAt(enemy);
		}
		return objectives;
	}

	private static Integer closestPointDjkstra(Point me, Point[] points, World w) {
		int min_dist = Integer.MAX_VALUE;
		Integer best = null;
		for (int i = 0; i < points.length; ++i) {
			int len = getPathLengthWrapper(w, me, points[i]);
			if (len < min_dist) {
				best = i;
				min_dist = len;
			}
		}
		return best;
	}

	private static boolean checkForNoneObjectives(ObjectiveSet objectives, FriendlyUnit[] f_units) {
		boolean found_none_objective = false;
		for (int i = 0; i < f_units.length; ++i) {
			if (objectives.getObjective(f_units[i]).isNone()) {
				found_none_objective = true;
				if (DEBUG_PRINTS){
					System.out.printf("%s's objective is still NONE!\n", f_units[i].getCallSign().toString());
				}
			}
		}
		return found_none_objective;
	}

}
