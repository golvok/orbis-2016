import java.util.HashMap;
import java.util.HashSet;

import com.orbischallenge.ctz.Constants;
import com.orbischallenge.ctz.objects.EnemyUnit;
import com.orbischallenge.ctz.objects.FriendlyUnit;
import com.orbischallenge.ctz.objects.Pickup;
import com.orbischallenge.ctz.objects.UnitClient;
import com.orbischallenge.ctz.objects.World;
import com.orbischallenge.ctz.objects.enums.WeaponType;
import com.orbischallenge.game.engine.Point;


public class PlayerAI {
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
		Pickup[] pickup_targets = getClosestPickups(world, friendly_units);
		for (int iunit = 0; iunit < friendly_units.length; ++iunit) {
			FriendlyUnit me = friendly_units[iunit];
			Pickup p = pickup_targets[iunit];
			if (p == null) { continue; }

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
    
    static Pickup[] getClosestPickups(World world, UnitClient[] units) {
    	Pickup[][] pickup_targets = new Pickup[MAX_NUM_TEAM_MEMBERS][2]; // index 0 is primary, 1 is secondary

		for (int iunit = 0; iunit < units.length; ++iunit) {
			UnitClient u = units[iunit];
			int closest_len = Integer.MAX_VALUE;	
			int second_closest_len = Integer.MAX_VALUE;

			for (Pickup p : world.getPickups()) {
				Point pickup_location = p.getPosition();
				int len = world.getPathLength(u.getPosition(), pickup_location);
				if (closest_len > len) {
					second_closest_len = closest_len;
					pickup_targets[iunit][1] = pickup_targets[iunit][0];
					closest_len = len;
					pickup_targets[iunit][0] = p;
				} else {
					if (second_closest_len > len) {
						second_closest_len = len;
						pickup_targets[iunit][1] = p;
					}
				}
			}
		}
		
		
		HashMap<Pickup,Integer> num_want_primary = new HashMap<>();
		for (int i = 0; i < pickup_targets.length; ++i) {
			Pickup this_pickup = pickup_targets[i][0];
			Integer old_value = num_want_primary.get(this_pickup);
			if (old_value == null) {
				old_value = 0;
			}
			num_want_primary.put(this_pickup, old_value + 1);
		}

		Pickup[] final_targets = new Pickup[MAX_NUM_TEAM_MEMBERS];
		HashSet<Pickup> used = new HashSet<>();
		for (int iunit = 0; iunit < units.length; ++iunit) {
			Pickup my_primary = pickup_targets[iunit][0];
			int num_want_my_primary = num_want_primary.get(my_primary);
			if (num_want_my_primary == 1) {
				final_targets[iunit] = my_primary;
			} else {
				final_targets[iunit] = null;
			}
		}
		
		for (int iunit = 0; iunit < units.length; ++iunit) {
			if (final_targets[iunit] != null) { continue; } // skip those with targets already
			Pickup my_secondary = pickup_targets[iunit][1];
			if (used.contains(my_secondary)) {
				final_targets[iunit] = null;
			} else {
				final_targets[iunit] = my_secondary;
			}
		}

		return final_targets;
    }
}
