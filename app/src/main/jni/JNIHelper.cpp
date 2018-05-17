#include <stdint.h>
#include "JNIHelper.h"

int JNIHelper::GetIntField(JNIEnv *env, jobject obj, const char *fieldName) {
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "I");
    return (int) env->GetIntField(obj, fieldId);
}

/**
 * @param env
 * @param obj
 * @param fieldName
 * @param array pointer to the result array
 * @return length of the result array
 */
int JNIHelper::GetIntArrayField(JNIEnv *env, jobject obj, int **array, const char *fieldName) {
    // get field [I = Array of int
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "[I");
    // Get the object field, returns JObject (because it’s an Array)
    jobject objArray = env->GetObjectField(obj, fieldId);
    // Cast it to a jfloatarray
    jintArray* iArray = reinterpret_cast<jintArray*>(&objArray);

    // Get the elements
    *array = env->GetIntArrayElements(*iArray, 0);
    return (int) env->GetArrayLength(*iArray);
}

void JNIHelper::SetIntArrayField(JNIEnv *env, jobject obj, int *array, int arrayLength, const char *fieldName) {
    // get field [I = Array of int
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "[I");
    // Get the object field, returns JObject (because it’s an Array)
    jobject objArray = env->GetObjectField(obj, fieldId);
    // Cast it to a jfloatarray
    jintArray* iArray = reinterpret_cast<jintArray*>(&objArray);

    env->SetIntArrayRegion(*iArray, 0, arrayLength, array);
}

float JNIHelper::GetFloatField(JNIEnv *env, jobject obj, const char *fieldName) {
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "F");
    return (int) env->GetFloatField(obj, fieldId);
}

int JNIHelper::GetFloatArrayField(JNIEnv *env, jobject obj, float **array, const char *fieldName) {
    // get field [F = Array of float
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "[F");
    // Get the object field, returns JObject (because it’s an Array)
    jobject objArray = env->GetObjectField(obj, fieldId);
    // Cast it to a jfloatarray
    jfloatArray* fArray = reinterpret_cast<jfloatArray*>(&objArray);

    // Get the elements
    *array = env->GetFloatArrayElements(*fArray, 0);
    return (int) env->GetArrayLength(*fArray);
}

void JNIHelper::SetFloatArrayField(JNIEnv *env, jobject obj, float *array, int arrayLength, const char *fieldName) {
    // get field [F = Array of float
    jfieldID fieldId = env->GetFieldID(env->GetObjectClass(obj), fieldName, "[F");
    // Get the object field, returns JObject (because it’s an Array)
    jobject objArray = env->GetObjectField(obj, fieldId);
    // Cast it to a jfloatarray
    jfloatArray* fArray = reinterpret_cast<jfloatArray*>(&objArray);

    env->SetFloatArrayRegion(*fArray, 0, arrayLength, array);
}
