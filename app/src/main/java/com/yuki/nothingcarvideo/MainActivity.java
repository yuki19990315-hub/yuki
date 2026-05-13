package com.yuki.nothingcarvideo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String YOUTUBE_URL = "https://m.youtube.com/";
    private static final String PRIME_VIDEO_URL = "https://www.primevideo.com/";
    private static final String DEFAULT_URL = YOUTUBE_URL;

    private WebView webView;
    private FrameLayout root;
    private LinearLayout toolbar;
    private EditText addressField;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean screenDimmed;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> { };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        buildUi();
        configureWebView();
        setContentView(root);
        enterImmersiveMode();

        if (savedInstanceState == null) {
            webView.loadUrl(DEFAULT_URL);
            addressField.setText(DEFAULT_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(8), dp(12), dp(8));
        toolbar.setBackgroundColor(Color.argb(220, 17, 17, 17));

        addressField = new EditText(this);
        addressField.setSingleLine(true);
        addressField.setTextColor(Color.WHITE);
        addressField.setHintTextColor(Color.LTGRAY);
        addressField.setHint("YouTube URL / 検索ワード");
        addressField.setTextSize(18);
        addressField.setSelectAllOnFocus(true);
        addressField.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressField.setBackgroundResource(R.drawable.field_background);
        addressField.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                loadFromAddressField();
                return true;
            }
            return false;
        });

        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        toolbar.addView(addressField, fieldParams);

        Button goButton = makeButton("再生");
        goButton.setOnClickListener(v -> loadFromAddressField());
        toolbar.addView(goButton, buttonParams());

        Button youtubeButton = makeButton("YouTube");
        youtubeButton.setOnClickListener(v -> loadUrl(YOUTUBE_URL));
        toolbar.addView(youtubeButton, buttonParams());

        Button primeButton = makeButton("Prime");
        primeButton.setOnClickListener(v -> loadUrl(PRIME_VIDEO_URL));
        toolbar.addView(primeButton, buttonParams());

        Button accountButton = makeButton("外部");
        accountButton.setOnClickListener(v -> openCurrentPageExternally());
        toolbar.addView(accountButton, buttonParams());

        Button dimButton = makeButton("暗く");
        dimButton.setOnClickListener(v -> toggleScreenBrightness(dimButton));
        toolbar.addView(dimButton, buttonParams());

        Button hideButton = makeButton("隠す");
        hideButton.setOnClickListener(v -> toolbar.setVisibility(View.GONE));
        toolbar.addView(hideButton, buttonParams());

        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        root.addView(toolbar, toolbarParams);

        root.setOnClickListener(v -> {
            if (toolbar.getVisibility() != View.VISIBLE) {
                toolbar.setVisibility(View.VISIBLE);
            }
        });
        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && toolbar.getVisibility() != View.VISIBLE) {
                toolbar.setVisibility(View.VISIBLE);
            }
            return false;
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setOffscreenPreRaster(false);
        settings.setUserAgentString(settings.getUserAgentString() + " YukiCarVideo/0.2");

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        applyLowLatencyAudioHints();

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                addressField.setText(url);
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                addressField.setText(url);
                super.onPageFinished(view, url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                showFullscreenVideo(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenVideo();
            }
        });
    }

    private void loadFromAddressField() {
        String input = addressField.getText().toString().trim();
        if (input.isEmpty()) {
            input = DEFAULT_URL;
        }
        loadUrl(normalizeInput(input));
        hideKeyboard();
    }

    private String normalizeInput(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input;
        }
        if (input.startsWith("youtu.be/") || input.startsWith("youtube.com/") || input.startsWith("m.youtube.com/")
                || input.startsWith("primevideo.com/") || input.startsWith("www.primevideo.com/")) {
            return "https://" + input;
        }
        return "https://m.youtube.com/results?search_query=" + Uri.encode(input);
    }

    private void loadUrl(String url) {
        addressField.setText(url);
        webView.loadUrl(url);
    }

    private void openCurrentPageExternally() {
        String url = addressField.getText().toString().trim();
        if (url.isEmpty()) {
            url = DEFAULT_URL;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(normalizeInput(url)));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "開けるアプリが見つかりません", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleScreenBrightness(Button dimButton) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        screenDimmed = !screenDimmed;
        params.screenBrightness = screenDimmed ? 0.02f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(params);
        dimButton.setText(screenDimmed ? "明るく" : "暗く");
        Toast.makeText(this, screenDimmed ? "スマホ画面だけ暗めにします" : "明るさを端末設定に戻します", Toast.LENGTH_SHORT).show();
    }

    private void showFullscreenVideo(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        toolbar.setVisibility(View.GONE);
        root.addView(customView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        enterImmersiveMode();
    }

    private void hideFullscreenVideo() {
        if (customView == null) {
            return;
        }
        root.removeView(customView);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        enterImmersiveMode();
    }

    private void applyLowLatencyAudioHints() {
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.button_background);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(96), dp(52));
        params.leftMargin = dp(8);
        return params;
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(addressField.getWindowToken(), 0);
        }
        addressField.clearFocus();
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideFullscreenVideo();
        } else if (toolbar.getVisibility() != View.VISIBLE) {
            toolbar.setVisibility(View.VISIBLE);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
