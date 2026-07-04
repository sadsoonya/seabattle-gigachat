package com.example.seabattle;

import java.util.ArrayList;
import java.util.List;

public class GameModels {

    public static class Ship {
        public int row, col;
        public boolean isHorizontal;
        public int length;
        public List<String> positions;
        public List<Boolean> hits;

        public Ship(int row, int col, boolean isHorizontal, int length) {
            this.row = row;
            this.col = col;
            this.isHorizontal = isHorizontal;
            this.length = length;
            this.positions = new ArrayList<>();
            this.hits = new ArrayList<>();

            for (int i = 0; i < length; i++) {
                int r = isHorizontal ? row : row + i;
                int c = isHorizontal ? col + i : col;
                positions.add(r + "," + c);
                hits.add(false);
            }
        }

        public boolean hit(int r, int c) {
            String pos = r + "," + c;
            int index = positions.indexOf(pos);
            if (index != -1 && !hits.get(index)) {
                hits.set(index, true);
                return true;
            }
            return false;
        }

        public boolean isSunk() {
            for (boolean hit : hits) {
                if (!hit) return false;
            }
            return true;
        }
    }

    public static class Move {
        public int row, col;
        public boolean isHit;
        public boolean isKill;

        public Move(int row, int col) {
            this.row = row;
            this.col = col;
            this.isHit = false;
            this.isKill = false;
        }
    }
}