/**
 * Ref:
 *  [Returning a Mat from native JNI to Java]
 *      http://answers.opencv.org/question/12090/returning-a-mat-from-native-jni-to-java/
 *  [Android NDK: Passing complex data between Java and JNI methods]
 *      http://adndevblog.typepad.com/cloud_and_mobile/2013/08/android-ndk-passing-complex-data-to-jni.html
 *  [JNI Types and Data Structures]
 *      https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/types.html
 */

#include "tw_cchi_medthimager_thermalproc_ThermalDumpProcessor.h"
#include "JNIHelper.h"
#include <opencv2/opencv.hpp>
#include <omp.h>
#include <cstring>


JNIEXPORT void JNICALL Java_tw_cchi_medthimager_thermalproc_ThermalDumpProcessor_generateThermalImageNative
  (JNIEnv *env, jobject obj, jfloat temp0, jfloat temp255, jlong resultMatAddr) {
    // Load class fields
    unsigned char *thermalLUT;
    int *thermalValues10;
    int thermalValues10Length = JNIHelper::GetIntArrayField(env, obj, &thermalValues10, "thermalValues10");
    int width = JNIHelper::GetIntField(env, obj, "width");
    int height = JNIHelper::GetIntField(env, obj, "height");
    int minThermalValue = JNIHelper::GetIntField(env, obj, "minThermalValue");
    int maxThermalValue = JNIHelper::GetIntField(env, obj, "maxThermalValue");

    // Create result mat
    cv::Mat *img = (cv::Mat*) resultMatAddr;
    img->create(height, width, CV_8U);

    int thermalValue0 = (int) (temp0 * 10) + 2731;
    int thermalValue255 = (int) (temp255 * 10) + 2731;
    double hopPer10K = 254.0 / (thermalValue255 - thermalValue0);

    printf("generateThermalImage, temp0=%d, temp255=%d, hopPer10K=%.2f\n", thermalValue0, thermalValue255, hopPer10K);

    // Generate thermalValue-grayLevel LUT: thermalLUT[temp10K] = grayLevel (0~255)
    thermalLUT = new unsigned char[1 + (maxThermalValue > thermalValue255 ? maxThermalValue : thermalValue255)];

    // We reserve grayLevel=0 for identifying image moved
    thermalLUT[0] = 0;
    for (int i = minThermalValue; i < thermalValue0; i++) {
        thermalLUT[i] = 1;
    }

    // Effective range: [1, 254]
    double sum = 1;
#pragma omp parallel for
    for (int i = thermalValue0; i <= thermalValue255; i++) {
        thermalLUT[i] = sum > 255 ? (unsigned char) 255 : (unsigned char) sum;
        sum += hopPer10K;
    }

    for (int i = thermalValue255 + 1; i <= maxThermalValue; i++) {
        thermalLUT[i] = 255;
    }

    // Generate image from LUT
#pragma omp parallel for
    for (int i = 0; i < width * height; i++) {
        img->data[i] = thermalLUT[thermalValues10[i]];
    }

    // Pass fields back to the java class
    JNIHelper::SetIntArrayField(env, obj, thermalValues10, thermalValues10Length, "thermalValues10");
}

void Java_tw_cchi_medthimager_thermalproc_ThermalDumpProcessor_cvtThermalValues10Native(
        JNIEnv *env, jobject obj, jintArray thermalValuesJNI) {
    // Load class fields
    jint *thermalValues = env->GetIntArrayElements(thermalValuesJNI, 0);
    int *thermalValues10;
    int thermalValues10Length = JNIHelper::GetIntArrayField(env, obj, &thermalValues10, "thermalValues10");

    // PS. thermalValues10Length is equal to pixelCount
#pragma omp parallel for
    for (int i = 0; i < thermalValues10Length; i++) {
        thermalValues10[i] = thermalValues[i] < 0 ? 0 : thermalValues[i] / 10;
    }

    // Pass fields back to the java class
    JNIHelper::SetIntArrayField(env, obj, thermalValues10, thermalValues10Length, "thermalValues10");
    env->ReleaseIntArrayElements(thermalValuesJNI, thermalValues, 0);
}

void Java_tw_cchi_medthimager_thermalproc_ThermalDumpProcessor_updateThermalHistNative(
        JNIEnv *env, jobject obj) {
    // Load class fields
    int *thermalValues10;
    int thermalValues10Length = JNIHelper::GetIntArrayField(env, obj, &thermalValues10, "thermalValues10");
    int *thermalHist;
    int thermalHistLength = JNIHelper::GetIntArrayField(env, obj, &thermalHist, "thermalHist");
    int pixelCount = thermalValues10Length;

    // Reset histogram to an zero-filled array
    memset(thermalHist, 0, sizeof(int) * tw_cchi_medthimager_thermalproc_ThermalDumpProcessor_MAX_TEMP_ALLOWED);

    int minThermalValue = pixelCount;
    int maxThermalValue = 0;
#pragma omp parallel for
    for (int i = 0; i < pixelCount; i++) {
        thermalHist[thermalValues10[i]]++;
        if (thermalValues10[i] != 0 && thermalValues10[i] < minThermalValue)
            minThermalValue = thermalValues10[i];
        if (thermalValues10[i] > maxThermalValue)
            maxThermalValue = thermalValues10[i];
    }

    // Pass fields back to the java class
    JNIHelper::SetIntArrayField(env, obj, thermalHist, thermalHistLength, "thermalHist");
    JNIHelper::SetIntField(env, obj, minThermalValue, "minThermalValue");
    JNIHelper::SetIntField(env, obj, maxThermalValue, "maxThermalValue");
}
