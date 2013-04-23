
package youten.redo.y1camerapreview;

/**
 * byte[] YUV420SP→int[] ARGB変換。<br>
 * http://code.google.com/p/android/issues/detail?id=823
 *
 * @author youten
 */
public class JavaFilter {
    /*
     * 【余談】
     * Q. このdecode関数ってJava層でコピペして使うのアリなの？
     * A. 結論から先に言うとリアルタイム処理（画像加工後に10fps以上欲しいケース）では
     * 無しだと思います。
     * JITが10倍近く高速化してくれている様ですが、
     * 1,2GHzぐらいのSoCで単純なYUV420SP→ARGB変換でVGAあたりで50ms程度食べます。
     * Java層のみでの画像加工は諦めて他プラットフォームとの共通化を考えてNDKを、
     * Android特化であればGPU並列計算の恩恵を受けるべくRenderScript一択な感じです。
     */
    static public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) {
                    y = 0;
                }
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) {
                    r = 0;
                } else if (r > 262143) {
                    r = 262143;
                }
                if (g < 0) {
                    g = 0;
                } else if (g > 262143) {
                    g = 262143;
                }
                if (b < 0) {
                    b = 0;
                } else if (b > 262143) {
                    b = 262143;
                }

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }
}
