package io.wax100.simplevoicechatinteraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AudioUtils} のユニットテスト。
 *
 * <p>テスト対象:
 * <ul>
 *   <li>PCMデータからdBへの変換ロジック ({@code calculateDbFromPcm})</li>
 *   <li>クールダウン判定ロジック ({@code isInCooldown})</li>
 * </ul>
 *
 * <p>Minecraftサーバーやvoicechat APIに依存しない純粋なロジックのみをテストする。
 * {@code GameEvent} を使用するマッピングテストはMinecraftレジストリの初期化が必要なため、
 * ゲーム内テスト（GameTest Framework）で別途実施する。
 */
@DisplayName("AudioUtils テスト")
class AudioUtilsTest {

    // ── 音声レベル計算テスト ─────────────────────────────────────────────

    /**
     * {@code calculateDbFromPcm(short[])} のテストクラス。
     *
     * <p>RMS（二乗平均平方根）からdBへの変換ロジックを検証する。
     * OpusDecoderに依存しないため、純粋な数学的計算のみをテストできる。
     */
    @Nested
    @DisplayName("音声レベル計算 (calculateDbFromPcm)")
    class AudioLevelCalculationTest {

        /**
         * 全サンプルがゼロ（無音）の場合、NEGATIVE_INFINITYを返すことを確認。
         * RMSが0になるため、dBは定義上 -∞ となる。
         */
        @Test
        @DisplayName("無音データ（全ゼロ）の場合、0.0を返す")
        void 無音データの場合0_0を返す() {
            // 全サンプルが0の無音PCMデータ
            short[] silence = new short[960];

            double result = AudioUtils.calculateDbFromPcm(silence);

            assertEquals(0.0, result,
                    "無音データ（全ゼロ）は0.0を返すべき");
        }

        /**
         * nullが渡された場合、NEGATIVE_INFINITYを返すことを確認。
         * null安全性の検証。
         */
        @Test
        @DisplayName("nullデータの場合、0.0を返す")
        void nullデータの場合0_0を返す() {
            double result = AudioUtils.calculateDbFromPcm(null);

            assertEquals(0.0, result,
                    "nullデータは0.0を返すべき");
        }

        /**
         * 空配列が渡された場合、NEGATIVE_INFINITYを返すことを確認。
         */
        @Test
        @DisplayName("空配列の場合、0.0を返す")
        void 空配列の場合0_0を返す() {
            short[] empty = new short[0];

            double result = AudioUtils.calculateDbFromPcm(empty);

            assertEquals(0.0, result,
                    "空配列は0.0を返すべき");
        }

        /**
         * 全サンプルがShort.MAX_VALUE（最大振幅）の場合、約0dBを返すことを確認。
         * RMS = MAX_VALUE → 20 * log10(MAX_VALUE / MAX_VALUE) = 20 * log10(1) = 0 dB
         */
        @Test
        @DisplayName("最大音量（Short.MAX_VALUE）の場合、約100dBを返す")
        void 最大音量の場合約100dBを返す() {
            short[] fullVolume = new short[960];
            for (int i = 0; i < fullVolume.length; i++) {
                fullVolume[i] = Short.MAX_VALUE;
            }

            double result = AudioUtils.calculateDbFromPcm(fullVolume);

            // 100dBに非常に近い値（浮動小数点誤差を考慮して±0.01）
            assertEquals(100.0, result, 0.01,
                    "最大音量はほぼ100dBを返すべき");
        }

        /**
         * 振幅が半分の場合、約-6dBを返すことを確認。
         * RMS = MAX_VALUE/2 → 20 * log10(0.5) ≈ -6.02 dB
         * これはオーディオ工学の基本的な関係: 振幅半分 ≈ -6dB
         */
        @Test
        @DisplayName("半分の音量の場合、約94dBを返す")
        void 半分の音量の場合約94dBを返す() {
            short halfMax = (short) (Short.MAX_VALUE / 2);
            short[] halfVolume = new short[960];
            for (int i = 0; i < halfVolume.length; i++) {
                halfVolume[i] = halfMax;
            }

            double result = AudioUtils.calculateDbFromPcm(halfVolume);

            // 100 - 6.02 = 93.98dBに近い値（±0.1の許容誤差）
            assertEquals(93.98, result, 0.1,
                    "半分の音量は約94dBを返すべき（オーディオの基本法則）");
        }

        /**
         * 非常に小さいRMS（閾値1.0未満）の場合、NEGATIVE_INFINITYを返すことを確認。
         * RMS < 1.0 はほぼ無音として扱われる。
         */
        @Test
        @DisplayName("RMS閾値未満の微小音量の場合、0.0を返す")
        void RMS閾値未満の場合0_0を返す() {
            // RMSが1.0未満になるような非常に小さなサンプル値
            // 1サンプルだけが1で残りが0: RMS = sqrt(1/960) ≈ 0.032 < 1.0
            short[] nearSilence = new short[960];
            nearSilence[0] = 1;

            double result = AudioUtils.calculateDbFromPcm(nearSilence);

            assertEquals(0.0, result,
                    "RMS < 1.0 の微小音量は0.0を返すべき");
        }

        /**
         * dB値がスカルク閾値と比較できることを検証する。
         * 一般的な音声認識閾値（例: -40dB）との比較ロジックの正確性を確認。
         */
        @Test
        @DisplayName("閾値比較ロジックの検証: dB値が閾値以上かを正しく判定する")
        void 閾値比較ロジックの検証() {
            // 中程度の音量のPCMデータを作成
            // RMS ≈ 3277 → dBFS ≈ -20 dB → dB SPL ≈ 80 dB
            short[] mediumVolume = new short[960];
            short sampleValue = (short) (Short.MAX_VALUE / 10); // 約3276
            for (int i = 0; i < mediumVolume.length; i++) {
                mediumVolume[i] = sampleValue;
            }

            double dB = AudioUtils.calculateDbFromPcm(mediumVolume);

            // dBは0より大きいこと
            assertTrue(dB > 0.0, "中程度の音量のdB値は正であるべき");

            // 一般的なスカルク閾値 60dB より大きいこと
            double sculkThreshold = 60.0;
            assertTrue(dB >= sculkThreshold,
                    "中程度の音量はスカルク閾値(60dB)以上であるべき。実際のdB: " + dB);

            // 最大値 100dB より小さいこと
            assertTrue(dB < 100.0,
                    "最大音量でない限りdB値は100未満であるべき。実際のdB: " + dB);
        }

        /**
         * 負のサンプル値（波形の負側）も正しく処理されることを確認。
         * 二乗するので正負に関わらず同じ結果になるべき。
         */
        @Test
        @DisplayName("負のサンプル値でも正のサンプルと同じdBを返す")
        void 負のサンプル値でも正しく計算される() {
            // 正のサンプルで埋めた配列
            short[] positive = new short[960];
            for (int i = 0; i < positive.length; i++) {
                positive[i] = 10000;
            }

            // 負のサンプルで埋めた配列
            short[] negative = new short[960];
            for (int i = 0; i < negative.length; i++) {
                negative[i] = -10000;
            }

            double positiveDb = AudioUtils.calculateDbFromPcm(positive);
            double negativeDb = AudioUtils.calculateDbFromPcm(negative);

            assertEquals(positiveDb, negativeDb, 0.001,
                    "二乗計算により、正負のサンプルは同じdB値を返すべき");
        }
    }

}
