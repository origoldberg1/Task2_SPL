package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.stream.Collectors;


/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * players that have placed tokens on each slot
     */
    private Vector<Vector<Integer>> tokensOnTable;

    /**
     * a list of cards(integers) that are on the table. used to check if there is a set on the table
     */
    private List<Integer> cardsOnTable;

    /**
     * Game entities.
     */
    private final int tableSize;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.tableSize = env.config.tableSize;
        this.cardsOnTable = new LinkedList<>();
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
        this.tokensOnTable = new Vector<>();
        for (int i = 0; i < tableSize; i++) {
            this.tokensOnTable.add(new Vector<>());
        }
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public synchronized void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        cardsOnTable.add(card);
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public synchronized void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        while (!tokensOnTable.elementAt(slot).isEmpty()) {
            removeToken(tokensOnTable.elementAt(slot).remove(0), slot);
        } 
        Integer card = slotToCard[slot];
        if(card != null){
            cardToSlot[card] = null;
            slotToCard[slot] = null;
            if(cardsOnTable.contains(card)){
                cardsOnTable.remove(cardsOnTable.indexOf(card));
            }
            env.ui.removeCard(slot);
        }
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public synchronized boolean placeToken(int player, int slot) {
        if(slotToCard[slot] != null){
            tokensOnTable.elementAt(slot).add(player);
            env.ui.placeToken(player, slot);
            return true;
        }
        return false;
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public synchronized boolean removeToken(int player, int slot) {
        if(tokensOnTable.elementAt(slot).contains(player)){
            tokensOnTable.elementAt(slot).remove(tokensOnTable.elementAt(slot).indexOf(player));
        }
        env.ui.removeToken(player, slot);
        return true;
    }

    public synchronized Integer[] getSlotToCard() {
        return slotToCard;
    }

    public synchronized Integer[] getCardToSlot() {
        return cardToSlot;
    }

    public synchronized Vector<Vector<Integer>> getTokensOnTable() {
        return tokensOnTable;
    }

    public synchronized boolean hasNoSetOnTable(){
            return env.util.findSets(cardsOnTable, 1).size() == 0;
    }
 }
