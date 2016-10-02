import java.util.Comparator;
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
import com.orbischallenge.ctz.objects.enums.UnitCallSign;
import com.orbischallenge.ctz.objects.enums.WeaponType;
import com.orbischallenge.game.engine.Point;

public class PlayerAI {

	private static class Objective {

		public Objective(Type t, Point p) {
			type = t;
			location = p;
		}

		enum Type {
			PICKUP, CAPTURE, SHOOT;
		}

		Type type;
		Point location;
		UnitCallSign target;
	}

	final static double DANGER_VAL = 10.0;
	final static double CAUTION_VAL = 5.0;

	public static final int MAX_NUM_TEAM_MEMBERS = 4;

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
    public void doMove(World world, EnemyUnit[] enemy_units, FriendlyUnit[] friendly_units) {
		Integer[] pickup_indexes = assignOnePointToEach(getLocationsOf(world.getPickups()), friendly_units, world);
		for (int iunit = 0; iunit < friendly_units.length; ++iunit) {
			FriendlyUnit me = friendly_units[iunit];
			Integer pickup_index = pickup_indexes[iunit];
			if (pickup_index == null) { continue; }

			Pickup p = world.getPickups()[pickup_index];
			if (p.getPosition().equals(me.getPosition())) {
				me.pickupItemAtPosition();
			} else {
				me.move(p.getPosition());
			}
		}
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
	        		int x = position.getX();
	        		int y = position.getY();

	        		boolean can_be_dangerous_next_turn = false;
	        		if (world.canShooterShootTarget(new Point(x-1, y-1), point, range)) {
	        			can_be_dangerous_next_turn = true;
	        		}
	        		else if (world.canShooterShootTarget(new Point(x-1, y), point, range)) {
	        			can_be_dangerous_next_turn = true;
	        		}
	        		else if (world.canShooterShootTarget(new Point(x-1, y+1), point, range)) {
	        			can_be_dangerous_next_turn = true;
	        		}
	        		else if (world.canShooterShootTarget(new Point(x, y+1), point, range)) {
	        			can_be_dangerous_next_turn = true;
	        		}
	        		else if (world.canShooterShootTarget(new Point(x+1, y+1), point, range)) {
	        			can_be_dangerous_next_turn = true;
	        		}
	        		else if (world.canShooterShootTarget(new Point(x+1, y), point, range)) {
	        			can_be_dangerous_next_turn = true;
	        		}
	        		else if (world.canShooterShootTarget(new Point(x+1, y-1), point, range)) {
	        			can_be_dangerous_next_turn = true;
	        		}
	        		else if (world.canShooterShootTarget(new Point(x, y-1), point, range)) {
	        			can_be_dangerous_next_turn = true;
	        		}


	        		if (can_be_dangerous_next_turn) {
	        			safety = Math.max(safety, CAUTION_VAL);
	        		}
    			}
    		}
    	}

    	return safety;
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
				total_distance += world.getPathLength(units[iunit].getPosition(), points[point_index]);
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

    public static int[] getPathingDistancesTo(Point src, Point[] points, World world) {
		int distances[] = new int[points.length];

		for (int ipoint = 0; ipoint < points.length; ++ipoint) {
			distances[ipoint] = world.getPathLength(src, points[ipoint]);
		}

		return distances;
    }
}
