package bguspl.set.ex;

import bguspl.set.Env;

import static java.lang.String.format;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

//testing 
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

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * slots from which cards should be removed
     */
    private Vector<Integer> slotsToRemove;

    /**
     * queue of players that have sets to check
     */
    private BlockingQueue<Player> playersToCheck;

    public volatile boolean dealerShouldReshuffle; 

    final int NUM_OF_SLOTS = 12;

    final int SIXTEY_SECONDS = 60000;

    final int ONE_SECOND = 1000;

    final int ONE_HUNDRED_MILI_SEC = 100;

    final int THREE = 3;

    final Object waitOnObject = new Object();

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.dealerShouldReshuffle=false;
        this.table = table;
        this.players = players;
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.terminate = false;
        this.slotsToRemove = new Vector<>();
        this.playersToCheck = new ArrayBlockingQueue<>(players.length);
        this.dealerShouldReshuffle =true;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            player.StartPlayerThread();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            dealerShouldReshuffle=false;
            updateTimerDisplay(true);
            timerLoop(); //self mark- should do things until we need to rersheufle 
            checkPlayersSets();
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while(!dealerShouldReshuffle){
            sleepUntilWokenOrTimeout();
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {   
        synchronized(waitOnObject){
            try {
                waitOnObject.wait(ONE_HUNDRED_MILI_SEC);
            } catch (InterruptedException e) {}
            checkPlayersSets();
        }
        dealerShouldReshuffle = System.currentTimeMillis() >= reshuffleTime;
        updateTimerDisplay(dealerShouldReshuffle);
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        env.ui.dispose(); //closes window
        terminate = true;
        for (Player player : players) {
            player.terminate();
        }
        for (Player player : players) {
            try {
                player.getPlayerThread().join();
            } catch (InterruptedException e) {};
        }

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {}
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        while (slotsToRemove.size()!=0){
            int slot=slotsToRemove.removeFirst();
            table.removeCard(slot);
            for(int i=0; i<players.length; i++){
                table.removeToken(players[i].id, slot);
            }   
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        shuffleDeck();
        if(table.countCards() == 0){
            placeTwelveCardsOnTable();
        }
        //else{
            int slot;
            int card;
            while(!deck.isEmpty() && table.countCards() < NUM_OF_SLOTS){
                card = deck.removeFirst();
                slot = findEmptySlot();
                if(findEmptySlot() >= 0){  //the slot is a legal one
                    table.placeCard(card, slot);
                }
            }
        //}
    }
    private void placeTwelveCardsOnTable(){
        Vector<Integer> oneToTwelve = new Vector<Integer>();
        for (int i = 0; i < NUM_OF_SLOTS; i++) {
            oneToTwelve.add(i);
        }
        Collections.shuffle(oneToTwelve);
        for (Integer slot : oneToTwelve) {
            if(!deck.isEmpty()){
                table.placeCard(deck.removeFirst(), slot);
            }
        }
    }

    private int findEmptySlot(){
        for (int i = 0; i < table.slotToCard.length; i++) {
            if(table.slotToCard[i] == null){
                return i;
            }
        }
        return -1;
    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset){
            reshuffleTime = 60000 + System.currentTimeMillis(); 
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
        else{
            long timeLeft = reshuffleTime - System.currentTimeMillis();
            boolean warn = timeLeft < env.config.turnTimeoutWarningMillis;
            env.ui.setCountdown(timeLeft, warn);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        for (int i = 0; i < NUM_OF_SLOTS; i++) {
            // if(table.slotToCard[i] != null){
            //     deck.add(table.slotToCard[i]);
            // }
            if(!slotsToRemove.contains(i)){
                slotsToRemove.add(i);
            }
        }
        Collections.shuffle(slotsToRemove);
        removeCardsFromTable();
        dealerShouldReshuffle=false;
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        if(players.length == 0) {return;} 
        List<Player> winners = new LinkedList<>();
        winners.add(players[0]);
        for (int i = 1; i < players.length; i++) {            
            if(winners.getFirst().score() == players[i].score()){
                winners.add(players[i]);
            }
            else if(winners.getFirst().score() < players[i].score()){
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
        } catch (InterruptedException e) {
            //System.out.println("addPlayerToCheck. should not be reached");
        }
        //dealerThread.interrupt(); //if dealer thread is sleeping we wake him up
        synchronized(waitOnObject){
            //System.out.println("waitOnObject.notifyAll()");
            waitOnObject.notifyAll();
        }
    }

    public void checkPlayersSets(){
        Player curPlayer;
        int[] curSet;
        while(!playersToCheck.isEmpty()){
            try {
                curPlayer = playersToCheck.take();
                synchronized(curPlayer.getSlotsVector()) {
                    curSet = slotsToCards(setVecToArr(curPlayer.getSlotsVector()));
                }
                if(testSet(curSet)){
                    removeCardsFromTable();
                    placeCardsOnTable();
                    curPlayer.keyPressed(Player.POINT_MSG);
                    updateTimerDisplay(true);
                }
                else{
                    curPlayer.keyPressed(Player.PENALTY_MSG);;
                }
            } catch (InterruptedException e) {}
        }
    }

    /**
     * 
     * @param vec - a vector of Integers representing slots, size(vec) = 3
     * @return an arr in size 3 of the slots
     */
    public int[] setVecToArr(Vector<Integer> vec){
        int[] res = new int[THREE];
        for (int i = 0; i < res.length; i++) {
            res[i] = vec.get(i);
        }
        return res;
    }

    public boolean testSet(int[]cards){
       if(env.util.testSet(cards)){
        for (int i = 0; i < cards.length; i++) {
            slotsToRemove.add(table.cardToSlot[cards[i]]);
        }
        return true;
       }
       return false;   
    }

    public int[] slotsToCards(int[]slots){
        int[]cards = new int[slots.length];
        for (int i = 0; i < cards.length; i++) {
            cards[i] = table.slotToCard[slots[i]];
        }
        return cards;
    }

    public int[] cardsToSlots(int[]cards){
        int [] slots = new int[cards.length];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = table.cardToSlot[cards[i]];
        }
        return slots;
    }

    public void shuffleDeck(){
        Collections.shuffle(deck);
    }

    public void notifyPlayers(){
        for(Player player:players){
            synchronized(player.getLockForPlayer()){
                player.getLockForPlayer().notifyAll();
            }
        }
    }
}
