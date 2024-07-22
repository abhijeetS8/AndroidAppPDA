package com.example.PDA;

import java.util.Calendar;

// システム時間を管理する SystemTime クラス
public class SystemTime {
    // 時刻要素を格納する変数群
    public static short Year;
    public static short Month;
    public static short DayOfWeek;
    public static short Day;
    public static short Hour;
    public static short Minute;
    public static short Second;
    public static short Milliseconds;

    // ローカル時間を取得するメソッド
    /************************************************************************************
     * <目的>
     * システムの現在時刻を取得し、SystemTime オブジェクトに各時刻要素を割り当てる。
     *
     * <引数>
     * systemTime: SystemTime オブジェクト
     *
     * <戻り値>
     * なし
     ************************************************************************************/
    public static void getLocalTime(SystemTime systemTime) {
        // 現在のシステム時刻を取得
        Calendar currentTime = Calendar.getInstance();

        // 各時刻要素を SystemTime オブジェクトに割り当てる
        systemTime.Year = (short) currentTime.get(Calendar.YEAR);
        systemTime.Month = (short) (currentTime.get(Calendar.MONTH) + 1); // 月は0から始まるため、+1 を行う
        systemTime.DayOfWeek = (short) currentTime.get(Calendar.DAY_OF_WEEK);
        systemTime.Day = (short) currentTime.get(Calendar.DAY_OF_MONTH);
        systemTime.Hour = (short) currentTime.get(Calendar.HOUR_OF_DAY); // 24時間形式
        systemTime.Minute = (short) currentTime.get(Calendar.MINUTE);
        systemTime.Second = (short) currentTime.get(Calendar.SECOND);
        systemTime.Milliseconds = (short) currentTime.get(Calendar.MILLISECOND);
    }
}
