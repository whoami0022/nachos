package nachos.threads;

import nachos.machine.*;
import java.util.*;

public class GameMatch{

    private static int totalNumber = 0;

    public static final int abilityBeginner = 1;
    public static final int abilityIntermediate = 2;
    public static final int abilityExpert = 3;

    private static Lock[] matchLock = new Lock[4];
    private static Lock numberLock = new Lock();
    private static Condition[] waitingToMatch = new Condition[4];
    // current players matching.
    private static int players = 0;
    // current waiters in three levels.
    private static int[] waiters = new int[4];
    private static int matchNumber = 0;
    private static List<List<Map<String, Integer>>> matchList = new ArrayList<>();

    public GameMatch(int numPlayersInMatch){
        Lib.assertTrue(numPlayersInMatch >= 0);
        totalNumber = numPlayersInMatch;

        // init conditions
        for(int i = 0; i < 4; i ++){
            matchLock[i] = new Lock();
            waitingToMatch[i] = new Condition(matchLock[i]);
            matchList.add(new ArrayList<>());
        }
        
    }

    // join a match
	public int play(int ability){

        // System.out.println(KThread.currentThread() + " calls play");
        if (!validAbility(ability)){
            return -1;
        }
        matchLock[ability].acquire();
        waiters[ability]++;
        
        List<Map<String, Integer>> curList = matchList.get(ability);
        
        Map<String, Integer> curTuple = new HashMap<>();
        curTuple.put(KThread.currentThread().getName(), 0);
        curList.add(curTuple);
        // System.out.println(curList);
        
        if(waiters[ability] == totalNumber){
            // match complete, start game.
            numberLock.acquire();
        	matchNumber ++;
            // System.out.println(matchNumber);
        	int curSize = matchList.get(ability).size();
        	// System.out.println(curSize);
        	for (int k = curSize - 1; k >= curSize - totalNumber; k--) {
        		Map<String, Integer> curMap = curList.get(k);
        		for (String cur : curMap.keySet()) {	
        			curMap.put(cur, matchNumber);
        		}
        	}	
            numberLock.release();
            // System.out.println(matchList.get(ability));
            waiters[ability] = 0;
            // wake all
            waitingToMatch[ability].wakeAll();
            
            matchLock[ability].release();
        }else{
            // System.out.println(KThread.currentThread() + " is waiting");
            waitingToMatch[ability].sleep();
            // System.out.println(KThread.currentThread() + " is waked");
            matchLock[ability].release();
        }
        for (int i = 0; i < curList.size(); i++) {
        	if (curList.get(i).containsKey(KThread.currentThread().getName())) {
        		return curList.get(i).get(KThread.currentThread().getName());
        	}
        }
        return -1;
    }

    private boolean validAbility(int ability){
        if(ability != abilityBeginner && ability != abilityIntermediate && ability != abilityExpert){
            return false;
        }
        return true;
    }


     // Place GameMatch test code inside of the GameMatch class.

    public static void matchTest4 () {
        final GameMatch match = new GameMatch(2);

        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg1 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
            });
        beg1.setName("B1");

        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
            });
        beg2.setName("B2");

        KThread int1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                System.out.println("int1 returns");
                Lib.assertNotReached("int1 should not have matched!");
            }
            });
        int1.setName("I1");

        KThread exp1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                System.out.println("exp1 returns");
                Lib.assertNotReached("exp1 should not have matched!");
            }
            });
        exp1.setName("E1");

        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        exp1.fork();
        int1.fork();
        
        beg2.fork();
        // Assume join is not implemented, use yield to allow other
        // threads to run
        beg1.join();
        //for (int i = 0; i < 10; i++) {
          //  KThread.currentThread().yield();
        //}
    }
    
    public static void selfTest() {
        matchTest4();
    }

}