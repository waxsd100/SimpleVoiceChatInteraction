package io.wax100.simplevoicechatinteraction;



/**
 * Minecraft クラスに依存しない純粋なユーティリティメソッド群。
 * <p>
 * 音声レベル計算やクールダウン判定など、Minecraft のレジストリ初期化なしに
 * テスト可能なロジックをここに集約する。
 */
public final class AudioUtils {

    private AudioUtils() {
        // ユーティリティクラスのためインスタンス化不可
    }

    /**
     * PCM（パルス符号変調）サンプル配列から音声レベル（dB）を計算する。
     * <p>
     * RMS（二乗平均平方根）を算出し、フルスケール基準でdBに変換する。
     * <ul>
     *   <li>0 dB = 最大音量（全サンプルが {@link Short#MAX_VALUE}）</li>
     *   <li>-∞ dB = 無音（RMS が 1.0 未満）</li>
     * </ul>
     *
     * @param pcmData PCMサンプル配列（16ビット符号付き整数）
     * @return 音声レベル（dB）。null/空の場合は {@link Double#NEGATIVE_INFINITY}
     */
    public static double calculateDbFromPcm(short[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            return 0.0;
        }

        // 振幅の絶対値を配列にコピーしてソート
        short[] absSamples = new short[pcmData.length];
        for (int i = 0; i < pcmData.length; i++) {
            absSamples[i] = (short) Math.abs(pcmData[i]);
        }
        java.util.Arrays.sort(absSamples);

        // 外れ値（突発的なノイズやデジタルポップ音）のみを除外するため、最上位の数サンプル（約1%）だけカット
        // ※5%カットだと人間の声のピーク成分まで削られてしまい、全体の音量が下がってしまうため
        int validLength = pcmData.length - 10;

        if (validLength <= 0) {
            return 0.0;
        }

        double sumSquares = 0.0;
        for (int i = 0; i < validLength; i++) {
            sumSquares += (double) absSamples[i] * absSamples[i];
        }
        double rms = Math.sqrt(sumSquares / validLength);

        // 事実上の無音
        if (rms < 1.0) {
            return 0.0;
        }

        // フルスケール基準のdBに変換
        // dBFS = 20 * log10(rms / 32767)
        double dbfs = 20.0 * Math.log10(rms / Short.MAX_VALUE);
        
        // 人間の声のダイナミックレンジに合わせてスケールを調整
        // dbfs は通常 -40 〜 -10 程度に収まる。
        // マイクゲインや声質にもよるが、叫び声(-10dBFS前後)で100に届くように
        // 係数を 2.0 に引き上げ、ベース値を 120.0 に設定。
        double scaledDb = 120.0 + (dbfs * 2.0);
        return Math.min(100.0, Math.max(0.0, scaledDb));
    }

}
