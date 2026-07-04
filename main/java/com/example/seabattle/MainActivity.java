package com.example.seabattle;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.gridlayout.widget.GridLayout;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SeaBattle";
    private GridLayout playerGrid, aiGrid;
    private TextView tvStatus, tvPlayerScore, tvAIScore;
    private Button btnReset;

    private GameBoard playerBoard;
    private GameBoard aiBoard;
    private GigaChatAI aiOpponent;
    private boolean isPlayerTurn = true;
    private int playerScore = 0, aiScore = 0;
    private boolean gameActive = true;
    private Handler handler = new Handler();
    private int cellSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "=== ИГРА ЗАПУЩЕНА ===");

        initViews();

        // Рассчитываем размер ячейки после отрисовки
        playerGrid.post(() -> {
            int gridSize = playerGrid.getWidth();
            cellSize = gridSize / 10;
            Log.d(TAG, "Размер ячейки: " + cellSize);
            initGame();
        });
    }

    private void initViews() {
        Log.d(TAG, "initViews: инициализация view");
        playerGrid = findViewById(R.id.playerGrid);
        aiGrid = findViewById(R.id.aiGrid);
        tvStatus = findViewById(R.id.tvStatus);
        tvPlayerScore = findViewById(R.id.tvPlayerScore);
        tvAIScore = findViewById(R.id.tvAIScore);
        btnReset = findViewById(R.id.btnReset);

        btnReset.setOnClickListener(v -> {
            Log.d(TAG, "Нажата кнопка НОВАЯ ИГРА");
            resetGame();
        });
    }

    private void initGame() {
        Log.d(TAG, "initGame: создание новой игры");
        playerBoard = new GameBoard();
        aiBoard = new GameBoard();

        setupDefaultShips(playerBoard);
        setupDefaultShips(aiBoard);

        aiOpponent = new GigaChatAI(this);

        drawPlayerBoard();
        drawAIBoard();

        gameActive = true;
        isPlayerTurn = true;
        tvStatus.setText("Ваш ход! 🔫");
        Log.d(TAG, "initGame: игра готова, ходит игрок");
    }

    private void setupDefaultShips(GameBoard board) {
        Log.d(TAG, "setupDefaultShips: расстановка кораблей");
        board.clear();

        int[][] ships = {
                {0, 0, 1, 4}, {2, 3, 1, 3}, {5, 6, 0, 3},
                {7, 1, 1, 2}, {3, 8, 0, 2}, {8, 5, 1, 2},
                {0, 8, 0, 1}, {4, 4, 1, 1}, {6, 9, 0, 1}, {9, 2, 1, 1}
        };

        for (int[] ship : ships) {
            board.addShip(new GameModels.Ship(ship[0], ship[1], ship[2] == 1, ship[3]));
        }
        Log.d(TAG, "setupDefaultShips: добавлено " + ships.length + " кораблей");
    }

    private void drawPlayerBoard() {
        playerGrid.removeAllViews();
        playerGrid.setRowCount(10);
        playerGrid.setColumnCount(10);

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                Button cell = createPlayerCell(i, j);
                playerGrid.addView(cell);
            }
        }
    }

    private Button createPlayerCell(int row, int col) {
        Button cell = new Button(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cellSize;
        params.height = cellSize;
        params.setMargins(2, 2, 2, 2);
        cell.setLayoutParams(params);

        GameBoard.CellState state = playerBoard.getCell(row, col);

        // Создаем красивый фон
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(8);

        if (state == GameBoard.CellState.HIT) {
            shape.setColor(Color.parseColor("#F44336"));
            cell.setText("💥");
            cell.setTextSize(16);
        } else if (state == GameBoard.CellState.MISS) {
            shape.setColor(Color.parseColor("#607D8B"));
            cell.setText("⚫");
            cell.setTextSize(16);
        } else if (state == GameBoard.CellState.SHIP) {
            shape.setColor(Color.parseColor("#4CAF50"));
            cell.setText("🚢");
            cell.setTextSize(16);
        } else {
            shape.setColor(Color.parseColor("#0F3460"));
            cell.setText("");
        }

        cell.setBackground(shape);
        cell.setEnabled(false);
        return cell;
    }

    private void drawAIBoard() {
        aiGrid.removeAllViews();
        aiGrid.setRowCount(10);
        aiGrid.setColumnCount(10);

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                Button cell = createAICell(i, j);
                final int row = i, col = j;
                cell.setOnClickListener(v -> {
                    Log.d(TAG, "Клик по клетке AI: row=" + row + ", col=" + col);

                    if (gameActive && isPlayerTurn && !aiBoard.isCellHit(row, col)) {
                        makePlayerMove(row, col);
                    } else if (!gameActive) {
                        Toast.makeText(MainActivity.this, "Игра окончена! Нажмите Новая игра", Toast.LENGTH_SHORT).show();
                    } else if (!isPlayerTurn) {
                        Toast.makeText(MainActivity.this, "Сейчас ход AI! ⏳", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Сюда уже стреляли! 🎯", Toast.LENGTH_SHORT).show();
                    }
                });
                aiGrid.addView(cell);
            }
        }
    }

    private Button createAICell(int row, int col) {
        Button cell = new Button(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cellSize;
        params.height = cellSize;
        params.setMargins(2, 2, 2, 2);
        cell.setLayoutParams(params);

        GameBoard.CellState state = aiBoard.getCell(row, col);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(8);

        if (state == GameBoard.CellState.HIT) {
            shape.setColor(Color.parseColor("#F44336"));
            cell.setText("💥");
            cell.setTextSize(16);
        } else if (state == GameBoard.CellState.MISS) {
            shape.setColor(Color.parseColor("#607D8B"));
            cell.setText("⚫");
            cell.setTextSize(16);
        } else {
            shape.setColor(Color.parseColor("#0F3460"));
            cell.setText("❓");
            cell.setTextSize(14);
        }

        cell.setBackground(shape);
        return cell;
    }

    private void makePlayerMove(int row, int col) {
        Log.d(TAG, "=== makePlayerMove: ход игрока в (" + row + "," + col + ") ===");

        if (!gameActive) return;

        boolean hit = aiBoard.shoot(row, col);

        if (hit) {
            GameModels.Ship hitShip = aiBoard.getShipAt(row, col);
            if (hitShip != null && hitShip.isSunk()) {
                playerScore += hitShip.length;
                tvPlayerScore.setText("👨 Игрок: " + playerScore);
                Toast.makeText(this, "💥 Корабль уничтожен! +" + hitShip.length + " очков", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "🎯 Попадание! (" + (row + 1) + "," + (col + 1) + ")", Toast.LENGTH_SHORT).show();
            }

            drawAIBoard();

            if (aiBoard.allShipsSunk()) {
                gameActive = false;
                tvStatus.setText("🎉 ПОБЕДА! Вы выиграли! 🎉");
                Toast.makeText(this, "Поздравляем! Вы победили!", Toast.LENGTH_LONG).show();
                return;
            }

            tvStatus.setText("🔥 Попадание! Еще ход!");
        } else {
            Toast.makeText(this, "💧 Мимо! (" + (row + 1) + "," + (col + 1) + ")", Toast.LENGTH_SHORT).show();
            drawAIBoard();
            isPlayerTurn = false;
            tvStatus.setText("🤖 Ход AI...");

            handler.postDelayed(this::makeAIMove, 800);
        }
    }

    private void makeAIMove() {
        Log.d(TAG, "=== makeAIMove: начало хода AI ===");

        if (!gameActive || isPlayerTurn) return;

        aiOpponent.makeMove(playerBoard, new GigaChatAI.AIMoveCallback() {
            @Override
            public void onMove(int row, int col) {
                runOnUiThread(() -> {
                    if (!gameActive || isPlayerTurn) return;

                    boolean hit = playerBoard.shoot(row, col);

                    if (hit) {
                        GameModels.Ship hitShip = playerBoard.getShipAt(row, col);
                        if (hitShip != null && hitShip.isSunk()) {
                            aiScore += hitShip.length;
                            tvAIScore.setText("🤖 AI: " + aiScore);
                            Toast.makeText(MainActivity.this, "💀 AI уничтожил ваш корабль!", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "🎯 AI попал в (" + (row + 1) + "," + (col + 1) + ")", Toast.LENGTH_SHORT).show();
                        }

                        drawPlayerBoard();

                        if (playerBoard.allShipsSunk()) {
                            gameActive = false;
                            tvStatus.setText("😭 ПОРАЖЕНИЕ! AI выиграл! 😭");
                            Toast.makeText(MainActivity.this, "AI победил!", Toast.LENGTH_LONG).show();
                            return;
                        }

                        tvStatus.setText("🔥 AI попал! Продолжает...");
                        handler.postDelayed(() -> makeAIMove(), 600);
                    } else {
                        Toast.makeText(MainActivity.this, "💧 AI промахнулся! (" + (row + 1) + "," + (col + 1) + ")", Toast.LENGTH_SHORT).show();
                        drawPlayerBoard();
                        isPlayerTurn = true;
                        tvStatus.setText("👨 Ваш ход! Нажмите на поле AI");
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Ошибка AI: " + error);
                    makeSmartAIMove();
                });
            }
        });
    }

    private void makeSmartAIMove() {
        Log.d(TAG, "=== makeSmartAIMove: эвристика ===");

        if (!gameActive || isPlayerTurn) return;

        // Умный поиск
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (playerBoard.getCell(i, j) == GameBoard.CellState.HIT) {
                    int[][] neighbors = {{i-1,j}, {i+1,j}, {i,j-1}, {i,j+1}};
                    for (int[] n : neighbors) {
                        if (n[0] >= 0 && n[0] < 10 && n[1] >= 0 && n[1] < 10 &&
                                !playerBoard.isCellHit(n[0], n[1])) {
                            executeAIShoot(n[0], n[1]);
                            return;
                        }
                    }
                }
            }
        }

        // Шахматный порядок
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (!playerBoard.isCellHit(i, j) && (i + j) % 2 == 0) {
                    executeAIShoot(i, j);
                    return;
                }
            }
        }

        // Любая доступная
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (!playerBoard.isCellHit(i, j)) {
                    executeAIShoot(i, j);
                    return;
                }
            }
        }
    }

    private void executeAIShoot(int row, int col) {
        boolean hit = playerBoard.shoot(row, col);

        if (hit) {
            GameModels.Ship hitShip = playerBoard.getShipAt(row, col);
            if (hitShip != null && hitShip.isSunk()) {
                aiScore += hitShip.length;
                tvAIScore.setText("🤖 AI: " + aiScore);
                Toast.makeText(this, "💀 AI уничтожил ваш корабль!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "🎯 AI попал в (" + (row + 1) + "," + (col + 1) + ")", Toast.LENGTH_SHORT).show();
            }

            drawPlayerBoard();

            if (playerBoard.allShipsSunk()) {
                gameActive = false;
                tvStatus.setText("😭 ПОРАЖЕНИЕ! AI выиграл! 😭");
                return;
            }

            handler.postDelayed(this::makeSmartAIMove, 600);
        } else {
            Toast.makeText(this, "💧 AI промахнулся! (" + (row + 1) + "," + (col + 1) + ")", Toast.LENGTH_SHORT).show();
            drawPlayerBoard();
            isPlayerTurn = true;
            tvStatus.setText("👨 Ваш ход! Нажмите на поле AI");
        }
    }

    private void resetGame() {
        Log.d(TAG, "=== СБРОС ИГРЫ ===");
        playerScore = 0;
        aiScore = 0;
        isPlayerTurn = true;
        gameActive = true;
        tvPlayerScore.setText("👨 Игрок: 0");
        tvAIScore.setText("🤖 AI: 0");
        tvStatus.setText("Новая игра! 🎮");

        handler.removeCallbacksAndMessages(null);
        initGame();
    }
}