package com.gemini.keyboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GeminiInputMethodService extends InputMethodService {
    private boolean isCaps = false;
    private boolean isNumberMode = false;
    private boolean isRecording = false;

    private LinearLayout keysContainer;
    private Button micButton;
    private TextView statusText;
    private Handler mainHandler;
    private MediaRecorder mediaRecorder;
    private final List<Button> letterButtons = new ArrayList<>();

    private final String[][] qwertyLetters = {
        {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
        {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
        {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"},
        {"123", ",", "SPACE", ".", "ENTER"}
    };

    private final String[][] qwertyNumbers = {
        {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
        {"@", "#", "$", "%", "&", "*", "-", "+", "(", ")"},
        {"ABC", "!", "\"", "'", ":", ";", "/", "?", "DEL"},
        {"ABC", ",", "SPACE", ".", "ENTER"}
    };

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public View onCreateInputView() {
        mainHandler = new Handler(Looper.getMainLooper());

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#18181B"));
        rootLayout.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(6));

        // Toolbar (Mic, Status, Settings, Switcher)
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(4));

        micButton = new Button(this);
        micButton.setText("🎙️ Voice");
        micButton.setTextColor(Color.WHITE);
        micButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable micBg = new GradientDrawable();
        micBg.setColor(Color.parseColor("#0284C7"));
        micBg.setCornerRadius(dpToPx(6));
        micButton.setBackground(micBg);
        micButton.setOnClickListener(v -> toggleVoiceTyping());
        toolbar.addView(micButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(38)));

        statusText = new TextView(this);
        statusText.setText("Ready");
        statusText.setTextColor(Color.parseColor("#A1A1AA"));
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusText.setGravity(Gravity.CENTER);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams stParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        stParams.setMargins(dpToPx(6), 0, dpToPx(6), 0);
        toolbar.addView(statusText, stParams);

        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.parseColor("#27272A"));
        iconBg.setCornerRadius(dpToPx(6));

        Button settingsBtn = new Button(this);
        settingsBtn.setText("⚙️");
        settingsBtn.setTextColor(Color.WHITE);
        settingsBtn.setBackground(iconBg);
        settingsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        toolbar.addView(settingsBtn, new LinearLayout.LayoutParams(dpToPx(42), dpToPx(38)));

        Button switchImeBtn = new Button(this);
        switchImeBtn.setText("🌐");
        switchImeBtn.setTextColor(Color.WHITE);
        GradientDrawable switchBg = new GradientDrawable();
        switchBg.setColor(Color.parseColor("#27272A"));
        switchBg.setCornerRadius(dpToPx(6));
        switchImeBtn.setBackground(switchBg);
        LinearLayout.LayoutParams swParams = new LinearLayout.LayoutParams(dpToPx(42), dpToPx(38));
        swParams.leftMargin = dpToPx(4);
        switchImeBtn.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        toolbar.addView(switchImeBtn, swParams);

        rootLayout.addView(toolbar);

        // Keys
        keysContainer = new LinearLayout(this);
        keysContainer.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(keysContainer);

        renderKeyboard();
        return rootLayout;
    }

    private void renderKeyboard() {
        if (keysContainer == null) return;
        keysContainer.removeAllViews();
        letterButtons.clear();

        String[][] currentLayout = isNumberMode ? qwertyNumbers : qwertyLetters;

        for (String[] row : currentLayout) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);

            for (String key : row) {
                float weight = 1.0f;
                if (key.equals("SPACE")) weight = 4.0f;
                else if (key.equals("SHIFT") || key.equals("DEL") || key.equals("123") || key.equals("ABC") || key.equals("ENTER")) {
                    weight = 1.5f;
                }

                Button btn = createKey(key, weight);
                if (key.length() == 1 && Character.isLetter(key.charAt(0))) {
                    letterButtons.add(btn);
                    btn.setText(isCaps ? key.toUpperCase() : key.toLowerCase());
                }
                rowLayout.addView(btn);
            }
            keysContainer.addView(rowLayout);
        }
    }

    private Button createKey(String keyText, float weight) {
        Button btn = new Button(this);
        btn.setText(keyText);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);

        GradientDrawable gd = new GradientDrawable();
        if (keyText.equals("ENTER")) {
            gd.setColor(Color.parseColor("#0284C7"));
        } else if (keyText.equals("SHIFT") || keyText.equals("DEL") || keyText.equals("123") || keyText.equals("ABC")) {
            gd.setColor(Color.parseColor("#27272A"));
        } else {
            gd.setColor(Color.parseColor("#3F3F46"));
        }
        gd.setCornerRadius(dpToPx(5));
        btn.setBackground(gd);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(48), weight);
        params.setMargins(dpToPx(2), dpToPx(3), dpToPx(2), dpToPx(3));
        btn.setLayoutParams(params);
        btn.setPadding(0, 0, 0, 0);

        btn.setOnClickListener(v -> handleKeyPress(keyText));
        return btn;
    }

    private void handleKeyPress(String key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (key) {
            case "DEL":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                break;
            case "ENTER":
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                break;
            case "SPACE":
                ic.commitText(" ", 1);
                break;
            case "SHIFT":
                isCaps = !isCaps;
                for (Button b : letterButtons) {
                    String s = b.getText().toString();
                    b.setText(isCaps ? s.toUpperCase() : s.toLowerCase());
                }
                break;
            case "123":
            case "ABC":
                isNumberMode = !isNumberMode;
                renderKeyboard();
                break;
            default:
                ic.commitText(isCaps ? key.toUpperCase() : key.toLowerCase(), 1);
                break;
        }
    }

    private void toggleVoiceTyping() {
        if (isRecording) {
            stopRecordingAndSend();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText.setText("⚠️ Mic permission missing! Tap ⚙️");
            return;
        }

        try {
            File audioFile = new File(getCacheDir(), "voice_input.m4a");
            if (audioFile.exists()) audioFile.delete();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(16000);
            mediaRecorder.setAudioEncodingBitRate(32000);
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            micButton.setText("⏹️ Stop");
            statusText.setText("🔴 Listening... Tap Stop when done");
        } catch (Exception e) {
            statusText.setText("Mic Error: " + e.getMessage());
            isRecording = false;
            micButton.setText("🎙️ Voice");
        }
    }

    private void stopRecordingAndSend() {
        isRecording = false;
        micButton.setText("🎙️ Voice");
        statusText.setText("⏳ Transcribing...");

        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (RuntimeException ignored) {
                statusText.setText("⚠️ Tap held too short");
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
                return;
            }
            try {
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
        }

        sendAudioToGemini();
    }

    private void sendAudioToGemini() {
        SharedPreferences prefs = getSharedPreferences("gemini_prefs", MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", "").trim();
        String modelName = prefs.getString("model_name", "gemini-2.0-flash").trim();
        String prompt = prefs.getString("prompt", "Transcribe the audio verbatim with correct punctuation.").trim();

        if (apiKey.isEmpty()) {
            statusText.setText("⚠️ API Key missing! Tap ⚙️");
            return;
        }

        new Thread(() -> {
            try {
                File audioFile = new File(getCacheDir(), "voice_input.m4a");
                if (!audioFile.exists() || audioFile.length() == 0) {
                    mainHandler.post(() -> statusText.setText("⚠️ No audio detected"));
                    return;
                }

                byte[] audioBytes = readFileToBytes(audioFile);
                if (audioBytes == null || audioBytes.length == 0) {
                    mainHandler.post(() -> statusText.setText("⚠️ File read error"));
                    return;
                }

                String base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP);

                JSONObject requestJson = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray parts = new JSONArray();

                JSONObject textPart = new JSONObject();
                textPart.put("text", prompt);
                parts.put(textPart);

                JSONObject inlineData = new JSONObject();
                inlineData.put("mimeType", "audio/mp4");
                inlineData.put("data", base64Audio);

                JSONObject audioPart = new JSONObject();
                audioPart.put("inlineData", inlineData);
                parts.put(audioPart);

                content.put("parts", parts);
                contents.put(content);
                requestJson.put("contents", contents);

                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", 0.0);
                requestJson.put("generationConfig", genConfig);

                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder responseBuilder = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) responseBuilder.append(line);
                }

                String respStr = responseBuilder.toString();
                if (responseCode != 200) {
                    String errMessage = "HTTP " + responseCode;
                    try {
                        JSONObject errJson = new JSONObject(respStr);
                        if (errJson.has("error")) {
                            errMessage = errJson.getJSONObject("error").optString("message", errMessage);
                        }
                    } catch (Exception ignored) {}
                    final String errFinal = errMessage;
                    mainHandler.post(() -> statusText.setText("❌ " + errFinal));
                    return;
                }

                JSONObject respJson = new JSONObject(respStr);
                JSONArray candidates = respJson.optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    JSONObject cand = candidates.getJSONObject(0);
                    JSONObject candContent = cand.optJSONObject("content");
                    if (candContent != null) {
                        JSONArray candParts = candContent.optJSONArray("parts");
                        if (candParts != null && candParts.length() > 0) {
                            String text = candParts.getJSONObject(0).optString("text", "").trim();
                            mainHandler.post(() -> {
                                InputConnection ic = getCurrentInputConnection();
                                if (ic != null && !text.isEmpty()) {
                                    ic.commitText(text + " ", 1);
                                }
                                statusText.setText("✅ Ready");
                            });
                            return;
                        }
                    }
                }
                mainHandler.post(() -> statusText.setText("No speech recognized"));
            } catch (Exception e) {
                mainHandler.post(() -> statusText.setText("❌ " + e.getMessage()));
            }
        }).start();
    }

    private byte[] readFileToBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) bos.write(buffer, 0, read);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaRecorder != null) {
            try { mediaRecorder.release(); } catch (Exception ignored) {}
            mediaRecorder = null;
        }
    }
}
