package com.thiaguinho.controlebar;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Controla a licença DEMO/PRO sem tocar no banco SQLite.
 * Portanto, ativar a versão PRO nunca apaga produtos, estoque ou vendas.
 */
public final class LicenseManager {
    private static final String PREFS = "controle_bar_license_v1";
    private static final String KEY_PRO = "pro_enabled";
    private static final String KEY_TRIAL_START = "trial_started_at";
    private static final String KEY_LAST_SEEN = "last_seen_at";
    private static final long TRIAL_DURATION_MS = TimeUnit.DAYS.toMillis(3);

    // Senha administrativa armazenada somente como hash SHA-256.
    private static final String UNLOCK_HASH = "519283ee8c599346e78a855664e42b8d2ceb37bb0a13b3d4e5b2849c7bd1de4d";

    private final SharedPreferences prefs;

    public LicenseManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isPro() {
        return prefs.getBoolean(KEY_PRO, false);
    }

    public boolean isTrialStarted() {
        return prefs.getLong(KEY_TRIAL_START, 0L) > 0L;
    }

    public void startTrialIfNeeded() {
        if (isPro() || isTrialStarted()) return;
        long now = System.currentTimeMillis();
        prefs.edit()
                .putLong(KEY_TRIAL_START, now)
                .putLong(KEY_LAST_SEEN, now)
                .apply();
    }

    /**
     * Usa o maior horário já visto para reduzir a possibilidade de voltar o relógio
     * do aparelho e reabrir uma demonstração vencida.
     */
    private long trustedNow() {
        long systemNow = System.currentTimeMillis();
        long lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L);
        long trusted = Math.max(systemNow, lastSeen);
        if (trusted > lastSeen) prefs.edit().putLong(KEY_LAST_SEEN, trusted).apply();
        return trusted;
    }

    public boolean isExpired() {
        if (isPro() || !isTrialStarted()) return false;
        return trustedNow() >= getTrialEndsAt();
    }

    public long getTrialStartedAt() {
        return prefs.getLong(KEY_TRIAL_START, 0L);
    }

    public long getTrialEndsAt() {
        long start = getTrialStartedAt();
        return start <= 0L ? 0L : start + TRIAL_DURATION_MS;
    }

    public int daysRemaining() {
        if (isPro()) return Integer.MAX_VALUE;
        if (!isTrialStarted()) return 3;
        long remaining = Math.max(0L, getTrialEndsAt() - trustedNow());
        return (int) Math.ceil(remaining / (double) TimeUnit.DAYS.toMillis(1));
    }

    public String statusLabel() {
        if (isPro()) return "PRO";
        if (!isTrialStarted()) return "DEMO pronta";
        if (isExpired()) return "DEMO encerrada";
        int days = daysRemaining();
        return "DEMO • " + days + (days == 1 ? " dia" : " dias");
    }

    public String formattedStart() {
        return format(getTrialStartedAt());
    }

    public String formattedEnd() {
        return format(getTrialEndsAt());
    }

    private String format(long value) {
        if (value <= 0L) return "Ainda não iniciada";
        return new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"))
                .format(new Date(value));
    }

    public boolean unlock(String password) {
        String enteredHash = sha256(password == null ? "" : password.trim());
        if (!UNLOCK_HASH.equals(enteredHash)) return false;
        prefs.edit().putBoolean(KEY_PRO, true).apply();
        return true;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b));
            return out.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
