package bguspl.set.ex;

import java.util.Vector;

import bguspl.set.Env;

public class ChosenSlots{

    /**
     * the slots on which a player has tokens
     */
    private Vector<Integer> slotsVector;

    /**
     * Game entities.
     */
    Table table;
    final int featureSize;
    
    public ChosenSlots(Table table, Env env) {
        this.slotsVector = new Vector<>();
        this.table = table;
        this.featureSize = env.config.featureSize;
    }
    
    public synchronized Vector<Integer> getSlotsVector() {
        return slotsVector;
    }
    
    public synchronized boolean contains(int slot){
        return slotsVector.contains(slot); 
    }

    public synchronized int size(){
        return slotsVector.size();
    }

    public synchronized void clear() {
        slotsVector.clear();
    }

    public synchronized void add(int slot){
        slotsVector.add(slot);
    }

    public synchronized void remove(int slot){ 
        if(slotsVector.contains(slot)){
            slotsVector.remove(slotsVector.indexOf(slot));  
        }
    }

    public synchronized int[] convertToSet(){
        if (size() == featureSize) {
            return slotsToCards(setVecToArr(slotsVector));
        } 
        return null;
    }

    private int[] slotsToCards(int[] slots){
        int[]cards = new int[slots.length];
        for (int i = 0; i < cards.length; i++) {
            cards[i] = table.getSlotToCard()[slots[i]];
        }
        return cards;
    }

    private int[] setVecToArr(Vector<Integer> vec){
        int[] res = new int[featureSize];
        for (int i = 0; i < res.length; i++) {
            res[i] = vec.get(i);
        }
        return res;
    }

}