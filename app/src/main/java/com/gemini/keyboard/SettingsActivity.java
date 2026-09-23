package com.gemini.keyboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SettingsActivity extends AppCompatActivity {
    private static final int REQ_MIC = 200;
    private EditText apiKeyEdit;
    private EditText modelEdit;
    private EditText promptEdit;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Gemini Keyboard Setup");

        prefs = getSharedPreferences("gemini_prefs", MODE_PRIVATE);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView header = new TextView(this);
        header.setText("Configure your Gemini AI Voice Keyboard.\nYour API key stays safe on your device.\n");
        header.setTextSize(14);
        header.setTextColor(Color.DKGRAY);
        root.addView(header);

        TextView keyLabel = new TextView(this);
        keyLabel.setText("1. Gemini API Key:");
        keyLabel.setTextSize(16);
        root.addView(keyLabel);

        apiKeyEdit = new EditText(this);
        apiKeyEdit.setHint("Paste your Google AI Studio API key");
        apiKeyEdit.setText(prefs.getString("api_key", ""));
        root.addView(apiKeyEdit);

        TextView modelLabel = new TextView(this);
        modelLabel.setText("\n2. Gemini Model:");
        modelLabel.setTextSize(16);
        root.addView(modelLabel);

        modelEdit = new EditText(this);
        modelEdit.setHint("e.g. gemini-2.0-flash");
        modelEdit.setText(prefs.getString("model_name", "gemini-2.0-flash"));
        root.addView(modelEdit);

        TextView promptLabel = new TextView(this);
        promptLabel.setText("\n3. Voice Prompt (Custom instructions):");
        promptLabel.setTextSize(16);
        root.addView(promptLabel);

        promptEdit = new EditText(this);
        promptEdit.setText(prefs.getString("prompt", "Transcribe the audio verbatim in the spoken language with proper punctuation. Output ONLY the raw transcript text with no explanations, no filler words, and no markdown."));
        root.addView(promptEdit);

        Button saveBtn = new Button(this);
        saveBtn.setText("💾 Save Configuration");
        saveBtn.setOnClickListener(v -> {
            prefs.edit()
                .putString("api_key", apiKeyEdit.getText().toString().trim())
                .putString("model_name", modelEdit.getText().toString().trim())
                .putString("prompt", promptEdit.getText().toString().trim())
                .apply();
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
        });
        root.addView(saveBtn);

        Button permBtn = new Button(this);
        permBtn.setText("🎙️ Step A: Grant Mic Permission");
        permBtn.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            } else {
                Toast.makeText(this, "Microphone permission is already granted!", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(permBtn);

        Button enableBtn = new Button(this);
        enableBtn.setText("⌨️ Step B: Enable in System Keyboards");
        enableBtn.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        });
        root.addView(enableBtn);

        Button switchBtn = new Button(this);
        switchBtn.setText("✨ Step C: Select Active Keyboard");
        switchBtn.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });
        root.addView(switchBtn);

        scrollView.addView(root);
        setContentView(scrollView);
    }
}
