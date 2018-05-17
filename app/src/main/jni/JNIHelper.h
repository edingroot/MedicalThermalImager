/**
 * Ref:
 *  [Android NDK: Passing complex data between Java and JNI methods]
 *      http://adndevblog.typepad.com/cloud_and_mobile/2013/08/android-ndk-passing-complex-data-to-jni.html
 *  [Type Signatures]
 *      https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/types.html
 */

#ifndef MEDICALTHERMALIMAGER_JNIHELPER_H
#define MEDICALTHERMALIMAGER_JNIHELPER_H


#include <jni.h>

class JNIHelper {
public:
    static int GetIntField(JNIEnv *env, jobject obj, const char *fieldName);

    static void SetIntField(JNIEnv *env, jobject obj, int value, const char *fieldName);

    static int GetIntArrayField(JNIEnv *env, jobject obj, int **array, const char *fieldName);

    static void SetIntArrayField(JNIEnv *env, jobject obj, int *array, int arrayLength, const char *fieldName);


    static float GetFloatField(JNIEnv *env, jobject obj, const char *fieldName);

    static void SetFloatField(JNIEnv *env, jobject obj, float value, const char *fieldName);

    static int GetFloatArrayField(JNIEnv *env, jobject obj, float **array, const char *fieldName);

    static void SetFloatArrayField(JNIEnv *env, jobject obj, float *array, int arrayLength, const char *fieldName);

};


#endif //MEDICALTHERMALIMAGER_JNIHELPER_H
