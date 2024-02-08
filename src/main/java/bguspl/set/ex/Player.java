package bguspl.set.ex;

import java.util.Vector;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /** 
     * vector that will be manged in FIFO, containg incoming actions
     */

     private Vector<Integer> incomingActions; 
     

    /**
      * number of tokens placed by the player currently
      */

     private int numOfTokens;

     /*
      * the dealer of the game
      */
     private Dealer dealer;

     /*
      * vector containg the current slots in which the player's tokens are placed
      */
     private Vector<Integer> curSlots;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.incomingActions = new Vector<>(3);
        for (int i = 0; i < incomingActions.size(); i++) {
            incomingActions.set(i, -1);
        }
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            while(incomingActions.size() != 0){
                Integer curSlot = incomingActions.get(0);
                if(toRemove(curSlot)){
                    table.removeToken(id, curSlot);
                    curSlots.remove(curSlot); //WARNING: may be a problem and reomve by index instead of by value
                    incomingActions.remove(0);
               }
               else{
                    table.placeToken(id, curSlot);
                    curSlots.add(curSlot);
                    incomingActions.remove(0);
               }
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if(incomingActions.size() < 3){
            incomingActions.add(slot);
            this.notifyAll();
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        //notifyALL

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        //notifyALL
    }

    public int score() {
        return score;
    }

    public Vector<Integer> getIncomingActions() {
        return incomingActions;
    }

    public int getNumOfTokens() {
        return numOfTokens;
    }

    /**
     * called to check if the action needed is removing or placing a token
     * @param slot - the slot corresponding to the key pressed.
     * @return true if we should remove a token from the slot, false if we should place a token on the slot
     */
    public boolean toRemove(int slot){
        return curSlots.contains(slot);
    }
}
