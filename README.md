# DSH Android

A dedicated Android client for a DeepSeek Harness (`dsh web`) server. It shows
the Web GUI fullscreen, with no browser chrome.

DeepSeek Harness (`dsh web`) サーバ用の専用Androidクライアント。Web GUIを全画面表示する。
URLバー等のブラウザ要素は出さない。opencode版 (`Opencode2-android`) のフォーク。

## How login works / ログインの仕組み

`dsh web` refuses every request until a browser has exchanged the launch token
it prints at startup for a cookie. That is why opening the plain URL in a fresh
browser shows `dsh web authentication required`.

1. On the server, start the Web UI; it prints one line:
   `dsh web: http://<host>:3080/?token=<launch token>`
2. Paste that whole URL into the app (or share it to the app). It sets the
   server address and loads the token once; the server answers with a cookie
   that survives restarts for `cookieMaxAgeDays` (default 30).
3. After that the app uses the plain address, e.g. `http://<host>:3080`.

The token itself changes on every server start, so a stored login URL is
one-shot: the app drops it as soon as the cookie exists. If the cookie is
cleared or expires, the error screen asks for a fresh login URL.

`dsh web` は起動時に一度だけトークン付きURLを表示し、それでCookieを得るまで全リクエストを401で
拒否する。そのため素のURLを新しいブラウザで開くと `dsh web authentication required` になる。

1. サーバで `dsh web` を起動し、表示された `http://<host>:3080/?token=...` を丸ごとコピー
2. アプリに貼り付ける（共有Intentでも可）。1回だけトークンURLを読み込み、Cookieを受け取る
   （既定で30日間、サーバ再起動でも有効）
3. 以降は素のアドレス `http://<host>:3080` で動く

トークンはサーバ起動ごとに変わるため保存したログインURLは使い捨て。Cookieが消えた／切れた場合は
エラー画面が新しいログインURLを促す。

## Server side / サーバ側

`dsh web` binds one interface and rejects wildcard binds, so expose it with a
proxy. This is the setup the app is developed against:

```
dsh --profile web --host 127.0.0.1 --port 3081 \
    --public-url http://myhost.example.ts.net:3080 \
    --trusted-host myhost.example.ts.net:3080 \
    --trusted-host 100.64.0.1:3080 \
    --trusted-host 192.168.1.10:3080
```

`--trusted-host` matters for anything but loopback: the `/api` browser-trust
fence rejects a Host it does not know, and the SPA then cannot reach its own
backend. `socat TCP-LISTEN:3080,fork,reuseaddr TCP:127.0.0.1:3081` exposes the
loopback listener on the LAN and the tailnet.

## Features / 機能

- Fullscreen Web GUI: no URL bar, no long-press menu, external links open in
  other apps
  - フルスクリーン表示、長押しメニュー無効、外部リンクは他アプリで開く
- "App settings" and "Reload" rows appended to the Web GUI's own
  Settings → General pane (toggle in the app settings; injected only when the
  Android bridge exists, so a desktop browser sees an untouched UI)
  - Web GUIの設定 → General の末尾に「App settings」「Reload」行を追加 (アプリ設定でON/OFF)
- Native settings screen: server / login URL, appearance fixes (custom CSS/JS
  injection), log download
  - ネイティブ設定画面: サーバ／ログインURL、外観修正 (CSS/JS注入)、ログ保存
- Accepts Share Intents: a shared login URL re-points the server, other text is
  pasted into the message box (falls back to the clipboard)
  - 共有Intent対応: ログインURLを共有するとサーバ設定、その他のテキストは入力欄へ
- Connection resilience: load watchdog, reconnect probe on resume, one-shot
  WebView self-heal, 401 detection with a re-login prompt
  - 接続の復帰処理: ロード監視、復帰時プローブ、WebView再生成、401検出と再ログイン案内

## Requirements / 必要なもの

- Android 8.0+
- A reachable `dsh web` server (see above)

## Install / インストール

Install the APK from GitHub Releases on the device ("install unknown apps" must
be allowed).

GitHub ReleasesのAPKを端末に送ってインストール。「提供元不明のアプリ」を許可すること。

## Build / ビルド

```sh
export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

A release build needs a signing key / リリースビルドには署名鍵が必要:

```sh
export DSH_KEYSTORE_FILE=$HOME/.android/dsh-release.keystore
export DSH_KEYSTORE_PASSWORD=...
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

To generate a new key / 新規に鍵を作る場合:

```sh
keytool -genkeypair -keystore ~/.android/dsh-release.keystore \
  -alias dsh -keyalg RSA -keysize 2048 -validity 9125
```

Note: updates must be signed with the same key or they cannot overwrite-install.
Keep the key and password safe / 公開後の更新は同一鍵で署名しないと上書き更新できない。鍵とパスワードの保管は自己責任。

## Icons / アイコン

`tools/make-icons.sh` renders the launcher icons from `tools/whale.svg` (the
DeepSeek whale, taken from the DSH Web frontend) into white/black legacy and
adaptive-icon layers. Requires ImageMagick.

## Privacy / プライバシー

- Talks only to your configured server. No analytics, no ads
  - 通信は設定した自サーバとの間のみ。外部送信・解析・広告なし
- The auth cookie stays inside the on-device WebView
  - 認証Cookieは端末内のWebViewにのみ保存

## License / ライセンス

MIT
