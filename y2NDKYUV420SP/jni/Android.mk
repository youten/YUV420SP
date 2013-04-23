LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_LDLIBS    := -llog
LOCAL_MODULE    := effect
LOCAL_SRC_FILES := effect.c

include $(BUILD_SHARED_LIBRARY)
