package com.thiaguinho.controlebar;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class GuideActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingText = null;
    private String pendingUtteranceId = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);

        web.addJavascriptInterface(new GuideBridge(), "AndroidGuide");
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/guia.html");
        setContentView(web);

        tts = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) return;
        int result = tts.setLanguage(new Locale("pt", "BR"));
        tts.setSpeechRate(0.92f);
        tts.setPitch(1.0f);
        ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                notifyJavascript("onNativeSpeechStart", utteranceId);
            }

            @Override public void onDone(String utteranceId) {
                notifyJavascript("onNativeSpeechDone", utteranceId);
            }

            @Override public void onError(String utteranceId) {
                notifyJavascript("onNativeSpeechDone", utteranceId);
            }

            @Override public void onError(String utteranceId, int errorCode) {
                notifyJavascript("onNativeSpeechDone", utteranceId);
            }
        });

        if (ttsReady && pendingText != null && pendingUtteranceId != null) {
            String text = pendingText;
            String id = pendingUtteranceId;
            pendingText = null;
            pendingUtteranceId = null;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        }
    }

    private void notifyJavascript(String function, String utteranceId) {
        mainHandler.post(() -> {
            if (web == null) return;
            String safeId = utteranceId == null ? "" : utteranceId.replace("\\", "\\\\").replace("'", "\\'");
            web.evaluateJavascript("window." + function + " && window." + function + "('" + safeId + "');", null);
        });
    }

    private final class GuideBridge {
        @JavascriptInterface
        public void speak(String text, String utteranceId) {
            mainHandler.post(() -> {
                if (tts == null) {
                    notifyJavascript("onNativeSpeechUnavailable", utteranceId);
                    return;
                }
                if (!ttsReady) {
                    pendingText = text;
                    pendingUtteranceId = utteranceId;
                    mainHandler.postDelayed(() -> {
                        if (!ttsReady && utteranceId.equals(pendingUtteranceId)) {
                            pendingText = null;
                            pendingUtteranceId = null;
                            notifyJavascript("onNativeSpeechUnavailable", utteranceId);
                        }
                    }, 2500);
                    return;
                }
                pendingText = null;
                pendingUtteranceId = null;
                tts.stop();
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            });
        }

        @JavascriptInterface
        public void stopSpeaking() {
            mainHandler.post(() -> {
                pendingText = null;
                pendingUtteranceId = null;
                if (tts != null) tts.stop();
            });
        }

        @JavascriptInterface
        public boolean isVoiceReady() {
            return ttsReady;
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (web != null) {
            web.stopLoading();
            web.removeJavascriptInterface("AndroidGuide");
            web.destroy();
        }
        super.onDestroy();
    }
}
