#include <jni.h>
#include <android/log.h>

#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, "Effect", __VA_ARGS__))
void decodeYUV420SP(int *rgb, unsigned char *yuv420sp, int width, int height);

/**
 * JNI IF: decode YUV420SP to ARGB array
 */
void Java_com_jni_NativeFilter_decodeYUV420SP(JNIEnv* env, jobject thiz,
        jintArray rgb, jbyteArray yuv420sp, jint width, jint height) {
    // jsize rgb_len = (*env)->GetArrayLength(env, rgb);
    // jsize yuv_len = (*env)->GetArrayLength(env, yuv);
    // // LOGD("rgb_out[%d], yuv_in[%d], width=%d, height=%d");
    // if ((rgb_out_len <= 0) || (yuv_in_len <= 0)) {
    // 	return;
    // }

    jint *p_rgb = (*env)->GetIntArrayElements(env, rgb, 0);
    jbyte *p_yuv = (*env)->GetByteArrayElements(env, yuv420sp, 0);

    decodeYUV420SP((int *) p_rgb, (unsigned char *) p_yuv, (int) width,
            (int) height);

    (*env)->ReleaseIntArrayElements(env, rgb, p_rgb, 0);
    (*env)->ReleaseByteArrayElements(env, yuv420sp, p_yuv, 0);

    return;
}

void decodeYUV420SP(int *rgb, unsigned char *yuv420sp, int width, int height) {
    int frameSize = width * height;
    int j, yp, uvp, u, v, i, y, y1192, r, g, b;

    for (j = 0, yp = 0; j < height; j++) {
        uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
        for (i = 0; i < width; i++, yp++) {
            y = (0xff & ((int) yuv420sp[yp])) - 16;
            if (y < 0) {
                y = 0;
            }
            if ((i & 1) == 0) {
                v = (0xff & yuv420sp[uvp++]) - 128;
                u = (0xff & yuv420sp[uvp++]) - 128;
            }

            y1192 = 1192 * y;
            r = (y1192 + 1634 * v);
            g = (y1192 - 833 * v - 400 * u);
            b = (y1192 + 2066 * u);

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

            rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00)
                    | ((b >> 10) & 0xff);
        }
    }
}

