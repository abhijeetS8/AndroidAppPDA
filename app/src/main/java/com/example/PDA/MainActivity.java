/***********************************************************************************
 * <ファイルヘッダ>
 *
 * <名前>
 * PDA
 *
 * <説明>
 * このクラスは、PDAアプリケーションのメインアクティビティを表します。次の主要な機能を処理します:
 * 1. 隠しテキストフィールドおよびバーコードスキャンを介したユーザー入力。
 * 2. SMBプロトコルを使用してリモートサーバーへのデータ送信。
 * 3. 入力フィールドおよび通信メッセージの定期的なクリア処理。
 * 4. ファイル操作に必要なストレージ許可の処理。
 * 5. ZXingライブラリを使用して画像を処理してバーコード情報を抽出します。
 *
 * <変更履歴>
 * (Rev.)     (日付)            (ID/名前)             (コメント)
 * Rev 00.00: 2024.05.24  YIL(220399/Vikky Gaurd)  : Original
 *
 ***********************************************************************************/

package com.example.PDA;

import static com.example.PDA.Constants.*;
import static com.example.PDA.SystemTime.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.google.zxing.*;
import com.google.zxing.common.GlobalHistogramBinarizer;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import jcifs.smb.*;

public class MainActivity extends AppCompatActivity {

    //フィールド
    private static final int REQUEST_CODE_SELECT_FILE = 1; // ファイル選択要求コード
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 123; // ストレージ許可要求コード
    private static final int CLEAR_DELAY = 10; // メッセージクリアの遅延時間（秒単位）

    // UI要素
    private EditText hideTxt; // ユーザーの入力を隠すテキストフィールド
    private TextView lblTerminalNo, lblBarcode, lblCommunication; // ターミナル番号、バーコード、通信ラベル
    private View parentLayout; // 親レイアウトのビューを定義
    private Button btnTransmission; // 送信ボタン
    private Handler pasteHandler, sendHandler, timerHandler; // ハンドラ
    private Runnable pasteRunnable, sendRunnable, timerRunnable; // ランナブル

    //共通変数
    private boolean flgState = false; // ステータスフラグ（未使用）
    private static String pstrDate = "", hostName = "", status=""; // 日時
    private static final String anExtension = ".txt"; // 拡張子


    //タイミング変数
    long waitTimeMillis = WAITTIME * 1000L; // 待機時間（ミリ秒単位）
    long clearDelayMillis = CLEAR_DELAY * 1000L; // クリア遅延時間（ミリ秒単位）
    private long lastBarcodeReadingTime = 0; // 最後のバーコード読み取り時間

    // UIハンドラ
    private Handler handler = new Handler();

