package com.movtery.zalithlauncher.feature.discord;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.movtery.zalithlauncher.InfoDistributor;
import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.utils.ZHTools;
import com.movtery.zalithlauncher.utils.path.UrlManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Obsidian-Black Discord Linking Dialog styled identically to the Orbit Launcher UI.
 * Connects via Discord Application ID 1571705435713437726 using one-step Discord Account Sign-In
 * (capturing and API-verifying the authenticated session via Kizzy's /app redirect + WebView hooks)
 * so others on Discord see "Playing Orbit Launcher" on the user's profile.
 */
public final class DiscordLinkDialog {
    public static final String DISCORD_INVITE_URL = UrlManager.URL_DISCORD;
    public static final String DISCORD_APPLICATION_ID = DiscordPresenceManager.DISCORD_APPLICATION_ID;

    // Kizzy's proven Samsung Android 14 Chrome UA avoids "Unsupported Browser" errors on Motorola/custom ROMs
    // while keeping Discord on the mobile login flow that writes localStorage.token and navigates to /app.
    private static final String MOBILE_LOGIN_UA =
            "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.363";

    private static final String KIZZY_JS_SNIPPET =
            "javascript:(function(){try{var i=document.createElement('iframe');"
            + "i.style.display='none';document.body.appendChild(i);"
            + "var raw=i.contentWindow.localStorage.token;"
            + "i.remove();"
            + "if(raw){var tk=String(raw).replace(/^\"|\"$/g,'').trim();"
            + "if(window.GurkhaDiscordBridge&&window.GurkhaDiscordBridge.onTokenCaptured){"
            + "window.GurkhaDiscordBridge.onTokenCaptured(tk);}"
            + "alert(tk);}}catch(e){}})()";

    private DiscordLinkDialog() {
    }

    public static void show(@NonNull Context context) {
        final DiscordPresenceManager manager = DiscordPresenceManager.getInstance(context);
        manager.syncFromDiskFile();
        final View view = LayoutInflater.from(context).inflate(R.layout.dialog_discord_link, null, false);

        final TextView statusBadge = view.findViewById(R.id.discord_status_badge);
        final Switch enableSwitch = view.findViewById(R.id.discord_enable_switch);
        final EditText discordIdInput = view.findViewById(R.id.discord_id_input);
        final Button webLoginBtn = view.findViewById(R.id.discord_web_login_button);
        final Button statusOnlineBtn = view.findViewById(R.id.status_online_btn);
        final Button statusIdleBtn = view.findViewById(R.id.status_idle_btn);
        final Button statusDndBtn = view.findViewById(R.id.status_dnd_btn);
        final EditText customStateInput = view.findViewById(R.id.discord_custom_state_input);
        final EditText webhookInput = view.findViewById(R.id.discord_webhook_input);
        final Button joinServerBtn = view.findViewById(R.id.discord_join_server_button);
        final Button unlinkBtn = view.findViewById(R.id.discord_unlink_button);
        final Button cancelBtn = view.findViewById(R.id.discord_cancel_button);
        final Button saveBtn = view.findViewById(R.id.discord_save_button);

        enableSwitch.setChecked(manager.isEnabled());
        String existingUser = manager.getLinkedUsername();
        String existingId = manager.getLinkedUserId();
        discordIdInput.setText(!existingUser.isEmpty() ? existingUser : existingId);
        customStateInput.setText(manager.getCustomState());
        webhookInput.setText(manager.getWebhookUrl());

        final String[] selectedStatus = new String[]{manager.getOnlineStatus()};
        final Runnable updateStatusPills = () -> {
            statusOnlineBtn.setSelected("online".equals(selectedStatus[0]));
            statusIdleBtn.setSelected("idle".equals(selectedStatus[0]));
            statusDndBtn.setSelected("dnd".equals(selectedStatus[0]));
            statusOnlineBtn.setAlpha("online".equals(selectedStatus[0]) ? 1.0f : 0.65f);
            statusIdleBtn.setAlpha("idle".equals(selectedStatus[0]) ? 1.0f : 0.65f);
            statusDndBtn.setAlpha("dnd".equals(selectedStatus[0]) ? 1.0f : 0.65f);
        };
        updateStatusPills.run();

        statusOnlineBtn.setOnClickListener(v -> {
            selectedStatus[0] = "online";
            updateStatusPills.run();
        });
        statusIdleBtn.setOnClickListener(v -> {
            selectedStatus[0] = "idle";
            updateStatusPills.run();
        });
        statusDndBtn.setOnClickListener(v -> {
            selectedStatus[0] = "dnd";
            updateStatusPills.run();
        });

        final Runnable refreshBanner = () -> {
            String user = manager.getLinkedUsername();
            if (!user.isEmpty() && discordIdInput.getText().toString().trim().isEmpty()) {
                discordIdInput.setText(user);
            }
            if (manager.isConnected()) {
                statusBadge.setText("● LIVE on Discord" + (user.isEmpty() ? "" : (" as @" + user))
                        + " • Playing " + InfoDistributor.APP_NAME + " (App ID: " + DISCORD_APPLICATION_ID + ")");
                statusBadge.setTextColor(Color.parseColor("#A8F0C6"));
                saveBtn.setText("Save & Update RPC");
            } else if (manager.isConnecting()) {
                statusBadge.setText("Connecting to Discord Gateway (App ID: " + DISCORD_APPLICATION_ID + ")...");
                statusBadge.setTextColor(Color.parseColor("#F5E09B"));
            } else if (!manager.getLastError().isEmpty()) {
                statusBadge.setText(manager.getLastError());
                statusBadge.setTextColor(Color.parseColor("#FFB4B4"));
                saveBtn.setText("Sign In & Connect RPC");
            } else {
                statusBadge.setText("Sign in with Discord below so friends & servers see \"Playing "
                        + InfoDistributor.APP_NAME + "\" on your profile");
                statusBadge.setTextColor(Color.parseColor("#B8BEC8"));
            }
        };
        refreshBanner.run();

        final DiscordPresenceManager.StatusListener liveListener = (summary, connected) -> refreshBanner.run();
        manager.addListener(liveListener);

        joinServerBtn.setOnClickListener(v -> ZHTools.openLink(context, DISCORD_INVITE_URL));

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.setOnDismissListener(d -> manager.removeListener(liveListener));

        final Runnable launchWebSignIn = () -> showWebLoginDialog(
                context,
                manager,
                discordIdInput.getText().toString().trim(),
                (userId, username, token) -> {
                    String displayId = (username != null && !username.isEmpty()) ? username : userId;
                    if (displayId != null && !displayId.isEmpty()) {
                        discordIdInput.setText(displayId);
                    }
                    enableSwitch.setChecked(true);
                    manager.onWebSessionAuthenticated(
                            userId,
                            username,
                            token,
                            selectedStatus[0],
                            customStateInput.getText().toString(),
                            webhookInput.getText().toString()
                    );
                    refreshBanner.run();
                    Toast.makeText(
                            context,
                            "Discord RPC connected! Now showing Playing " + InfoDistributor.APP_NAME,
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );

        webLoginBtn.setOnClickListener(v -> launchWebSignIn.run());

        unlinkBtn.setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            WebStorage.getInstance().deleteAllData();
            manager.unlink();
            Toast.makeText(context, "Discord unlinked", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        saveBtn.setOnClickListener(v -> {
            String enteredId = discordIdInput.getText().toString().trim();
            String enteredWebhook = webhookInput.getText().toString().trim();

            // If the user pasted a token directly into the box, verify & accept it
            if (DiscordPresenceManager.looksLikeDiscordToken(enteredId)) {
                manager.saveConfiguration(
                        "",
                        enteredId,
                        enteredWebhook,
                        selectedStatus[0],
                        customStateInput.getText().toString(),
                        enableSwitch.isChecked()
                );
                Toast.makeText(
                        context,
                        "Connecting to Discord Gateway • Playing " + InfoDistributor.APP_NAME,
                        Toast.LENGTH_SHORT
                ).show();
                dialog.dismiss();
                return;
            }

            // If no valid Discord session is authenticated yet (or previous session expired) and no webhook is set,
            // open the fresh Discord Sign-In dialog so the user can log in cleanly!
            if (!manager.hasAuthenticatedSession() && enteredWebhook.isEmpty()) {
                launchWebSignIn.run();
                return;
            }

            manager.saveConfiguration(
                    enteredId,
                    manager.getToken(),
                    enteredWebhook,
                    selectedStatus[0],
                    customStateInput.getText().toString(),
                    enableSwitch.isChecked()
            );
            Toast.makeText(
                    context,
                    enableSwitch.isChecked()
                            ? ("Discord RPC updated! Showing Playing " + InfoDistributor.APP_NAME)
                            : "Discord RPC paused",
                    Toast.LENGTH_SHORT
            ).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private interface WebAuthCallback {
        void onAuthenticated(String userId, String username, String sessionToken);
    }

    private static final String JS_INSTALL_INTERCEPTOR =
            "(function(){"
            + "try{"
            + "var report=function(tk){"
            + "if(!tk||typeof tk!=='string')return;"
            + "tk=tk.replace(/^\"|\"$/g,'').replace(/^Bearer\\s+/i,'').trim();"
            + "if(tk.length>=30&&tk!=='null'&&tk!=='undefined'"
            + "&&tk.indexOf('Basic ')!==0&&tk.indexOf('P1_')!==0&&tk.indexOf('eyJ')!==0){"
            + "window.__gurkhaToken=tk;"
            + "if(window.GurkhaDiscordBridge&&window.GurkhaDiscordBridge.onTokenCaptured){"
            + "window.GurkhaDiscordBridge.onTokenCaptured(tk);"
            + "}"
            + "}"
            + "};"
            + "if(!window.__gurkhaHooked){"
            + "window.__gurkhaHooked=true;"
            + "var origSetItem=Storage.prototype.setItem;"
            + "Storage.prototype.setItem=function(k,v){"
            + "if(k==='token'&&v)report(String(v));"
            + "return origSetItem.apply(this,arguments);"
            + "};"
            + "var origOpen=XMLHttpRequest.prototype.open;"
            + "XMLHttpRequest.prototype.open=function(m,u){"
            + "this.__gurkhaUrl=String(u||'');"
            + "return origOpen.apply(this,arguments);"
            + "};"
            + "var origSetHeader=XMLHttpRequest.prototype.setRequestHeader;"
            + "XMLHttpRequest.prototype.setRequestHeader=function(k,v){"
            + "if(k&&k.toLowerCase()==='authorization'&&v"
            + "&&this.__gurkhaUrl&&this.__gurkhaUrl.indexOf('/api/')!==-1){"
            + "report(String(v));"
            + "}"
            + "return origSetHeader.apply(this,arguments);"
            + "};"
            + "var origSend=XMLHttpRequest.prototype.send;"
            + "XMLHttpRequest.prototype.send=function(){"
            + "var self=this;"
            + "this.addEventListener('load',function(){"
            + "try{"
            + "var u=self.__gurkhaUrl||'';"
            + "if(u.indexOf('/auth/')!==-1&&self.responseText&&self.responseText.indexOf('\"token\"')!==-1){"
            + "var j=JSON.parse(self.responseText);"
            + "if(j&&j.token&&!j.mfa&&!j.ticket)report(String(j.token));"
            + "}"
            + "}catch(e){}"
            + "});"
            + "return origSend.apply(this,arguments);"
            + "};"
            + "if(window.fetch){"
            + "var origFetch=window.fetch;"
            + "window.fetch=function(input,init){"
            + "try{"
            + "var u=(typeof input==='string')?input:(input&&input.url?input.url:'');"
            + "if(u.indexOf('/api/')!==-1&&init&&init.headers){"
            + "var h=init.headers;"
            + "var a=(typeof h.get==='function')?(h.get('Authorization')||h.get('authorization')):(h.Authorization||h.authorization);"
            + "if(a)report(String(a));"
            + "}"
            + "}catch(e){}"
            + "return origFetch.apply(this,arguments);"
            + "};"
            + "}"
            + "}"
            + "}catch(e){}"
            + "})();";

    private static final String JS_POLL_AUTH =
            "(function(){"
            + "try{"
            + "var t=window.__gurkhaToken||'';"
            + "if(!t){"
            + "var f=document.createElement('iframe');"
            + "f.style.display='none';"
            + "document.body.appendChild(f);"
            + "var s=f.contentWindow.localStorage.getItem('token');"
            + "f.remove();"
            + "if(s)t=String(s).replace(/^\"|\"$/g,'').trim();"
            + "}"
            + "if(!t&&window.webpackChunkdiscord_app){"
            + "window.webpackChunkdiscord_app.push([[Math.random()],{},function(req){"
            + "for(var k in req.c){"
            + "var m=req.c[k];"
            + "if(m&&m.exports&&m.exports.default&&typeof m.exports.default.getToken==='function'){"
            + "var tk=m.exports.default.getToken();"
            + "if(tk)t=String(tk);"
            + "}"
            + "}"
            + "}]);"
            + "}"
            + "if(t&&t.length>=30&&t!=='null'&&t!=='undefined'&&t.indexOf('P1_')!==0&&t.indexOf('eyJ')!==0){"
            + "if(window.GurkhaDiscordBridge&&window.GurkhaDiscordBridge.onTokenCaptured){"
            + "window.GurkhaDiscordBridge.onTokenCaptured(String(t));"
            + "}"
            + "return String(t);"
            + "}"
            + "return '';"
            + "}catch(e){return '';}"
            + "})();";

    private static final class JsBridge {
        private final Handler mMainHandler = new Handler(Looper.getMainLooper());
        private final DiscordPresenceManager mManager;
        private final Runnable mOnComplete;
        private final WebAuthCallback mCallback;
        private final Set<String> mCheckedTokens = Collections.synchronizedSet(new HashSet<>());
        private volatile boolean mDone = false;

        JsBridge(DiscordPresenceManager manager, WebAuthCallback callback, Runnable onComplete) {
            this.mManager = manager;
            this.mCallback = callback;
            this.mOnComplete = onComplete;
        }

        @JavascriptInterface
        public void onTokenCaptured(String rawToken) {
            deliverToken(rawToken);
        }

        void deliverToken(@Nullable String rawToken) {
            if (mDone || rawToken == null) return;
            final String clean = DiscordPresenceManager.normalizeToken(cleanJsString(rawToken));
            if (!DiscordPresenceManager.looksLikeDiscordToken(clean)) {
                return;
            }
            if (!mCheckedTokens.add(clean)) {
                return;
            }
            mManager.verifyTokenAsync(clean, (valid, userId, username, verifiedToken) -> {
                if (!valid || mDone) {
                    return;
                }
                synchronized (JsBridge.this) {
                    if (mDone) return;
                    mDone = true;
                }
                mMainHandler.post(() -> {
                    mCallback.onAuthenticated(userId, username, verifiedToken);
                    mOnComplete.run();
                });
            });
        }

        boolean isDone() {
            return mDone;
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private static void showWebLoginDialog(
            @NonNull Context context,
            @NonNull DiscordPresenceManager manager,
            @Nullable String initialLoginHint,
            @NonNull WebAuthCallback callback
    ) {
        final float density = context.getResources().getDisplayMetrics().density;
        final int pad = (int) (10 * density);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.mc_panel);
        container.setPadding(pad, pad, pad, pad);

        TextView header = new TextView(context);
        header.setText("Sign in with Discord • App ID: " + DISCORD_APPLICATION_ID
                + "\nLog in with your Discord account below — Rich Presence connects automatically as soon as you sign in.");
        header.setTextColor(Color.parseColor("#F2F4F8"));
        header.setTextSize(12f);
        header.setPadding(pad, pad / 2, pad, pad);
        container.addView(header);

        final WebView webView = new WebView(context);
        // Clear any stale cookies/storage from previous expired sessions (Kizzy PR #512 fix)
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        cookieManager.removeAllCookies(null);
        cookieManager.flush();
        WebStorage.getInstance().deleteAllData();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUserAgentString(MOBILE_LOGIN_UA);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (360 * density)
        ));
        container.addView(webView);

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, pad, 0, 0);

        Button cancelBtn = new Button(context);
        cancelBtn.setBackgroundResource(R.drawable.mc_button);
        cancelBtn.setTextColor(Color.parseColor("#F2F4F8"));
        cancelBtn.setAllCaps(false);
        cancelBtn.setText(android.R.string.cancel);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                0,
                (int) (38 * density),
                1f
        );
        cancelLp.setMarginEnd(pad / 2);
        buttonRow.addView(cancelBtn, cancelLp);

        Button connectNowBtn = new Button(context);
        connectNowBtn.setBackgroundResource(R.drawable.mc_play);
        connectNowBtn.setTextColor(Color.parseColor("#0A0B0E"));
        connectNowBtn.setAllCaps(false);
        connectNowBtn.setText("Connect Current Session");
        LinearLayout.LayoutParams connectLp = new LinearLayout.LayoutParams(
                0,
                (int) (38 * density),
                1.4f
        );
        buttonRow.addView(connectNowBtn, connectLp);
        container.addView(buttonRow);

        final AlertDialog loginDialog = new AlertDialog.Builder(context)
                .setView(container)
                .create();
        Window window = loginDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        final JsBridge bridge = new JsBridge(manager, callback, () -> {
            if (loginDialog.isShowing()) {
                loginDialog.dismiss();
            }
        });
        webView.addJavascriptInterface(bridge, "GurkhaDiscordBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                if (message != null && !message.isEmpty()) {
                    bridge.deliverToken(message);
                }
                if (result != null) {
                    result.confirm();
                }
                return true;
            }
        });

        final Runnable pollTokenRunnable = new Runnable() {
            @Override
            public void run() {
                if (bridge.isDone() || !loginDialog.isShowing()) return;
                webView.evaluateJavascript(JS_INSTALL_INTERCEPTOR, null);
                webView.evaluateJavascript(JS_POLL_AUTH, value -> {
                    if (bridge.isDone() || !loginDialog.isShowing()) return;
                    String token = cleanJsString(value);
                    if (DiscordPresenceManager.looksLikeDiscordToken(token)) {
                        bridge.deliverToken(token);
                    }
                    if (!bridge.isDone() && loginDialog.isShowing()) {
                        webView.postDelayed(this, 900L);
                    }
                });
            }
        };

        webView.setWebViewClient(new WebViewClient() {
            private boolean handleDiscordRedirect(@NonNull WebView view, @Nullable String url) {
                if (url == null || url.isEmpty()) return false;
                if (url.startsWith("intent:") || url.startsWith("market:")
                        || url.startsWith("discord:") || url.contains("play.google.com")) {
                    return true;
                }
                // Kizzy's exact login completion hook: when Discord redirects from /login to /app or /channels,
                // stop loading before mobile redirect and extract localStorage.token immediately.
                if (url.endsWith("/app") || url.contains("/app?") || url.contains("/channels")) {
                    view.stopLoading();
                    view.loadUrl(KIZZY_JS_SNIPPET);
                    view.evaluateJavascript(JS_POLL_AUTH, value -> {
                        String tk = cleanJsString(value);
                        if (DiscordPresenceManager.looksLikeDiscordToken(tk)) {
                            bridge.deliverToken(tk);
                        }
                    });
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request != null ? request.getUrl() : null;
                if (uri != null && handleDiscordRedirect(view, uri.toString())) {
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, request);
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (handleDiscordRedirect(view, url)) {
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    Uri uri = request.getUrl();
                    String host = uri != null && uri.getHost() != null ? uri.getHost().toLowerCase() : "";
                    String path = uri != null && uri.getPath() != null ? uri.getPath() : "";
                    if (host.endsWith("discord.com") && path.contains("/api/")) {
                        Map<String, String> headers = request.getRequestHeaders();
                        if (headers != null) {
                            for (Map.Entry<String, String> entry : headers.entrySet()) {
                                if ("authorization".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                                    bridge.deliverToken(entry.getValue());
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (handleDiscordRedirect(view, url)) {
                    return;
                }
                view.evaluateJavascript(JS_INSTALL_INTERCEPTOR, null);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                view.evaluateJavascript(JS_INSTALL_INTERCEPTOR, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (handleDiscordRedirect(view, url)) {
                    return;
                }
                view.evaluateJavascript(JS_INSTALL_INTERCEPTOR, null);
                view.evaluateJavascript(JS_POLL_AUTH, null);
                if (initialLoginHint != null && !initialLoginHint.isEmpty() && url != null && url.contains("/login")) {
                    String safeHint = initialLoginHint.replace("\\", "\\\\").replace("'", "\\'");
                    view.evaluateJavascript(
                            "(function(){var el=document.querySelector('input[name=\"email\"]');"
                            + "if(el&&!el.value){el.focus();document.execCommand('insertText',false,'" + safeHint + "');}"
                            + "})();",
                            null
                    );
                }
            }
        });

        cancelBtn.setOnClickListener(v -> loginDialog.dismiss());
        connectNowBtn.setOnClickListener(v -> {
            webView.loadUrl(KIZZY_JS_SNIPPET);
            webView.evaluateJavascript(JS_POLL_AUTH, value -> {
                String token = cleanJsString(value);
                if (DiscordPresenceManager.looksLikeDiscordToken(token)) {
                    bridge.deliverToken(token);
                } else {
                    Toast.makeText(
                            context,
                            "Please finish signing in with your Discord account in the window above first",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        });

        loginDialog.setOnDismissListener(d -> webView.destroy());

        loginDialog.show();
        webView.loadUrl("https://discord.com/login");
        webView.postDelayed(pollTokenRunnable, 1000L);
    }

    @NonNull
    private static String cleanJsString(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        clean = clean.replace("\\\"", "\"").trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        return clean;
    }
}
