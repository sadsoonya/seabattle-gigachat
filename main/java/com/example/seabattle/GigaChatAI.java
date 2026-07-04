package com.example.seabattle;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class GigaChatAI {
    private static final String TAG = "GigaChatAI";
    private static final String CLIENT_ID = "019cade7-f4c8-7e6a-ab02-d2fd0ce6faff";
    private static final String CLIENT_SECRET = "14fa5665-5b27-451c-9dc8-3a542efb0889";
    private static final String GIGACHAT_SCOPE = "GIGACHAT_API_PERS";
    private static final String GIGACHAT_AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private static final String GIGACHAT_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";

    private OkHttpClient client;
    private String accessToken;
    private Context context;
    private Gson gson;
    private long tokenExpiryTime = 0;

    public interface AIMoveCallback {
        void onMove(int row, int col);
        void onError(String error);
    }

    public GigaChatAI(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.client = getUnsafeOkHttpClient();
        Log.d(TAG, "GigaChatAI инициализирован с unsafe SSL");
        authenticate();
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Создаем TrustManager, который доверяет всем сертификатам
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // Устанавливаем SSL контекст
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            // Создаем фабрику SSL сокетов
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
            builder.connectTimeout(30, TimeUnit.SECONDS);
            builder.readTimeout(30, TimeUnit.SECONDS);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void authenticate() {
        Log.d(TAG, "Начало аутентификации...");
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String basicAuth = "Basic " + android.util.Base64.encodeToString(credentials.getBytes(), android.util.Base64.NO_WRAP);

        RequestBody body = new FormBody.Builder()
                .add("scope", GIGACHAT_SCOPE)
                .build();

        Request request = new Request.Builder()
                .url(GIGACHAT_AUTH_URL)
                .addHeader("Authorization", basicAuth)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("RqUID", UUID.randomUUID().toString())
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Ошибка аутентификации: " + e.getMessage());
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String json = response.body().string();
                        JSONObject obj = new JSONObject(json);
                        accessToken = obj.getString("access_token");

                        // Токен живет примерно 30 минут
                        tokenExpiryTime = System.currentTimeMillis() + 30 * 60 * 1000;

                        Log.d(TAG, "Аутентификация успешна! Токен получен: " + accessToken.substring(0, Math.min(20, accessToken.length())) + "...");
                        Log.d(TAG, "Токен действителен до: " + new Date(tokenExpiryTime));
                    } catch (JSONException e) {
                        Log.e(TAG, "Ошибка парсинга JSON при аутентификации", e);
                    }
                } else {
                    Log.e(TAG, "Ошибка аутентификации, код: " + response.code());
                    try {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Тело ошибки: " + errorBody);
                    } catch (Exception e) {
                        Log.e(TAG, "Не удалось прочитать тело ошибки", e);
                    }
                }
            }
        });
    }

    private void refreshTokenIfNeeded() {
        if (accessToken == null || System.currentTimeMillis() >= tokenExpiryTime - 5 * 60 * 1000) {
            Log.d(TAG, "Токен устарел или отсутствует, обновляем...");
            authenticate();
            try {
                Thread.sleep(2000); // Ждем получения токена
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void makeMove(GameBoard board, AIMoveCallback callback) {
        Log.d(TAG, "=== makeMove вызван ===");

        refreshTokenIfNeeded();

        if (accessToken == null) {
            Log.w(TAG, "Токен отсутствует, используем эвристику");
            makeMinimaxMove(board, callback);
            return;
        }

        try {
            String prompt = createPrompt(board);
            Log.d(TAG, "Prompt для AI (первые 200 символов): " + prompt.substring(0, Math.min(200, prompt.length())));

            JSONObject messages = new JSONObject();
            JSONArray messageArray = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messageArray.put(userMessage);
            messages.put("messages", messageArray);
            messages.put("model", "GigaChat");
            messages.put("temperature", 0.7);
            messages.put("max_tokens", 100);

            Log.d(TAG, "Отправка запроса к GigaChat API...");

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    messages.toString()
            );

            Request request = new Request.Builder()
                    .url(GIGACHAT_URL)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Ошибка сети при запросе к GigaChat: " + e.getMessage());
                    e.printStackTrace();
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String json = response.body().string();
                            Log.d(TAG, "Ответ от GigaChat получен");

                            JSONObject obj = new JSONObject(json);
                            JSONArray choices = obj.getJSONArray("choices");
                            String aiResponse = choices.getJSONObject(0).getJSONObject("message").getString("content");

                            Log.d(TAG, "Ответ AI: " + aiResponse);

                            int[] move = parseAIMove(aiResponse, board);
                            if (move != null) {
                                Log.d(TAG, "Успешно распарсены координаты: (" + move[0] + "," + move[1] + ")");
                                callback.onMove(move[0], move[1]);
                            } else {
                                Log.w(TAG, "Не удалось распарсить ответ AI, используем эвристику");
                                makeMinimaxMove(board, callback);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Ошибка парсинга JSON ответа", e);
                            callback.onError("Parse error: " + e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "Ошибка API, код: " + response.code());
                        try {
                            String errorBody = response.body().string();
                            Log.e(TAG, "Тело ошибки: " + errorBody);
                        } catch (Exception e) {
                            Log.e(TAG, "Не удалось прочитать тело ошибки", e);
                        }
                        callback.onError("API error: " + response.code());
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Ошибка создания JSON запроса", e);
            callback.onError("Request creation error: " + e.getMessage());
        }
    }

    private String createPrompt(GameBoard board) {
        StringBuilder boardState = new StringBuilder();
        boardState.append("Игровое поле 10x10. 0 - пустая клетка, 1 - подбитый корабль, 2 - промах.\n");
        boardState.append("Текущее состояние поля (мои выстрелы):\n");

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                GameBoard.CellState state = board.getCell(i, j);
                if (state == GameBoard.CellState.HIT) {
                    boardState.append("1 ");
                } else if (state == GameBoard.CellState.MISS) {
                    boardState.append("2 ");
                } else {
                    boardState.append("0 ");
                }
            }
            boardState.append("\n");
        }

        boardState.append("\nПравила игры Морской бой:\n");
        boardState.append("- Корабли: 4-палубный(1), 3-палубные(2), 2-палубные(3), 1-палубные(4)\n");
        boardState.append("- Стратегия: сначала ищи неподбитые клетки, стреляй в шахматном порядке\n");
        boardState.append("- При попадании стреляй по соседним клеткам\n");
        boardState.append("- Отвечай только координатами в формате: row,col (0-9,0-9)\n");
        boardState.append("Твой ход: ");

        return boardState.toString();
    }

    private int[] parseAIMove(String response, GameBoard board) {
        try {
            Log.d(TAG, "Парсинг ответа: " + response);

            // Ищем паттерн типа "row,col" или "row, col"
            String cleaned = response.replaceAll("[^0-9,]", "");
            String[] parts = cleaned.split(",");

            if (parts.length >= 2) {
                int row = Integer.parseInt(parts[0].trim());
                int col = Integer.parseInt(parts[1].trim());
                if (row >= 0 && row < 10 && col >= 0 && col < 10 && !board.isCellHit(row, col)) {
                    Log.d(TAG, "Найдены координаты: " + row + "," + col);
                    return new int[]{row, col};
                }
            }

            // Поиск чисел через регулярное выражение
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+");
            java.util.regex.Matcher matcher = pattern.matcher(response);
            int[] coords = new int[2];
            int index = 0;
            while (matcher.find() && index < 2) {
                coords[index++] = Integer.parseInt(matcher.group());
            }
            if (index == 2) {
                int row = coords[0], col = coords[1];
                if (row >= 0 && row < 10 && col >= 0 && col < 10 && !board.isCellHit(row, col)) {
                    Log.d(TAG, "Найдены координаты через regex: " + row + "," + col);
                    return new int[]{row, col};
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка парсинга ответа", e);
        }
        return null;
    }

    private void makeMinimaxMove(GameBoard board, AIMoveCallback callback) {
        Log.d(TAG, "Используем эвристическую стратегию");
        List<int[]> availableMoves = new ArrayList<>();

        // 1. Сначала ищем клетки вокруг подбитых кораблей
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (board.getCell(i, j) == GameBoard.CellState.HIT) {
                    int[][] neighbors = {{i-1,j}, {i+1,j}, {i,j-1}, {i,j+1}};
                    for (int[] n : neighbors) {
                        if (n[0] >= 0 && n[0] < 10 && n[1] >= 0 && n[1] < 10 &&
                                !board.isCellHit(n[0], n[1])) {
                            availableMoves.add(new int[]{n[0], n[1]});
                        }
                    }
                }
            }
        }

        // 2. Если нет подбитых, стреляем в шахматном порядке
        if (availableMoves.isEmpty()) {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    if (!board.isCellHit(i, j) && (i + j) % 2 == 0) {
                        availableMoves.add(new int[]{i, j});
                    }
                }
            }
        }

        // 3. Если все еще пусто, берем любую неподбитую клетку
        if (availableMoves.isEmpty()) {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    if (!board.isCellHit(i, j)) {
                        availableMoves.add(new int[]{i, j});
                    }
                }
            }
        }

        if (!availableMoves.isEmpty()) {
            int[] move = availableMoves.get(0);
            Log.d(TAG, "Эвристика выбрала ход: (" + move[0] + "," + move[1] + ")");
            callback.onMove(move[0], move[1]);
        } else {
            Log.e(TAG, "Нет доступных ходов!");
            callback.onError("No available moves");
        }
    }
}