    // クリア用のRunnable
    private Runnable clearRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            // 最後のバーコード読み取りからの経過時間が10秒以上の場合、UIをクリアします
           // if (System.currentTimeMillis() - lastBarcodeReadingTime >= clearDelayMillis)
            {
                // バーコード、通信、端末番号のテキストをクリアします
                lblBarcode.setText("");
                lblCommunication.setText("");
                lblTerminalNo.setText("");
            }
        }
    };

    /***********************************************************************************
     * <目的>
     * アクティビティの初期化処理を行います。UIコンポーネントの初期化、リスナーの設定、ストレージの許可確認を行います。
     * また、デバイスのホスト名を取得し、クリア処理を遅延実行します。
     *
     * <引数>
     * savedInstanceState: 前回の状態が保存されたバンドル
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initComponents(); // UIコンポーネントの初期化
        setupListeners(); // リスナーのセットアップ
        checkStoragePermission(); // ストレージの許可を確認

        hostName = Build.MODEL; // デバイスのホスト名を取得

       // handler.postDelayed(clearRunnable, clearDelayMillis); // クリア処理の遅延実行

        hideTxt.requestFocus(); // テキストフィールドにフォーカスを設定

        // 親レイアウトの任意の場所をタッチするとフォーカスを設定
        parentLayout.setOnTouchListener((v, event) -> {
            hideTxt.requestFocus(); // hideTxtにフォーカスを要求
            return true;
        });

//        parentLayout.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                showKeyboard();
//                return true;
//            }
//        });

    }

    /***********************************************************************************
     * <目的>
     * UIコンポーネントを初期化する
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void initComponents()
    {
        hideTxt = findViewById(R.id.hide_Txt);
        lblTerminalNo = findViewById(R.id.Lbl_TerminalNo);
        lblBarcode = findViewById(R.id.Lbl_Barcode);
        lblCommunication = findViewById(R.id.Lbl_Communication);
        btnTransmission = findViewById(R.id.Btn_Transmission);
        parentLayout = findViewById(R.id.parent_layout);
    }

    /***********************************************************************************
     * <目的>
     * UIコンポーネントにリスナーを設定し、各種イベントを処理する。
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void setupListeners()
      {
//       //  テキストフィールドのエンターキー押下時の処理を設定します
//        hideTxt.setOnEditorActionListener((v, actionId, event) ->
//        {
//            // エラーステータスでない場合のみ処理を実行します
//            if (!status.equals(PDAERR2) && !status.equals(PDAERR3))
//            {
//                // エンターキーが押されたかどうかを確認し、入力処理を実行します
//                if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN))
//                {
//                    processInput(hideTxt.getText().toString());
//                    hideTxt.requestFocus();
//                    hideKeyboard();
//                    return true;
//                }
//            }
//            return false;
//        });

          // テキストフィールドの内容変更時の処理を設定します
          hideTxt.addTextChangedListener(new TextWatcher() {
              private Handler handler = new Handler();
              private Runnable runnable;

              @Override
              public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                  // ここでは何もしません
              }

              @Override
              public void onTextChanged(CharSequence s, int start, int before, int count) {
                  // テキストが変更されたときに2秒後に入力処理を実行するようにスケジュールします
                  if (runnable != null) {
                      handler.removeCallbacks(runnable);
                  }
                  runnable = new Runnable() {
                      @Override
                      public void run() {
                          if (!status.equals(PDAERR2) && !status.equals(PDAERR3)) {
                              processInput(hideTxt.getText().toString());
                              hideTxt.requestFocus();
                              hideKeyboard();
                          }
                      }
                  };
                  handler.postDelayed(runnable, 1000);
              }
              @Override
              public void afterTextChanged(Editable s) {
                  // ここでは何もしません
                  // ここでは何もしません
              }
          });

        // テキストフィールドのテキスト変更リスナーを設定します
        hideTxt.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                resetHandler(true); // ハンドラをリセットします
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // エラーステータスに応じてテキストフィールドの有効/無効を設定します
        hideTxt.setEnabled(!status.equals(PDAERR2) && !status.equals(PDAERR3));

        // 送信ボタンのクリックリスナーを設定します
        btnTransmission.setOnClickListener(v -> onSendTimerTick());

        // ペースト、送信、タイマーのハンドラを初期化します
        pasteHandler = new Handler();
        sendHandler = new Handler();
        timerHandler = new Handler();

        // ペースト、送信、タイマーのコールバック処理を定義します
        pasteRunnable = this::onPasteTimerTick;
        sendRunnable = this::onSendTimerTick;
        timerRunnable = this::onMyTimerTick;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(hideTxt.getWindowToken(), 0);
        hideTxt.clearFocus();
    }
    private void showKeyboard() {
        hideTxt.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.showSoftInput(hideTxt, InputMethodManager.SHOW_IMPLICIT);
    }

    /***********************************************************************************
     * <目的>
     * ユーザーのキー入力を処理する
     *
     * <引数>
     * - keyCode: キーのコード
     * - event: イベント
     *
     * <戻り値>
     * - boolean: キーイベントの処理結果
     ***********************************************************************************/
