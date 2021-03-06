package core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import core.Globals.Colour;
import core.Globals.JokerRules;

public class Meld {
    public enum MeldType {
        INVALID, POTENTIAL, RUN, SET;
    }

    private ArrayList<Tile> meld;
    private MeldType meldType;
    private boolean isLocked = false;
    private boolean isInitialMeld = false;
    
    // Highlighting
    private int meldID;

    public Meld() {
        this.meld = new ArrayList<>();
        this.meldType = MeldType.INVALID;
        this.generateMeldID();
    }

    public Meld(String meld) {
        this.meld = new ArrayList<>();
        this.meldType = MeldType.INVALID;
        this.generateMeldID();

        if (meld.length() != 0) {
            for (String tile : meld.split(",")) {
                this.addTile(new Tile(tile));
            }
        }
    }
    
    public Meld(Meld meld) {
        this.meld = new ArrayList<>();
        this.meldType = meld.meldType;
        this.isLocked = meld.isLocked;
        this.isInitialMeld = meld.isInitialMeld;
        this.meldID = meld.meldID;
        
        for (Tile tile : meld.meld) {
            this.meld.add(new Tile(tile));
        }
    }
    
    public Tile addTile(Tile tile) {
        ArrayList<Tile> tempMeld = new ArrayList<>();
        tempMeld.add(tile);
        return this.addTile(tempMeld);
    }

    public Tile removeTile(int index) {
        if (!this.isInitialMeld && this.isLocked) { return null; }
        if (index < 0 || index >= this.meld.size()) {
            return null;
        }

        Tile removedTile = this.meld.remove(index);     
        this.meldType = this.determineMeldType(this.meld);
        
        // If the removed tile is a joker or a meld with a joker only is left, reset it
        if (removedTile.isJoker()) { removedTile.releaseJoker(); }
        
        // If the meld has a single joker after splitting, reset it
        return removedTile;
    }

    public boolean isValidIfRemoveTile(int index) {
        // Copy meld
        Meld copyMeld = new Meld(this);

        // Remove from copy and check if valid
        Tile removed = copyMeld.removeTile(index);
        if (copyMeld.isValidMeld() && removed != null) {
            return true;
        }
        return false;
    }

    public Tile removeTileObject(Tile tile) {
        for (int i = 0; i < this.meld.size(); i++) {
            if (this.meld.get(i).equals(tile)) {
                return removeTile(i);
            }
        }
        return null;
    }

    public Meld splitMeld(int index) {
        if ((!this.isInitialMeld && this.isLocked) || index <= 0 || index >= this.meld.size()) { return null; }

        Meld newMeld = new Meld();
        ArrayList<Tile> secondHalf = new ArrayList<>();
        int currMeldSize = this.meld.size();

        // Add the second chunk of the meld to a new ArrayList
        for (int i = index; i < currMeldSize; i++) {
            secondHalf.add(this.meld.remove(index));
        }

        // Re-determine the meld type since the current meld has changed
        this.meldType = this.determineMeldType(this.meld);

        // Check if the second half of the meld (which we split) is valid
        for (Tile tile : secondHalf) {
            newMeld.addTile(tile);
        }
        
        // If the meld has one tile left which is a joker, reset it
        if (this.meld.size() == 1 && this.meld.get(0).isJoker()) { this.meld.get(0).releaseJoker(); }
        
        // If the second half has one tile left which is a joker, reset it
        if (secondHalf.size() == 1 && secondHalf.get(0).isJoker()) { secondHalf.get(0).releaseJoker(); }
        return newMeld;
    }

