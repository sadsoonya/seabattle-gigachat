package com.example.seabattle;

import java.util.ArrayList;
import java.util.List;

public class GameBoard {
    public enum CellState {
        EMPTY, SHIP, HIT, MISS
    }

    private CellState[][] board;
    private List<GameModels.Ship> ships;
    private boolean[][] hitCells;

    public GameBoard() {
        board = new CellState[10][10];
        hitCells = new boolean[10][10];
        ships = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                board[i][j] = CellState.EMPTY;
                hitCells[i][j] = false;
            }
        }
    }

    public void clear() {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                board[i][j] = CellState.EMPTY;
                hitCells[i][j] = false;
            }
        }
        ships.clear();
    }

    public void addShip(GameModels.Ship ship) {
        ships.add(ship);
        for (String pos : ship.positions) {
            String[] coords = pos.split(",");
            int row = Integer.parseInt(coords[0]);
            int col = Integer.parseInt(coords[1]);
            board[row][col] = CellState.SHIP;
        }
    }

    public boolean shoot(int row, int col) {
        if (hitCells[row][col]) return false;

        hitCells[row][col] = true;

        if (board[row][col] == CellState.SHIP) {
            board[row][col] = CellState.HIT;

            for (GameModels.Ship ship : ships) {
                if (ship.hit(row, col)) {
                    if (ship.isSunk()) {
                        markSurroundingCells(ship);
                    }
                    break;
                }
            }
            return true;
        } else {
            board[row][col] = CellState.MISS;
            return false;
        }
    }

    private void markSurroundingCells(GameModels.Ship ship) {
        for (String pos : ship.positions) {
            String[] coords = pos.split(",");
            int r = Integer.parseInt(coords[0]);
            int c = Integer.parseInt(coords[1]);

            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    int nr = r + i;
                    int nc = c + j;
                    if (nr >= 0 && nr < 10 && nc >= 0 && nc < 10 && board[nr][nc] == CellState.EMPTY) {
                        board[nr][nc] = CellState.MISS;
                        hitCells[nr][nc] = true;
                    }
                }
            }
        }
    }

    public CellState getCell(int row, int col) {
        return board[row][col];
    }

    public boolean isCellHit(int row, int col) {
        return hitCells[row][col];
    }

    public GameModels.Ship getShipAt(int row, int col) {
        for (GameModels.Ship ship : ships) {
            String pos = row + "," + col;
            if (ship.positions.contains(pos)) {
                return ship;
            }
        }
        return null;
    }

    public boolean allShipsSunk() {
        for (GameModels.Ship ship : ships) {
            if (!ship.isSunk()) return false;
        }
        return true;
    }
}