//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event)
//    {
//        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
//        {
//            // エラーステータスでない場合のみ処理
//            if (!status.equals(PDAERR2) && !status.equals(PDAERR3))
//            {
//                openFileSelector(); // ファイル選択ダイアログを開く
//            }
//            return true;
//        }
//        return super.onKeyDown(keyCode, event);
//    }

    /***********************************************************************************
     * <目的>
     * 受信したデータを処理する
     *
     * <引数>
     * - data: 受信したデータ
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void handleIncomingData(String data)
    {
        if (data != null)
        {
            // 最後のバーコード読み取り時間を更新
            lastBarcodeReadingTime = System.currentTimeMillis();
            // テキストフィールドにデータをセット
            hideTxt.setText(data);
            // ペーストタイマーを開始（500ミリ秒後に実行）
            pasteHandler.postDelayed(pasteRunnable, 500);
        }
    }

    /***********************************************************************************
     * <目的>
     * ペーストタイマーがトリガーされたときの処理
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void onPasteTimerTick()
    {
        // ペーストタイマーをキャンセル
        pasteHandler.removeCallbacks(pasteRunnable);
        // 入力データの処理を実行
        processInput(hideTxt.getText().toString());
    }

    /***********************************************************************************
     * <目的>
     * 入力データの処理を行う
     *
     * <引数>
     * input: 入力されたテキスト
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void processInput(String input)
    {
        // 入力が空の場合は処理を中断
        if (input.isEmpty()) return;

        // 通信ラベルをクリア
        lblCommunication.setText("");

        // 入力データがCode39かどうかをチェック
        boolean isCode39 = (input.startsWith("*") && input.length() > 1 && (input.charAt(1) == 'T' || input.charAt(1) == 'P')) ||
                (!input.startsWith("*") && (input.charAt(0) == 'T' || input.charAt(0) == 'P'));

        // Code39の場合の処理
        if (isCode39)
        {
            if (validateCode39(input))
            {
                // テキストをターミナル番号として設定
                lblTerminalNo.setText(input);
            }
            else
            {
                // エラーメッセージを表示
                showErrorOnUI(PDAERR11);
            }
        }
        else
        { // Code128の場合の処理
            if (validateCode128(input))
            {
                // テキストをバーコードとして設定
                lblBarcode.setText(input);
            }
            else
            {
                // エラーメッセージを表示
                showErrorOnUI(PDAERR11);
            }
        }

        // 入力フィールドをクリアし、ハンドラをリセット
        hideTxt.setText("");
        resetHandler(true);
    }

    /***********************************************************************************
     * <目的>
     * Code39形式の文字列のバリデーションを行う
     *
     * <引数>
     * content: バリデーションする文字列
     *
     * <戻り値>
     * バリデーション結果 (true: 有効なCode39形式, false: 無効なCode39形式)
     ***********************************************************************************/
    private boolean validateCode39(String content)
    {
        // Code39の開始と終了文字が存在する場合、それらを除去
        if (content.startsWith("*") && content.endsWith("*"))
        {
            // 長さが2未満の場合は無効なCode39形式として処理
            if (content.length() < 3) return false;
            content = content.substring(1, content.length() - 1);
        }
        else
        {
            // 開始と終了文字がない場合、長さが0の場合は無効なCode39形式として処理
            if (content.length() < 1) return false;
        }

        // 正規表現でのバリデーション
        return content.matches("[A-Z0-9\\-\\. \\$/\\+%]+");
    }

    /***********************************************************************************
     * <目的>
     * Code128形式の文字列のバリデーションを行う
     *
     * <引数>
     * content: バリデーションする文字列
     *
     * <戻り値>
     * バリデーション結果 (true: 有効なCode128形式, false: 無効なCode128形式)
     ***********************************************************************************/
    private boolean validateCode128(String content) {
        // 文字列内の各文字がASCII文字の範囲内にあるかどうかをチェック
        return content.chars().allMatch(c -> c >= 0 && c <= 127);
    }

    /***********************************************************************************
     * <目的>
     * 送信タイマーのコールバック処理を実行し、端末番号とバーコードのテキストをサーバーに送信する
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void onSendTimerTick() {
        // 送信ハンドラのコールバックを削除
        sendHandler.removeCallbacks(sendRunnable);

        // バーコードおよび端末番号が空でないことを確認し、それぞれのテキストをサーバーに送信する
        if (lblTerminalNo.getText().toString().isEmpty() || lblBarcode.getText().toString().isEmpty()) {
            // バーコードまたは端末番号が空の場合はエラーメッセージを表示して処理を中断
            showErrorOnUI(PDAERR1);
            // ハンドラのリセット
            resetHandler(false);
            return;
        }

        // NetworkTaskを実行してサーバーにデータを送信
        new NetworkTask(success -> {}).execute();
    }

    /***********************************************************************************
     * <目的>
     * バックグラウンドでサーバーへのデータ送信を処理し、結果をUIに反映させる
     *
     * <引数>
     * callback - ファイル作成のコールバック
     *
     * <戻り値>
     * 成功した場合はtrue、失敗した場合はfalse
     ***********************************************************************************/
    private class NetworkTask extends AsyncTask<Void, Void, Boolean>
    {
        // コールバックとエラーメッセージを保持するためのフィールド
        private final FileCreationCallback callback;
        private String errorMessage = "";

        // コンストラクタ
        NetworkTask(FileCreationCallback callback)
        {
            this.callback = callback; // コールバックを初期化
        }

        /*
         * バックグラウンドで長時間かかる操作を非同期で実行し、UIスレッドをブロックせずにアプリのレスポンス性を確保します。
         * このメソッドでは、ネットワーク通信やファイル操作などの処理を行います。
         */
        @Override
        protected Boolean doInBackground(Void... voids)
        {
            try
            {
                // UIスレッドでエラーメッセージを表示
                runOnUiThread(() -> showErrorOnUI(PDAERR2));

                Thread.sleep(100);

                // ハンドラのリセット
                resetHandler(true);

                // ステータスフラグが立っているか、サーバーへの接続ができないか、ファイルの送信ができない場合は処理を中断
                if (flgState || !conServer() || !fileSend())
                {
                    return false;
                }
                // 公開フォルダの監視
                return pubfMonitoring();
            }
            catch (Exception ex)
            {
                errorMessage = PDAERR15;
                return false;
            }
        }

        /*
         * doInBackground メソッドが完了した後、UIスレッドで結果を処理します。
         * 通常は、バックグラウンド処理の結果をもとにUIの更新や追加の処理を行います。
         */
        @Override
        protected void onPostExecute(Boolean result)
        {
            // エラーメッセージの表示
            runOnUiThread(() -> showErrorOnUI(result ? "" : errorMessage));

            resetHandler(false);

            // コールバックの実行
            if (callback != null) callback.onFileCreationComplete(result);
        }
    }

    /***********************************************************************************
     * <目的>
     * サーバーへの接続を確立し、共有フォルダーの存在とディレクトリであることを確認する
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * 成功した場合はtrue、失敗した場合はfalse
     ***********************************************************************************/
    private boolean conServer()
    {
        try
        {
            // サーバーへのパスを構築。SMBプロトコルを使用して、共有フォルダーのパスを作成します。
            // SOPFOLDERとRESPONSEFOLDERは定数として定義されています。これらは共有フォルダーへのパスを構築するために使用されます。
            String serverPath = "smb:" + SOPFOLDER + RESPONSEFOLDER;

            // 認証情報を設定（TODO: ユーザー名、ドメイン、パスワードは環境に合わせて変更する）
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("INYKGW", "SRVYF03", "Srvyf03");

            // サーバーフォルダーのインスタンスを作成
            SmbFile serverFolder = new SmbFile(serverPath, auth);

            // サーバーフォルダーが存在し、ディレクトリであるかを確認
            return serverFolder.exists() && serverFolder.isDirectory();
        }
        catch (Exception e)
        {
            runOnUiThread(() -> showErrorOnUI(PDAERR15)); // エラーメッセージの表示
            throw new RuntimeException(e);
        }
    }

    /***********************************************************************************
     * <目的>
     * ファイルの送信を行う。新しいファイルを作成し、指定されたデータを書き込んで送信を試みます。
     * 送信が成功した場合、ステータスフラグを更新してtrueを返します。
     * 送信に失敗した場合はエラーメッセージを表示し、ステータスフラグを更新してfalseを返します。
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * 送信が成功した場合はtrue、失敗した場合はfalse
     ***********************************************************************************/
    private boolean fileSend()
    {
        try (BufferedWriter textFile = new BufferedWriter(new OutputStreamWriter(new SmbFileOutputStream(createNewFile()))))
        {
            // 送信するデータを生成し、ファイルに書き込む
            String data = lblTerminalNo.getText().toString() + lblBarcode.getText().toString();
            textFile.write(data);
            textFile.newLine();
            flgState = true; // ステータスフラグを更新
            return true; // 送信成功を返す
        }
        catch (IOException e)
        {
            runOnUiThread(() -> showErrorOnUI(PDAERR13)); // エラーメッセージの表示
            throw new RuntimeException(e);
        }
    }

    /***********************************************************************************
     * <目的>
     * 新しいファイルを作成します。ファイル名はホスト名、現在の日時、および拡張子から構成されます。
     * 作成されたファイルのパスは指定された共有フォルダーにあります。
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * 作成されたSmbFileオブジェクト
     * @throws IOException ファイルの作成に失敗した場合
     ***********************************************************************************/
    private SmbFile createNewFile() throws IOException
    {
        SystemTime st = new SystemTime();

        // 現在日時を取得
        getLocalTime(st);
        pstrDate = String.format("%04d%02d%02d%02d%02d%02d", st.Year, st.Month, st.Day, st.Hour, st.Minute, st.Second);

        // サーバーへのパスを構築。SMBプロトコルを使用して、共有フォルダーのパスを作成します。
        // SOPFOLDERとRESPONSEFOLDERは定数として定義されています。これらは共有フォルダーへのパスを構築するために使用されます。
        String filePath = "smb:" + SOPFOLDER + RESPONSEFOLDER + hostName + "_" + pstrDate + anExtension;

        // 認証情報を設定（TODO: ユーザー名、ドメイン、パスワードは環境に合わせて変更する）
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("INYKGW", "SRVYF03", "Srvyf03");

        // 新しいファイルを作成
        SmbFile file = new SmbFile(filePath, auth);
        if (!file.exists())
        {
            file.createNewFile();
        }
        else
        {
            showErrorOnUI(PDAERR16); // エラーメッセージの表示
            resetHandler(false);
            flgState = false; // ステータスフラグを更新
            throw new IOException(PDAERR16); // IOExceptionをスロー
        }
        return file; // 作成されたファイルを返す
    }

    /***********************************************************************************
     * <目的>
     * 公衆監視を開始します。1秒ごとにタイマーハンドラを呼び出します。
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * 常にfalse
     ***********************************************************************************/
    private boolean pubfMonitoring()
    {
        timerHandler.postDelayed(timerRunnable, 1000); // タイマーハンドラを1秒ごとに実行
        return false; // 常にfalseを返す
    }

    /***********************************************************************************
     * <目的>
     * タイマーがタイムアウトした際の処理を行います。タイマーハンドラを削除し、新しいタイマーネットワークタスクを実行します。
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void onMyTimerTick()
    {
        timerHandler.removeCallbacks(timerRunnable); // タイマーハンドラを削除
        new TimerNetworkTask().execute(); // 新しいタイマーネットワークタスクを実行
    }

    /***********************************************************************************
     * <目的>
     * タイマータスクを実行するための非同期タスクです。指定されたファイルを削除し、タイムアウトのチェックを行います。
     * タイマータスクが完了すると、エラーメッセージを表示し、ハンドラをリセットします。
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * Boolean: ファイルの削除が成功したかどうか
     ***********************************************************************************/
    private class TimerNetworkTask extends AsyncTask<Void, Void, Boolean>
    {
        private String errorMessage = ""; // エラーメッセージの初期化

        @Override
        protected Boolean doInBackground(Void... voids)
        {
            try
            {
                runOnUiThread(() -> showErrorOnUI(PDAERR3)); // UIスレッドでエラーメッセージを表示
                resetHandler(true); // ハンドラのリセット

                // サーバーへのパスを構築。SMBプロトコルを使用して、共有フォルダーのパスを作成します。
                // SOPFOLDERとRESPONSEFOLDERは定数として定義されています。これらは共有フォルダーへのパスを構築するために使用されます。
                String filePath = "smb:" + SOPFOLDER + RESPONSEFOLDER + hostName + "_" + pstrDate + "R"+ anExtension;

                // 認証情報を設定（TODO: ユーザー名、ドメイン、パスワードは環境に合わせて変更する）
                NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("INYKGW", "SRVYF03", "Srvyf03");

                // 指定されたファイルを削除
                SmbFile file = new SmbFile(filePath, auth);
                if (file.exists())
                {
                    // ファイルが存在する場合、その内容を読み込む
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream())))
                    {
                        StringBuilder fileContent = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null)
                        {
                            fileContent.append(line).append("\n"); // 各行をバッファに追加
                        }

                        String finalContent = fileContent.toString();
                        runOnUiThread(() -> lblCommunication.setText(finalContent)); // UIスレッドで内容を表示
                    }
                    file.delete(); // ファイルを削除
                    return true;
                }
                else if (isTimeout()) // タイムアウトのチェック
                {
                    errorMessage = PDAERR4; // エラーメッセージの設定
                    return false;
                }
            }
            catch (Exception e)
            {
                errorMessage = PDAERR13; // エラーメッセージの設定
                return false;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Boolean result)
        {
            if (result == null)
            {
                timerHandler.postDelayed(timerRunnable, 1000); // タイマーを再度開始
            }
            else
            {
                // ハンドラのリセット
                resetHandler(false);

                // ステータスフラグの更新
                flgState = false;
            }
        }

        // タイムアウトの確認
        private boolean isTimeout() throws Exception
        {
            Date now = new Date(); // 現在の日付を取得
            Date createDate = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).parse(pstrDate); // ファイルの作成日を取得
            return (now.getTime() - createDate.getTime()) / 1000 > WAITTIME; // タイムアウトのチェック
        }
    }

    /***********************************************************************************
     * <目的>
     * ファイル選択ダイアログを開くメソッドです。画像ファイルのみを選択できます。
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void openFileSelector()
    {
        // ファイル選択用のインテントを作成
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*"); // 画像ファイルのみを選択できるように設定
        startActivityForResult(intent, REQUEST_CODE_SELECT_FILE); // アクティビティの起動
    }

    /***********************************************************************************
     * <目的>
     * startActivityForResult() メソッドで開始されたアクティビティからの結果を受け取るためのメソッドです。
     * 選択されたファイルが画像である場合、その画像を処理するメソッドを呼び出します。
     *
     * <引数>
     * requestCode: リクエストコード
     * resultCode: 結果コード
     * data: 受け取ったデータ
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_FILE && resultCode == RESULT_OK && data != null)
        {
            processImageFromUri(data.getData()); // 選択されたファイルの処理を開始
        }
    }

    /***********************************************************************************
     * <目的>
     * 渡されたURIから画像を読み込み、その画像からバーコードを解析するメソッドです。
     *
     * <引数>
     * uri: 画像のURI
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void processImageFromUri(Uri uri)
    {
        try (InputStream inputStream = getContentResolver().openInputStream(uri))
        {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream); // URIから画像をデコードしてBitmapを作成
            decodeBarcodeFromBitmap(bitmap); // バーコードを解析
        }
        catch (Exception e)
        {
            showErrorOnUI(PDAERR12); // エラーメッセージの表示
            resetHandler(false);
        }
    }

    /***********************************************************************************
     * <目的>
     * 渡されたBitmapからバーコードを解析し、その結果を処理するメソッドです。
     *
     * <引数>
     * bitmap: バーコードを含む画像のBitmap
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void decodeBarcodeFromBitmap(Bitmap bitmap)
    {
        try
        {
            // 画像の幅と高さを取得し、ピクセル配列を作成
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            // ピクセルからLuminanceSourceを作成し、BinaryBitmapを生成
            LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));

            // バーコードを解析して結果を取得
            Result result = new MultiFormatReader().decode(binaryBitmap);

            // バーコードが検出されたことをToastで表示し、結果を処理
            displayToast("バーコードが検出されました: " + result.getText());
            handleIncomingData(result.getText());
        }
        catch (Exception e)
        {
            showErrorOnUI(PDAERR12); // エラーメッセージの表示
            resetHandler(false);
        }
    }

    /***********************************************************************************
     * <目的>
     * 渡されたメッセージを短い期間のToastで表示するメソッドです。
     *
     * <引数>
     * message: 表示するメッセージの文字列
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void displayToast(String message)
    {
        // アプリケーションのコンテキストを使用して、短い期間のToastを表示
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    /***********************************************************************************
     * <目的>
     * UI上にエラーメッセージを表示するためのメソッドです。
     *
     * <引数>
     * errorMessage: 表示するエラーメッセージの文字列
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void showErrorOnUI(final String errorMessage)
    {
        // UIスレッド上でエラーメッセージをラベルに設定する
        runOnUiThread(() -> lblCommunication.setText(errorMessage));
    }

    /***********************************************************************************
     * <目的>
     * ファイルの作成が完了した際に呼び出されるコールバックメソッドです。
     *
     * <引数>
     * - success: ファイルの作成が成功したかどうかを示すブール値です。
     *     - true: ファイルの作成が成功したことを示します。
     *     - false: ファイルの作成が失敗したことを示します。
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    public interface FileCreationCallback
    {
        void onFileCreationComplete(boolean success);
    }

    /***********************************************************************************
     * <目的>
     * ストレージのパーミッションを確認およびリクエストします。Android 10以降では、
     * 読み取りと書き込みの両方のパーミッションが必要です。
     *
     * <引数>
     * なし
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void checkStoragePermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            // Android 10以降では、読み取りと書き込みの両方のパーミッションが必要です
            requestPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        else
        {
            // Android 10未満の場合は、書き込みのパーミッションのみが必要です
            requestPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    /***********************************************************************************
     * <目的>
     * 指定されたパーミッションをリクエストします。パーミッションが許可されていない場合、
     * パーミッションリクエストが自動的に開始されます。
     *
     * <引数>
     * permissions: リクエストするパーミッションの配列
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void requestPermission(String... permissions)
    {
        for (String permission : permissions)
        {
            // 権限が許可されていない場合、パーミッションリクエストを開始します
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_STORAGE_PERMISSION);
                break; // パーミッションが1つでも許可されていない場合はループを抜けます
            }
        }
    }

    /***********************************************************************************
     * <目的>
     * クリア処理のハンドラーをリセットします。特別なケースに応じて適切な遅延時間を設定します。
     *
     * <引数>
     * specialCase: 特別なケースが発生したかどうかを示すフラグ
     *
     * <戻り値>
     * なし
     ***********************************************************************************/
    private void resetHandler(boolean specialCase)
    {
        // クリア処理の遅延実行をキャンセルします
        handler.removeCallbacks(clearRunnable);

        // 遅延時間を特別なケースに応じて設定します
        long delay = specialCase ? waitTimeMillis : clearDelayMillis;

        // 現在のステータスを取得します
        status = lblCommunication.getText().toString();

        // 特別なケースの場合の処理
        if (specialCase)
        {
            // もしステータスが PDAERR2 または PDAERR3 の場合、遅延時間を waitTimeMillis に設定します
            if (status.equals(PDAERR2) || status.equals(PDAERR3))
            {
               // delay = waitTimeMillis;
            }
            // それ以外の場合、遅延時間を clearDelayMillis に設定します
            else
            {
               // delay = clearDelayMillis;
            }
        }

        // クリア処理の遅延実行を設定します
        handler.postDelayed(clearRunnable, delay);
    }

}