    public Tile addTile(ArrayList<Tile> tiles) {
        ArrayList<Tile> tempMeld = new ArrayList<>();
        ArrayList<Tile> jokers = new ArrayList<>();
        Tile releasedJoker = null;
        
        // Disallow adding multiple tiles containing a joker (affects console game only) TODO: Remove console game support
        if (tiles.size() > 1 && tiles.removeIf(t -> t.isJoker())) { return null; }
        
        // Disallow two jokers in one meld
        if (this.containsJoker() && tiles.get(0).isJoker()) { return null; }
        
        // Disallow adding a tile to a full meld with a joker if Lenient Jokers are allowed
        if (Globals.getJokerRules() == JokerRules.LENIENT) {
            if (this.meld.size() == 13 && this.meldType == MeldType.RUN) { return null; }
            if (this.meld.size() == 4 && this.meldType == MeldType.SET)  { return null; }   
        }
        
        // Check if the tile being added is part of an initial meld (so the joker inside is not replaced by it right away)
        boolean meldIsFromHand = this.meld.stream().allMatch(t -> t.isOnTable() == false);
        if (meldIsFromHand && !tiles.get(0).isOnTable()) { this.isInitialMeld = true; }
        
        // Temporarily remove jokers from meld
        for (int i = 0; i < this.meld.size(); i++) {
            if (this.meld.get(i).isJoker()) {
                jokers.add(this.meld.remove(i));
            }
        }

        // If meld has no jokers
        if (jokers.size() == 0) {
            // Disallow adding tiles to melds that are full
            if (this.meld.size() == 13 && this.meldType == MeldType.RUN) { return null; }
            if (this.meld.size() == 4 && this.meldType == MeldType.SET)  { return null; }
            
            if (tiles.get(0).isJoker()) {
                if (Globals.getJokerRules() == JokerRules.NO_EXISTING_MELDS && this.isInitialMeld == false) {
                    return null;
                }
                
                tempMeld.addAll(this.meld);
                Tile joker = this.determineJokerType(tiles.get(0), tempMeld);
                tempMeld.add(joker);
                return this.buildMeld(tempMeld, releasedJoker);
            }
             tempMeld.addAll(this.meld);
             tempMeld.addAll(tiles);   
             return this.buildMeld(tempMeld, releasedJoker);
        }
        
        // If meld has 1 joker
        if (jokers.size() == 1 && tiles.get(0).isJoker() == false) {
            Tile joker = jokers.get(0);
            Tile tile = tiles.get(0);
            
            // Adding tile to a meld with a joker only
            if (this.meld.size() == 0) {
                tempMeld.add(tile);
                tempMeld.add(this.determineJokerType(joker, tempMeld));
                return this.buildMeld(tempMeld, releasedJoker);
            }
            
            // Adding tile to a meld with a joker and one or more tiles
            if (this.meld.size() > 0) {
                if (Globals.getJokerRules() != JokerRules.LENIENT) {
                    // Check if the joker can be replaced as long as its not part of an initial move
                    if (!this.isInitialMeld && joker.jokerEquals(tile)) {
                        // If this tile is on the table but is being added to a meld that is locked 
                        if (tile.onTable && this.isLocked) { 
                            // Add the joker back
                            joker = this.determineJokerType(joker, tempMeld);
                            this.meld.add(joker);
                            this.buildMeld(this.meld, releasedJoker);
                            return null;
                        }
                        // Otherwise this meld is not locked or the tile being added is from the hand
                        // In both cases, replace the joker
                        releasedJoker = jokers.remove(0);
                        tempMeld.add(tile);
                        tempMeld.addAll(this.meld);
                        return this.buildMeld(tempMeld, releasedJoker);
                    } 
                }
                
                // Otherwise the joker can't be replaced. Check if the tile can still be added to the meld
                tempMeld.addAll(this.meld);
                tempMeld.add(tile);
                Collections.sort(tempMeld, Comparator.comparingInt(Tile::getValue)); // Sort numerically
                
                // Try adjusting the joker to this newly added tile
                Tile adjustedJoker = this.determineJokerType(joker, tempMeld);
                
                // If this joker could not be added to this meld because of the new tile
                // Then the new tile must have mad the whole meld invalid
                if (adjustedJoker == null) {
                    // Remove the tile
                    tempMeld.remove(tile);
                    Collections.sort(tempMeld, Comparator.comparingInt(Tile::getValue)); // Sort numerically
                    
                    // Add the joker back
                    joker = this.determineJokerType(joker, tempMeld);
                    tempMeld.add(joker);
                    this.buildMeld(tempMeld, releasedJoker);
                    return null;
                }
                
                // Otherwise, the joker and the new tile could be added to the new meld
                tempMeld.add(joker);
                return this.buildMeld(tempMeld, releasedJoker);
            }
        }
        return null;
    }
    
