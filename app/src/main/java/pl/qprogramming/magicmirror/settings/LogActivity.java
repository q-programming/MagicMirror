package pl.qprogramming.magicmirror.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import pl.qprogramming.magicmirror.R;

/**
 * Displays the application's own logcat output (errors, failed requests, expired keys etc.).
 */
public class LogActivity extends AppCompatActivity {
    private static final String TAG = LogActivity.class.getSimpleName();
    private static final int MAX_LINES = 20000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView logsText;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log_activity);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        logsText = findViewById(R.id.logs_text);
        scroll = findViewById(R.id.logs_scroll);
        findViewById(R.id.logs_refresh).setOnClickListener(v -> loadLogs());
        findViewById(R.id.logs_copy).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("logs", logsText.getText()));
            Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show();
        });
        loadLogs();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadLogs() {
        executor.execute(() -> {
            String logs = readLogcat();
            runOnUiThread(() -> {
                logsText.setText(logs.isEmpty() ? getString(R.string.logs_empty) : logs);
                scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
            });
        });
    }

    /**
     * An app can read its own logs without extra permissions.
     */
    private String readLogcat() {
        StringBuilder result = new StringBuilder();
        Process process = null;
        try {
            process = new ProcessBuilder("logcat", "-d", "-v", "time", "-t", String.valueOf(MAX_LINES))
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append('\n');
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read logs", e);
            result.append("Failed to read logs: ").append(e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return result.toString();
    }
}
