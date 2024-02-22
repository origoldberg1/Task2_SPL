package bguspl.set.ex;

import java.util.Vector;

import bguspl.set.Env;

import java.util.concurrent.ArrayBlockingQueue;

import java.util.concurrent.BlockingQueue;


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
     * we add it
     * holds until when the player should be freezed
     * intialized by the cuurent time of creating the player in constructor -1 
     */
    private volatile long freezeUntil;
    
    /**
     * The current score of the player.
     */
    private int score;

    /** 
     * we add it
     * Queue for incoming actions
     * size <=3
     */
    private BlockingQueue<Integer> incomingActions;

    /**
     * an object containg a vector of the player's current chosen slots
     */
     private ChosenSlots chosenSlots;
   
     /*
      * the dealer of the game
      */
     private Dealer dealer;

     private Object lockForPlayer;
     
     final int ONE_SECOND = 1000;
     
     public static final int PENALTY_MSG = -1;
     
     public static final int POINT_MSG = -2;
     
     public static final int CONTINUEPLAY_MSG = -3;
     
     final int featureSize;

     final int tableSize;

     private boolean inCheckByDealer;

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
        this.incomingActions = new ArrayBlockingQueue<>(env.config.featureSize);
        freezeUntil= System.currentTimeMillis()-1;
        this.lockForPlayer = new Object();
        this.chosenSlots = new ChosenSlots(table, env);
        this.inCheckByDealer = false;
        this.featureSize = env.config.featureSize;
        this.tableSize = env.config.tableSize;

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
            try{
                Integer action=incomingActions.take(); //wait until the queue isn't empty
                if (action == PENALTY_MSG) {
                    penalty();
                    incomingActions.clear();
                    inCheckByDealer = false;
                } 
                else if (action == POINT_MSG) {
                    point();
                    incomingActions.clear();
                    inCheckByDealer = false;
                } else if (action == CONTINUEPLAY_MSG) {
                    inCheckByDealer = false;
                }
                else if (!inCheckByDealer) {
                    if (chosenSlots.contains(action)){ //we need to remove token
                        table.removeToken(id, action);
                        chosenSlots.remove(action); 
                    }
                    else{  //we need to place token
                        if(chosenSlots.size() != featureSize){
                            if(table.slotToCard[action] != null){
                                table.placeToken(id, action);
                                chosenSlots.add(action); //place the theSlot from the array
                                if (chosenSlots.size() == featureSize){
                                    inCheckByDealer = true;
                                    incomingActions.clear();   
                                    dealer.addPlayerToCheck(this); // calling the dealer to check its slots
                                }
                            }
                        }
                    }
                }             
            }
            catch (InterruptedException ignored){}
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
                keyPressed((int) (Math.random() * tableSize));
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) { 
        //TODO implement
        if ((inCheckByDealer && slot >= 0) || dealer.dealerChangesCard || dealer.dealerIsReshuffle) {return;}
        try { 
            incomingActions.put(slot);//when the queue is full the thread will wait
        } catch(InterruptedException ignored){}
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
         int ignored = table.countCards(); // this part is just for demonstration in the unit tests
         freezeUntil=System.currentTimeMillis()+env.config.pointFreezeMillis; //the player is blocked for input, see keyPresses method
         env.ui.setFreeze(id, env.config.pointFreezeMillis);
         int cnt = 0;
         while (System.currentTimeMillis() <= freezeUntil && !terminate) {
             cnt ++;
             try {
                 Thread.sleep(ONE_SECOND);
             } catch (Exception e) {}
             env.ui.setFreeze(id, env.config.pointFreezeMillis - ONE_SECOND*cnt);            
         }
        env.ui.setScore(id, ++score);
    }        

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        freezeUntil=System.currentTimeMillis()+env.config.penaltyFreezeMillis; //the player is blocked for input, see keyPresses method
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
        int cnt = 0;
        while (System.currentTimeMillis() <= freezeUntil && !terminate) {
            cnt ++;
            try {
                Thread.sleep(ONE_SECOND);
            } catch (Exception e) {}
            env.ui.setFreeze(id, env.config.penaltyFreezeMillis - ONE_SECOND * cnt);            
        }
    }

    public int score() {
        return score;
    }

     public void setPlayerThread(Thread playerThread) {
        this.playerThread = playerThread;
    }

    public Thread getPlayerThread() {
        return this.playerThread;
    }
    
    public void StartPlayerThread(){
        playerThread=new Thread(this);
        playerThread.start();
    }

    public Object getLockForPlayer(){
        return lockForPlayer;
    }

    public ChosenSlots getChosenSlots(){
        return chosenSlots;
    }
}