    private Tile buildMeld(ArrayList<Tile> tempMeld, Tile releasedJoker) {
        // Test for a valid meld on a dummy list before changing the actual meld
        MeldType tempMeldType = this.determineMeldType(tempMeld);

        // Check if this is a meld or a potential meld
        if (tempMeldType != MeldType.INVALID && tempMeld.size() < 3 || tempMeldType == MeldType.RUN || tempMeldType == MeldType.SET) {
            Collections.sort(tempMeld, Comparator.comparingInt(Tile::getValue)); // Sort numerically
            
            if (Globals.getJokerRules() != JokerRules.LENIENT) {
                // Lock the meld if a joker from the hand was added
                this.isLocked = this.determineLockedMeld(tempMeld);
            }
            
            this.meld = tempMeld;
            this.meldType = tempMeldType;
            
            // Highlighting
            for (Tile tile : this.meld) {
                if (tile.getParentMeldID() == 0) {
                    tile.setParentMeldID(this.meldID);
                }
            }
            
            // If there was a released joker, return it
            if (releasedJoker != null) {
                releasedJoker.releaseJoker();
                return releasedJoker;
            }
            
            // Return a null type tile to indicate that the addition was successful
            return new Tile(null, 0);
        }
        return null;
    }
    
    // Determines the tile the joker will replace when added to a meld
    private Tile determineJokerType(Tile joker, ArrayList<Tile> meld) {
        // If the meld is empty (adding joker to new meld)
        if (meld.size() == 0) {
            return joker;
        }
        
        // If the meld is a single tile
        if (meld.size() == 1) {
            // Set the joker's alternate attributes to make this meld a potential set
            // Get a list of all the available colours
            ArrayList<Colour> availableColours = new ArrayList<>();
            for (Colour c : Colour.values()) {
                availableColours.add(c);
            }

            // Remove the colours already in use from the available colours list
            for (Tile tile : meld) {
                availableColours.remove(tile.getColour());
            }

            // The joker could be any other available colour in the set
            joker.setColour(availableColours.get(0));
            joker.setValue(meld.get(0).value);
            for (Colour c : availableColours) {
                joker.addAlternateState(new Tile(c, meld.get(0).value));
            }

            // Set the joker's alternate attributes to make this meld a potential run
            if (meld.get(0).value == 1) {
                joker.addAlternateState(new Tile(meld.get(0).colour, meld.get(0).value + 1));
            } else if (meld.get(0).value == 13) {
                joker.addAlternateState(new Tile(meld.get(0).colour, meld.get(0).value - 1));
            } else {
                joker.addAlternateState(new Tile(meld.get(0).colour, meld.get(0).value - 1));
                joker.addAlternateState(new Tile(meld.get(0).colour, meld.get(0).value + 1));
            }
        } else {
            // Case when the meld is a valid or potential run
            if (this.determineMeldType(meld) == MeldType.RUN || this.determineMeldType(meld) != MeldType.INVALID && meld.get(0).colour == meld.get(1).colour) {
                // Adding to back
                if (meld.get(0).value == 1) {
                    joker.setColour(meld.get(0).colour);
                    joker.setValue(meld.get(meld.size() - 1).value + 1);
                // Adding to front
                } else if (meld.get(meld.size() - 1).value == 13) {
                    joker.setColour(meld.get(0).colour);
                    joker.setValue(meld.get(0).value - 1);
                // Add to back by default
                } else {
                    joker.setColour(meld.get(0).colour);
                    joker.setValue(meld.get(meld.size() - 1).value + 1);
                }
            // Case when the meld is a valid or potential set
            } else if (this.determineMeldType(meld) == MeldType.SET || this.determineMeldType(meld) != MeldType.INVALID && meld.get(0).colour != meld.get(1).colour) {
                // Get a list of all the available colours
                ArrayList<Colour> availableColours = new ArrayList<>();
                for (Colour c : Colour.values()) {
                    availableColours.add(c);
                }

                // Remove the colours already in use from the available colours list
                for (Tile tile : meld) {
                    availableColours.remove(tile.getColour());
                }

                // Set the joker colour as the first colour in the available colours list
                if (availableColours.size() == 0) { return null; }
                joker.setColour(availableColours.get(0));
                joker.setValue(meld.get(0).value);
                
                // The joker could be any other available colour in the set
                for (Colour c : availableColours) {
                    joker.addAlternateState(new Tile(c, meld.get(0).value));
                }
            // Case when the meld is invalid (adding back a joker to make it valid)
            } else {                
                // Check if the joker can be added back to the potential meld without making it invalid
                if (meld.size() == 2) {
                    // In potential melds, jokers can have multiple forms. Check each one
                    for (Tile tile : joker.getAlternateState()) {
                        for (int i = 0; i < meld.size(); i++) {
                            meld.add(i, tile);
                            if (this.determineMeldType(meld) == MeldType.POTENTIAL || this.determineMeldType(meld) == MeldType.RUN || determineMeldType(meld) == MeldType.SET) {
                                meld.remove(i);
                                joker.setColour(tile.getColour());
                                joker.setValue(tile.getValue());
                                return joker;
                            }
                            meld.remove(i);
                        }
                    }
                    return null;
                }
                
                // Check if the joker can be added back to the meld without making it invalid
                for (int i = 0; i < meld.size(); i++) {
                    meld.add(i, joker);
                    if (this.determineMeldType(meld) == MeldType.POTENTIAL || this.determineMeldType(meld) == MeldType.RUN || this.determineMeldType(meld) == MeldType.SET) {
                        meld.remove(i);
                        return joker;
                    }
                    meld.remove(i);
                }
                return null;
            }
        }
        return joker;
    }

