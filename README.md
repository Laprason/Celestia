# Celestia（HoYoverse 月パス/シーズンパス 課金管理）

原神・スターレイル・ゼンレスゾーンゼロの **月パス** と **シーズン/バトルパス** の
課金（更新）時期を管理するアプリです。**Web版**と**ネイティブAndroid版**の2種類があります。

## 機能
- 各ゲームの月パス・シーズンパスの **残り日数を一覧表示**
- **月パス**：毎月更新前提で初期ON（各¥610固定・30日周期で自動継続）。購入日（更新日）は一覧から調整可、期限が近づくと通知
- **シーズン/バトルパス**：過去の購入日は不要。**次回アップデート（新バージョン）日**が近づくと通知。
  アップデート日が過ぎると **周期（既定42日）分だけ自動で次回へ繰り上げ**
  - 初期値は次バージョン公開日（メンテ明け＝次バナー第1弾開始）を埋め込み済み。ズレたら設定/編集で調整可：
    原神 **2026-07-01**（6.7）／スターレイル **2026-07-15**（4.4）／ゼンレス **2026-07-29**（3.1）
- 通知タイミングは設定で「期限／アップデートの何日前から」を変更可
- **シーズンパスの種類を選択**：設定画面で各ゲームごとに「標準／上位」を選ぶと金額に反映
  - 原神 精緻な紀行¥1,220／華麗な紀行¥2,440 ・ スターレイル ナナシビトの褒章¥1,220／勲章¥2,480 ・ ゼンレス 成長プラン¥1,220／プレミアム¥2,480
- **📅 課金カレンダー**：月パスの更新日とシーズンの更新日から、**いつ・いくら課金が必要か**を月別カレンダーに表示。
  日ごとの金額、月合計、今後6ヶ月の合計を集計
- **🧩 ホーム画面ウィジェット（Androidのみ）**：次に必要な課金を日付順に最大5件＋今後6ヶ月の合計を表示。
  タップでアプリを起動。アプリ編集時・約1時間ごとに自動更新（Jetpack Glance）
- 通知タップ／ボタンで **公式 top-up center（課金センター）をワンタップで開く**
- バックアップ／復元（Web版）、端末バックアップ対応（Android版）

> ⚠️ **自動購入について**：HoYoverse の課金センターは公開APIがなくログイン・決済認証が必須で、
> 自動購入は利用規約に反するため実装していません。代わりに「課金センターを開く」リンクを用意しています
> （ログイン済みなら数タップで購入できます）。URLは設定画面で各ゲームごとに変更可能です。

---

## Web版（すぐ使える）
`web/index.html` をブラウザで開くだけです。スマホでは：

1. Android の Chrome で `index.html` を開く
2. メニュー →「ホーム画面に追加」で **PWAとしてインストール**
3. 「🔔 通知を有効化」をタップして通知を許可

データは端末内（localStorage）に保存されます。「バックアップ」で JSON を書き出せます。
※ Web版の通知はアプリを開いている間／起動時に判定されます。確実なバックグラウンド通知は Android版をご利用ください。

---

## ネイティブAndroid版（`android/`）

### 必要なもの
- **Android Studio**（最新版 / Koala 以降推奨）
- このマシンには Java 17 はありますが Android SDK 未導入のため、Android Studio の導入が必要です

### ビルド手順
1. Android Studio を起動し **Open** → `D:\works\hoyo-pass-manager\android` を選択
2. 初回は Gradle Sync が自動実行されます（依存と Gradle Wrapper を自動取得）
3. 実機を USB 接続（USBデバッグ ON）または エミュレータを起動
4. ツールバーの ▶ **Run 'app'** でインストール＆起動
5. APK 単体が欲しい場合：**Build → Build Bundle(s)/APK(s) → Build APK(s)**
   → `app/build/outputs/apk/debug/app-debug.apk`

> Gradle Wrapper の jar/スクリプトは Android Studio が初回 Sync 時に自動生成します。
> コマンドラインでビルドしたい場合は、Android Studio で一度 Sync 後に
> `cd android && ./gradlew assembleDebug`（Windows は `gradlew.bat assembleDebug`）。

### 技術構成
- Kotlin / Jetpack Compose (Material3)
- DataStore Preferences + kotlinx.serialization（永続化）
- WorkManager（約12時間ごとに期限チェックして通知）
- Jetpack Glance（ホーム画面ウィジェット）
- ブラウザ Intent で課金センターを起動

### ウィジェットの設置（スマホ）
ホーム画面長押し → ウィジェット → 「HoYoパス管理」を探してドラッグ配置。
サイズは横3×縦2目安（リサイズ可）。

### 主要ファイル
| ファイル | 役割 |
|---|---|
| `Models.kt` | ゲーム定義・パスデータ・残り日数計算 |
| `PassRepository.kt` | DataStore への保存／読み込み |
| `MainScreen.kt` | Compose UI（一覧・編集・設定） |
| `ReminderWorker.kt` | 定期チェックして通知 |
| `NotificationHelper.kt` | 通知生成（タップで課金センター） |
| `MainActivity.kt` | 起動・通知権限要求・Worker登録 |

---

## 課金センターURL（既定値・設定で変更可）
公式トップアップセンター（HoYoverse統一決済ポータル）の日本語ページを既定にしています。

| ゲーム | URL |
|---|---|
| 原神 | https://sdk.hoyoverse.com/payment/genshin/index.html?lang=ja-jp |
| スターレイル | https://sdk.hoyoverse.com/payment/hsr/index.html?lang=ja-jp |
| ゼンレスゾーンゼロ | https://sdk.hoyoverse.com/payment/zenless/index.html?lang=ja-jp |

別ページにしたい場合は各アプリの設定画面でURLを書き換えてください。

## ゲームアイコンについて
各ゲームのバッジは、**公式ストア掲載のアプリアイコンを実行時に参照（ホットリンク）**して表示します
（画像はアプリに同梱せず、表示のたびに公式CDNから読み込み）。URLは [Models.kt](android/app/src/main/java/com/hoyopass/manager/Models.kt) / web の `GAMES.iconUrl`。

- **読み込み失敗・オフライン時**は、ブランドカラー＋オリジナル記号（スパーク／星／雷）のローカル画像に自動フォールバック
  - Web: `web/icons/<id>.png` ／ Android: `res/drawable/ic_game_<id>.png`
- 参照元URL（`play-lh.googleusercontent.com`）は予告なく変わる可能性があります。切れたらフォールバックが表示され、URLを差し替えれば復帰します
- Android はリモート画像読み込みに [Coil](https://coil-kt.github.io/coil/) を使用
