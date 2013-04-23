YUV420SP
========

Keyword: Android, Camera Preview, NDK, YUV420SPtoARGB

y1CameraPreview: Android App. Project.
Sadmple that use Camera#setPreviewCallbackWithBuffer,
writeback to SurfaceView by Canvas#drawBitmap, with converting
byte[] YUV420SP to int[] ARGB using only Java.

y2NDKYUV420SP: Android App. Project, needs NDK for build.
Edited from y1CameraPreview.
Convert byte[] YUV420SP to int[] ARGB by Native code with JNI.