    private MeldType determineMeldType(ArrayList<Tile> tiles) {
        // Redundant melds
        if (tiles.size() <= 1) { return MeldType.POTENTIAL; }

        // Map each tile to its corresponding integer value and use this array to determine if it satisfies any Meld properties
        List<Integer> tilesByValue = tiles.stream()
                .map(tile -> tile.getValue())
                .sorted()
                .collect(Collectors.toList());

        // Calculate the differences of each consecutive pair of elements in the sorted list and if each pair
        // yields a difference of 1 then the list contains consecutive values, which makes it a potential RUN
        boolean consecutiveValues = IntStream
                .range(0, tilesByValue.size() - 1)
                .map(i -> tilesByValue.get(i + 1) - tilesByValue.get(i))
                .reduce((t1, t2) -> t1 * t2)
                .getAsInt() == 1;

        // Determine if the values in the potential SET are of the same value
        boolean sameValues = tiles.stream()
                .allMatch(tile -> tile.getValue() == tiles.get(0).getValue());

        // Count the number of distinct colours, N, in this list. N = list.size() is true distinctness, which makes it a potential RUN
        boolean differentColours = tiles.stream()
                .map(tile -> tile.getColour().getSymbol())
                .distinct()
                .collect(Collectors.counting())
                .intValue() == tiles.size();

        // Determine if the values in the potential RUN are of the same colour
        boolean sameColours = tiles.stream()
                .allMatch(tile -> tile.getColour() == tiles.get(0).getColour());

        if ((sameValues && differentColours || consecutiveValues && sameColours) && tiles.size() < 3) { return MeldType.POTENTIAL;    }
        else if (consecutiveValues && sameColours)                                                    { return MeldType.RUN;          }
        else if (sameValues && differentColours)                                                      { return MeldType.SET;          }
        else                                                                                          { return MeldType.INVALID;      }
    }
    
