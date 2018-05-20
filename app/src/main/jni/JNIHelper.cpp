#include <stdint.h>
#include "JNIHelper.h"

int JNIHelper::GetIntField(JNIEnv *env, jobject obj, const char *fieldName) {
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "I");
    // if (env->ExceptionOccurred()) { // Do something }
    return reinterpret_cast<int>(env->GetIntField(obj, fieldId));
}

void JNIHelper::SetIntField(JNIEnv *env, jobject obj, int value, const char *fieldName) {
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "I");
    env->SetIntField(obj, fieldId, reinterpret_cast<jint>(value));
}

/**
 * @param env
 * @param obj
 * @param fieldName
 * @param array pointer to the result array
 * @oaram jArray pointer to jintArray for releasing memory
 * @return length of the result array
 */
int JNIHelper::GetIntArrayField(JNIEnv *env, jobject obj, jintArray *jArray, int **array, const char *fieldName) {
    // get field [I = Array of int
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "[I");
    // Get the object field, returns JObject (because it’s an Array), and cast it to a jintArray
    *jArray = reinterpret_cast<jintArray>(env->GetObjectField(obj, fieldId));

    // Get the elements and return array length
    *array = env->GetIntArrayElements(*jArray, 0);
    // if (*array == NULL) { // No memory left ?!?!? }
    return reinterpret_cast<int>(env->GetArrayLength(*jArray));
}

void JNIHelper::SetIntArrayField(JNIEnv *env, jintArray jArray, int *array, int arrayLength) {
    env->SetIntArrayRegion(jArray, 0, arrayLength, array);
}

float JNIHelper::GetFloatField(JNIEnv *env, jobject obj, const char *fieldName) {
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "F");
    return env->GetFloatField(obj, fieldId);
}

void JNIHelper::SetFloatField(JNIEnv *env, jobject obj, float value, const char *fieldName) {
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "F");
    env->SetFloatField(obj, fieldId, value);
}

int JNIHelper::GetFloatArrayField(JNIEnv *env, jobject obj, jfloatArray *jArray, float **array, const char *fieldName) {
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "[F");
    *jArray = reinterpret_cast<jfloatArray >(env->GetObjectField(obj, fieldId));

    // Get the elements and return array length
    *array = env->GetFloatArrayElements(*jArray, 0);
    return reinterpret_cast<int>(env->GetArrayLength(*jArray));
}

void JNIHelper::SetFloatArrayField(JNIEnv *env, jfloatArray jArray, float *array, int arrayLength) {
    env->SetFloatArrayRegion(jArray, 0, arrayLength, array);
}
