package io.wax100.simplevoicechatinteraction;


/**
 * Minecraft クラスに依存しない純粋なユーティリティメソッド群。
 * <p>
 * 音声レベル計算やクールダウン判定など、Minecraft のレジストリ初期化なしに
 * テスト可能なロジックをここに集約する。
 */
public final class AudioUtils {

    // フィルタ用の定数 (サンプリングレート 48000Hz 前提)
    private static final double ALPHA_LP = 0.281; // Low-pass at 3000 Hz
    private static final double ALPHA_HP = 0.962; // High-pass at 300 Hz

    private AudioUtils() {
        // ユーティリティクラスのためインスタンス化不可
    }

    /**
     * PCM（パルス符号変調）サンプル配列から音声レベル（dB）を計算する。
     * <p>
     * RMS（二乗平均平方根）を算出し、フルスケール基準でdBに変換する。
     * 外れ値（上位約1%のサンプル）はO(n)のtop-k挿入で除外する。
     * <ul>
     *   <li>0 dB = 最大音量（全サンプルが {@link Short#MAX_VALUE}）</li>
     *   <li>-∞ dB = 無音（RMS が 1.0 未満）</li>
     * </ul>
     * <p>
     * 中間配列のアロケーションを最小化し、ソートを排除した軽量実装。
     *
     * @param pcmData PCMサンプル配列（16ビット符号付き整数）
     * @return 音声レベル（dB SPL相当、0.0〜200.0）。null/空/無音の場合は 0.0
     */
    public static double calculateDbFromPcm(short[] pcmData, double baseValue, double multiplier) {
        if (pcmData == null || pcmData.length == 0) {
            return 0.0;
        }

        int length = pcmData.length;
        // 外れ値除外数（上位約1%）
        int trimCount = Math.max(1, length / 100);
        // 上位trimCount個の二乗値を昇順で保持（topSquared[0]が最小）
        double[] topSquared = new double[trimCount];
        double sumSquares = 0.0;
        double noisePenalty = 1.0;

        if (Config.advancedNoiseFiltering) {
            // バンドパスフィルターを適用しつつ、RMS・ゼロ交差率・top-kを単一パスで同時計算
            double prevX = pcmData[0];
            double prevY_HP = pcmData[0];
            double prevY_LP = prevY_HP;
            double prevFiltered = 0.0;
            int zeroCrossings = 0;

            for (int i = 0; i < length; i++) {
                double x = pcmData[i];
                // High-pass filter (カットオフ約300Hz)
                double y_hp = ALPHA_HP * (prevY_HP + x - prevX);
                // Low-pass filter (カットオフ約3000Hz)
                double y_lp = prevY_LP + ALPHA_LP * (y_hp - prevY_LP);

                double absVal = Math.min(Math.abs(y_lp), (double) Short.MAX_VALUE);
                double squared = absVal * absVal;
                sumSquares += squared;
                insertTopK(topSquared, squared);

                // ゼロ交差率の計算
                if (i > 0 && ((prevFiltered > 0 && y_lp <= 0) || (prevFiltered < 0 && y_lp >= 0))) {
                    zeroCrossings++;
                }
                prevFiltered = y_lp;
                prevX = x;
                prevY_HP = y_hp;
                prevY_LP = y_lp;
            }

            // ZCRが高い（高周波数成分が支配的、ホワイトノイズや打鍵音）場合は減衰
            double zcr = (double) zeroCrossings / length;
            if (zcr > 0.15) {
                noisePenalty = 0.1; // 音量を大幅に下げる
            }
        } else {
            // フィルタリング無効時: PCMデータから直接計算（中間配列アロケーションなし）
            for (int i = 0; i < length; i++) {
                double val = pcmData[i];
                double squared = val * val;
                sumSquares += squared;
                insertTopK(topSquared, squared);
            }
        }

        // 外れ値（突発的なノイズやデジタルポップ音）のみを除外するため、最上位の約1%だけカット
        // ※5%カットだと人間の声のピーク成分まで削られてしまい、全体の音量が下がってしまうため
        int validLength = length - trimCount;
        if (validLength <= 0) {
            return 0.0;
        }

        double topSum = 0.0;
        for (double sq : topSquared) {
            topSum += sq;
        }
        double rms = Math.sqrt((sumSquares - topSum) / validLength);

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

    /**
     * 上位k個の二乗値を昇順配列で管理するための挿入処理。
     * <p>
     * {@code topK[0]}が最小値。新しい値が{@code topK[0]}以下の場合は即座にリターンし、
     * 大きい場合のみ挿入ソートで正しい位置に配置する。
     * trimCount(≒9)に対してO(1)〜O(k)なので、Arrays.sort()のO(n log n)より大幅に高速。
     *
     * @param topK  上位k個の二乗値（昇順）
     * @param value 挿入候補の二乗値
     */
    private static void insertTopK(double[] topK, double value) {
        if (value <= topK[0]) return;
        topK[0] = value;
        for (int j = 1; j < topK.length && topK[j - 1] > topK[j]; j++) {
            double tmp = topK[j];
            topK[j] = topK[j - 1];
            topK[j - 1] = tmp;
        }
    }

}
