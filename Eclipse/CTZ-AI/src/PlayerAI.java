import com.orbischallenge.ctz.Constants;
import com.orbischallenge.ctz.objects.EnemyUnit;
import com.orbischallenge.ctz.objects.FriendlyUnit;
import com.orbischallenge.ctz.objects.World;
import com.orbischallenge.ctz.objects.enums.WeaponType;
import com.orbischallenge.game.engine.Point;


public class PlayerAI {

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
    public void doMove(World world, EnemyUnit[] enemyUnits, FriendlyUnit[] friendlyUnits) {
			;
    }
    
    // Return a safety value of the provided square/point
    // Lower the value the better.
    // Lowest value returned is 0.0
    public static double getSquareSafety(Point point, EnemyUnit[] enemyUnits, World world) {
    	final double DANGER_VAL = 10.0;
    	final double CAUTION_VAL = 5.0;
    	
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
}