    // Locked meld = meld with a joker that has not been replaced yet
    private boolean determineLockedMeld(ArrayList<Tile> meld) {
        if (Globals.getJokerRules() != JokerRules.LENIENT) {
            for (Tile tile : meld) {
                if (tile.isJoker() && tile.isReplaced() == false) {
                    return true;
                }
            }   
        }
        return false;
    }
    
    public boolean isLocked() {
        if (Globals.getJokerRules() != JokerRules.LENIENT) {
            // This meld is locked if it has a joker which has not been replaced by a tile from the hand
            return this.meld.stream().anyMatch(t -> t.isJoker() && !t.isReplaced());   
        }
        return false;
    }
    
    public void setIsLocked(boolean isLocked) {
        if (Globals.getJokerRules() != JokerRules.LENIENT) {
            // Lock this meld manually by looking for a joker and setting isReplaced
            for (Tile tile : this.meld) {
                if (tile.isJoker()) {
                    tile.setIsReplaced(!isLocked);
                    this.isLocked = isLocked;
                }
            }   
        }
    }
    
    public boolean isInitialMeld() {
        return this.isInitialMeld;
    }
    
    public void setIsInitialMeld(boolean isInitialMeld) {
        for (Tile tile : this.meld) {
            tile.setOnTable(true);
        }
        this.isInitialMeld = isInitialMeld;
    }

    public int getSize() {
        return this.meld.size();
    }
    
    public ArrayList<Tile> getMeld() {
        return this.meld;
    }

    public Tile getTile(int index) {
        return this.meld.get(index);
    }

    public MeldType getMeldType() {
        return this.meldType;
    }

    public boolean isValidMeld() {
        return this.meldType == MeldType.RUN || this.meldType == MeldType.SET;
    }

    public boolean isPotentialMeld() {
        return this.meldType == MeldType.POTENTIAL;
    }

    public int getValue() {
        int total = 0;
        for (Tile tile : this.meld) {
            total += tile.getValue();
        }
        return total;
    }

    public boolean containsTile(Tile tile) {
        for (Tile tempTile : this.meld) {
            if (tempTile.toString().equals(tile.toString())) {
                return true;
            }
        }
        return false;
    }
    
    public boolean containsJoker() {
        for (Tile tempTile : this.meld) {
            if (tempTile.isJoker()) {
                return true;
            }
        }
        return false;
    }

    public boolean containsTileValue(Tile tile) {
        for (Tile tempTile : this.meld) {
            if (tempTile.getValue() == tile.getValue()) {
                return true;
            }
        }
        return false;
    }

    public boolean equals(Meld meld) {
        if (this.getSize() != meld.getSize()) {
            return false;
        }
        for (int i = 0; i < meld.getSize(); i++) {
            if (!this.containsTile(meld.getTile(i))) {
                return false;
            }
        }
        return true;
    }
    
    public int getMeldID() {
        return this.meldID;
    }
    
    public void generateMeldID() {
        Random rnd = new Random();
        rnd.setSeed(this.hashCode());
        this.meldID = rnd.nextInt();
        
        // Setting each of its tiles to its corresponding ID
        for (Tile tile : this.meld) {
            tile.setParentMeldID(this.meldID);
        }
    }
    
    public boolean hasChild(Tile tile) {
        return this.meldID == tile.getParentMeldID();
    }
    
    public String toHighlightedString() {
        return this.meld.stream().map(t -> t.toHighlightedString()).collect(Collectors.joining(" ", "{", "}"));
    }

    @Override
    public String toString() {
        return this.meld.stream().map(Object::toString).collect(Collectors.joining(" ", "{", "}"));
    }
}