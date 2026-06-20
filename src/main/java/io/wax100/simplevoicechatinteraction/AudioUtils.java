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

    // フィルタ用の定数 (サンプリングレート 48000Hz 前提)
    private static final double ALPHA_LP = 0.281; // Low-pass at 3000 Hz
    private static final double ALPHA_HP = 0.962; // High-pass at 300 Hz

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
     * @return 音声レベル（dB SPL相当、0.0〜200.0）。null/空/無音の場合は 0.0
     */
    public static double calculateDbFromPcm(short[] pcmData, double baseValue, double multiplier) {
        if (pcmData == null || pcmData.length == 0) {
            return 0.0;
        }

        double[] processedSamples = new double[pcmData.length];
        double noisePenalty = 1.0;

        if (Config.advancedNoiseFiltering) {
            double prevX = pcmData[0];
            double prevY_HP = pcmData[0];
            double prevY_LP = prevY_HP;
            int zeroCrossings = 0;

            for (int i = 0; i < pcmData.length; i++) {
                double x = pcmData[i];
                // High-pass filter (カットオフ約300Hz)
                double y_hp = ALPHA_HP * (prevY_HP + x - prevX);
                // Low-pass filter (カットオフ約3000Hz)
                double y_lp = prevY_LP + ALPHA_LP * (y_hp - prevY_LP);
                
                processedSamples[i] = y_lp;
                
                if (i > 0) {
                    if ((processedSamples[i - 1] > 0 && processedSamples[i] <= 0) || (processedSamples[i - 1] < 0 && processedSamples[i] >= 0)) {
                        zeroCrossings++;
                    }
                }
                prevX = x;
                prevY_HP = y_hp;
                prevY_LP = y_lp;
            }

            double zcr = (double) zeroCrossings / pcmData.length;
            // ZCRが高い（高周波数成分が支配的、ホワイトノイズや打鍵音）場合は減衰
            if (zcr > 0.15) {
                noisePenalty = 0.1; // 音量を大幅に下げる
            }
        } else {
            for (int i = 0; i < pcmData.length; i++) {
                processedSamples[i] = pcmData[i];
            }
        }

        // 振幅の絶対値を配列にコピーしてソート
        short[] absSamples = new short[processedSamples.length];
        for (int i = 0; i < processedSamples.length; i++) {
            absSamples[i] = (short) Math.min(Math.abs((int) processedSamples[i]), Short.MAX_VALUE);
        }
        java.util.Arrays.sort(absSamples);

        // 外れ値（突発的なノイズやデジタルポップ音）のみを除外するため、最上位の約1%だけカット
        // ※5%カットだと人間の声のピーク成分まで削られてしまい、全体の音量が下がってしまうため
        int trimCount = Math.max(1, pcmData.length / 100);
        int validLength = pcmData.length - trimCount;

        if (validLength <= 0) {
            return 0.0;
        }

        double sumSquares = 0.0;
        for (int i = 0; i < validLength; i++) {
            sumSquares += (double) absSamples[i] * absSamples[i];
        }
        double rms = Math.sqrt(sumSquares / validLength);

        // ZCRなどのノイズ判定ペナルティを適用
        rms *= noisePenalty;

        // 事実上の無音
        if (rms < 1.0) {
            return 0.0;
        }

        // フルスケール基準のdBに変換
        // dBFS = 20 * log10(rms / 32767)
        double dbfs = 20.0 * Math.log10(rms / Short.MAX_VALUE);
        
        // 人間の声のダイナミックレンジに合わせてスケールを調整
        // Configで設定されたベース値と乗数を使用する
        double scaledDb = baseValue + (dbfs * multiplier);
        return Math.min(200.0, Math.max(0.0, scaledDb));
    }

}
