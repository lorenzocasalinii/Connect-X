package connectx.myPlayer;

import java.util.HashMap;

public class TranspositionTable {
    private static TranspositionTable instance;
    private HashMap<Long, Double> table;

    private TranspositionTable() {
        table = new HashMap<>();
    }

    public static TranspositionTable getInstance() {
        if (instance == null) {
            instance = new TranspositionTable();
        }
        return instance;
    }

    public void store(long hash, double score) {
        table.put(hash, score);
    }

    public boolean contains(long hash) {
        return table.containsKey(hash);
    }

    public double retrieve(long hash) {
        return table.get(hash);
    }
}
