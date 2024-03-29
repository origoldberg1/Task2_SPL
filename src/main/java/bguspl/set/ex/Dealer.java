package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    final int tableSize;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;


     /**
     * The list of card ids that are left both in the dealer's deck and on the table.
     */   
    private final List<Integer> cardsInDeckAndTable = new ArrayList<>();

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate = false;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * slots from which cards should be removed
     */
    private Vector<Integer> slotsToRemove = new Vector<>();

    /**
     * queue of players that have sets to check
     */
    private BlockingQueue<Player> playersToCheck;

    /**
     * true iff the dealer needs to reshuffle 
     */
    private boolean dealerShouldReshuffle = true; 
    
    /**
     * useful finals 
     */
    final int TEN_MILI_SEC = 10;
    final int ONE_SECOND = 1000;
    final int HALF_SECOND = 500;
    final int ILLEGAL = -1;

    /**
     * for synchronizing
     */
    final Object waitOnObject = new Object();

    /**
     * indicator for when dealer is reshuffling
     */
    public volatile boolean dealerIsReshuffling = true;

    
    /**
     * the time in which the last action has occured, used for if TurnTimeoutSeconds=0
     */
    private long lastAction;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.playersToCheck = new ArrayBlockingQueue<>(players.length);
        cardsInDeckAndTable.addAll(deck);
        this.tableSize = env.config.tableSize;
        lastAction = env.config.turnTimeoutMillis;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            new Thread(player).start();
            synchronized(player.waitForAi){
                if(!player.aiStarted){
                    try {
                        player.waitForAi.wait();
                    } catch (InterruptedException e) {}
                }
            }
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            dealerIsReshuffling=false;
            dealerShouldReshuffle=false;
            updateTimerDisplay(true);
            timerLoop();
            checkPlayersSets();
            dealerIsReshuffling=true;
            lastAction = System.currentTimeMillis();
            removeAllCardsFromTable();
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        if(!terminate){
            announceWinners();
            try {
                Thread.sleep(env.config.endGamePauseMillies);
            } catch (Exception e) {}
            terminate();
        }
    }
    
    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while(!dealerShouldReshuffle && !shouldFinish()){
            sleepUntilWokenOrTimeout();
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized(waitOnObject){
            if(System.currentTimeMillis() + env.config.turnTimeoutWarningMillis >= reshuffleTime){
                try {
                    waitOnObject.wait(TEN_MILI_SEC);
                } catch (InterruptedException e) {}
            }else{
                try {
                    waitOnObject.wait(HALF_SECOND);
                } catch (InterruptedException e) {}
            }
            checkPlayersSets();
        }
        dealerShouldReshuffle = System.currentTimeMillis() >= reshuffleTime;
        updateTimerDisplay(dealerShouldReshuffle);
    }

    /**
     * Called when the game should be terminated.
     */
    public synchronized void terminate() {
        try {
            env.ui.dispose();
        } catch (Exception e) {}
        //join the players' threads in reverse order
        for (int i = players.length - 1; i >= 0; i--) { 
            players[i].terminate();
            try {
                players[i].getPlayerThread().interrupt();
                players[i].getPlayerThread().join();
            } catch (InterruptedException e) {};
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(cardsInDeckAndTable, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        while (slotsToRemove.size() != 0){
            int slot=slotsToRemove.remove(0);
            table.removeCard(slot);
            for(int i = 0; i<players.length; i++){
                players[i].getChosenSlots().remove(slot); 
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Collections.shuffle(deck);
        if(table.countCards() == 0){
            placeAllSlots();
        }
        else{
            int slot;
            while(!deck.isEmpty() && table.countCards() < tableSize){
                slot = findEmptySlot();
                if(slot != ILLEGAL){ 
                    table.placeCard(deck.remove(0), slot);
                }
            }
        }
    }

    private void placeAllSlots(){
        Vector<Integer> allSlots = new Vector<Integer>();
        for (int i = 0; i < tableSize; i++) {
            allSlots.add(i);
        }
        Collections.shuffle(allSlots);
        for (Integer slot : allSlots) {
            if(!deck.isEmpty()){
                table.placeCard(deck.remove(0), slot);
            }
        }
    }

    private int findEmptySlot(){
        for (int i = 0; i < table.getSlotToCard().length; i++) {
            if(table.getSlotToCard()[i] == null){
                return i;
            }
        }
        return ILLEGAL;
    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(env.config.turnTimeoutMillis > 0){
            if(reset){
                reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis(); 
                env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            }
            else{
                long timeLeft = reshuffleTime - System.currentTimeMillis();
                boolean warn = timeLeft < env.config.turnTimeoutWarningMillis;
                env.ui.setCountdown(timeLeft, warn);
            }
        }
        else{
            if(env.config.turnTimeoutMillis == 0){
                if(reset){
                    lastAction = System.currentTimeMillis();
                }
                env.ui.setElapsed(System.currentTimeMillis() - lastAction);
            }
            if(table.hasNoSetOnTable()){
                dealerShouldReshuffle = true;
            }
        } 
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int i = 0; i < tableSize; i++) {
            if(table.getSlotToCard()[i] != null){
                deck.add(table.getSlotToCard()[i]);
            }
            if(!slotsToRemove.contains(i)){
                slotsToRemove.add(i);
            }
        }
        Collections.shuffle(slotsToRemove);
        removeCardsFromTable();

        for (Player player : players) {
            player.getChosenSlots().clear();
        }

        dealerShouldReshuffle=false;
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        if(players.length == 0) {return;} 
        List<Player> winners = new LinkedList<>();
        winners.add(players[0]);
        for (int i = 1; i < players.length; i++) {            
            if(winners.get(0).score() == players[i].score()){
                winners.add(players[i]);
            }
            else if(winners.get(0).score() < players[i].score()){
                winners.clear();
                winners.add(players[i]);
            }
        }
        env.ui.announceWinner(playerListToIdArr(winners));
    }

    /**
     * 
     * @param lst - a list of Players
     * @return an Arr of the player's ids
     */
    public int[] playerListToIdArr(List<Player> lst){
        int[] res = new int[lst.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = lst.get(i).id;
        }
        return res;
    }

    public void addPlayerToCheck(Player player){
        try {
            playersToCheck.put(player);
        } catch (InterruptedException e) {}
        synchronized(waitOnObject){
            waitOnObject.notifyAll();
        }
    }

    public void checkPlayersSets(){
        Player curPlayer;
        int[] curSet;
        while(!playersToCheck.isEmpty()){
            try {
                curPlayer = playersToCheck.take();
                curSet = curPlayer.getChosenSlots().convertToSet();

                if (curSet == null) {
                    curPlayer.keyPressed(Player.CONTINUEPLAY_MSG);
                } else if(testSet(curSet)){
                    removeCardsFromTable();
                    placeCardsOnTable();
                    curPlayer.keyPressed(Player.POINT_MSG);
                    updateTimerDisplay(true);
                    for (Integer card : curSet) {
                        cardsInDeckAndTable.remove(card);
                    }
                } else{
                    curPlayer.keyPressed(Player.PENALTY_MSG);;
                }
            } catch (InterruptedException e) {}
        }
    }


    public boolean testSet(int[]cards){
        if(env.util.testSet(cards)){
            for (int i = 0; i < cards.length; i++) {
                slotsToRemove.add(table.getCardToSlot()[cards[i]]);
            }
            return true;
        }
       return false;
    }

}
