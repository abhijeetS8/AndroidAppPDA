/***********************************************************************************
 * <目的>
 * アプリケーションで使用される定数を定義するクラス。
 *
 * <定数>
 * - エラーメッセージ定数
 * - サーバーの共有フォルダのパス
 * - PDAとSOPサーバーの要求＆応答ファイル保管場所
 * - 待機時間（秒）
 ***********************************************************************************/
package com.example.PDA;

// 定数クラス
public class Constants {

    // エラーメッセージ定数
    public static final String PDAERR1 = "ﾃﾞｰﾀ未読込";
    public static final String PDAERR2 = "送信中";
    public static final String PDAERR3 = "処理中";
    public static final String PDAERR4 = "ﾀｲﾑｱｳﾄ(BCIF)";
    public static final String PDAERR5 = "ﾀｲﾑｱｳﾄ(SOP)";
    public static final String PDAERR6 = "ﾀｲﾑｱｳﾄ(M3子)";
    public static final String PDAERR7 = "正常終了";
    public static final String PDAERR8 = "該当項目なし";
    public static final String PDAERR9 = "対象外のBCR";
    public static final String PDAERR10 = "項目未選択";
    public static final String PDAERR11 = "フォーマット例外";
    public static final String PDAERR12 = "BCR読込不可";
    public static final String PDAERR13 = "SYSTEMエラー";
    public static final String PDAERR14 = "該当端末なし";
    public static final String PDAERR15 = "SOP接続失敗";
    public static final String PDAERR16 = "同file存在";


    // SOPサーバーの共有フォルダへのパス（TODO: クライアントのサーバーIPを更新してください）
    public static final String SOPFOLDER = "//192.168.119.249/";

    // PDAおよびSOPサーバーの要求と応答ファイルの保存場所（特定のフォルダ構造に更新してください）
    public static final String RESPONSEFOLDER = "interface/bcr/";

    // 待機時間（秒）
    public static final int WAITTIME = 60;
}
