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

    private Thread dealerThread;

    final int NUM_OF_SLOTS = 12;

    final int SIXTEY_SECONDS = 60000;

    final int ONE_SECOND = 1000;

    final int THREE = 3;

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
        this.dealerThread = null;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            //Thread playerThread = new Thread(player);
            //player.setPlayerThread(playerThread);
            //playerThread.start();
            player.StartPlayerThread();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            dealerShouldReshuffle=false;
            updateTimerDisplay(true);
            timerLoop(); //self mark- should do things until we need to rersheufle 
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while(!dealerShouldReshuffle)
        {
            sleepUntilWokenOrTimeout();
        }
        // the next lines were given
        // while (!terminate && System.currentTimeMillis() < reshuffleTime) {
        //     sleepUntilWokenOrTimeout();
        //     updateTimerDisplay(false);
        //     removeCardsFromTable();
        //     placeCardsOnTable();
        // }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {      
        try{
            Thread.sleep(ONE_SECOND);
        } 
        catch(InterruptedException error){
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
        while (slotsToRemove.size()!=0)
        {
            int slot=slotsToRemove.remove(0);
            table.removeCard(slot);
            for(int i=0; i<players.length; i++)
            {
                players[i].removeSlotFromArr(slot); //update player its token has been removed from the card
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
        // if(table.countCards() == 0){
        //     placeTwelveCardsOnTable();
        // }
        //else{
            int slot;
            int card;
            while(!deck.isEmpty() && table.countCards() < NUM_OF_SLOTS){
                card = deck.remove(0);
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
                table.placeCard(deck.remove(0), slot);
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
            reshuffleTime = SIXTEY_SECONDS + System.currentTimeMillis(); 
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
            if(!slotsToRemove.contains(i)){
                slotsToRemove.add(i);
            }
        }
        Collections.shuffle(slotsToRemove);
        removeCardsFromTable();
        dealerShouldReshuffle=false;
        //notifyPlayers();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        List<Player> winners = new LinkedList<>();
        winners.add(players[0]);
        for (Player cuPlayer : players) {
            if(winners.getFirst().score() == cuPlayer.score()){
                winners.add(cuPlayer);
            }
            else if(winners.getFirst().score() > cuPlayer.score()){
                winners.clear();
                winners.add(cuPlayer);
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
        dealerThread.interrupt(); //if dealer thread is sleeping we wake him up
    }

    public void checkPlayersSets(){
        System.out.println(">> checkPlayersSets. size=" + playersToCheck.size());
        Player curPlayer;
        int[] curSet;
        while(!playersToCheck.isEmpty()){
            try {
                curPlayer = playersToCheck.take();
                curSet = slotsToCards(setVecToArr(curPlayer.getSlotsVector()));
                if(testSet(curSet)){
                    removeCardsFromTable();
                    placeCardsOnTable();
                    curPlayer.incomingActions.put(Player.POINT_MSG);
                    updateTimerDisplay(true);
                }
                else{
                    // curPlayer.incomingActions.clear();
                    //self remark- the incomingActions queue is empty because we made it when updating takeOutput=false
                    curPlayer.incomingActions.put(Player.PENALTY_MSG);
                }
                curPlayer.waKeUpPlayer();
            } catch (InterruptedException e) {}
        }
        System.out.println("<< checkPlayersSets. size=" + playersToCheck.size());
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
        //Collections.shuffle(deck);
    }

    public Thread getDealerThread(){
        return dealerThread;
    }

    public void notifyPlayers(){
        for(Player player:players){
            synchronized(player.getLockForPlayer()){
                player.getLockForPlayer().notifyAll();
            }
        }
    }
}
