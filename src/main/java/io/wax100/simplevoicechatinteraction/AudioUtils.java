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

        // RMS（二乗平均平方根）振幅を計算
        double sumSquares = 0.0;
        for (short sample : pcmData) {
            sumSquares += (double) sample * sample;
        }
        double rms = Math.sqrt(sumSquares / pcmData.length);

        // 事実上の無音
        if (rms < 1.0) {
            return 0.0;
        }

        // フルスケール基準のdBに変換
        // dBFS = 20 * log10(rms / 32767)
        double dbfs = 20.0 * Math.log10(rms / Short.MAX_VALUE);
        
        // デジタル音声の dBFS（-100～0）を、人間が直感的に分かりやすい音圧レベル dB SPL（0～100）に変換する
        return Math.max(0.0, dbfs + 100.0);
    }

}
