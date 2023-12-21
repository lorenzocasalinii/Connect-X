package connectx.myPlayer;

import java.util.HashMap;

// Class representing a Transposition Table used for storing and retrieving game state evaluations.
public class TranspositionTable {
    
    private static TranspositionTable instance;         // singleton instance for the TranspositionTable class

    private HashMap<Long, Double> table;            // HashMap to store the hash-value pairs

    // Constructor to enforce singleton pattern
    private TranspositionTable() {
        table = new HashMap<>();
    }

    // Get the singleton instance of the TranspositionTable class
    public static TranspositionTable getInstance() {
        if (instance == null) {
            instance = new TranspositionTable();
        }
        return instance;
    }

    // Store the given hash and corresponding score in the table
    public void store(long hash, double score) {
        table.put(hash, score);
    }

    // Check if the table contains the given hash
    public boolean contains(long hash) {
        return table.containsKey(hash);
    }

    // Retrieve the score associated with the given hash
    public double retrieve(long hash) {
        return table.get(hash);
    }
    
}

