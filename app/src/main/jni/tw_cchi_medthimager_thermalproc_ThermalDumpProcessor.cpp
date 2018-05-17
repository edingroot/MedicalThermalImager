#include "tw_cchi_medthimager_thermalproc_ThermalDumpProcessor.h"
#include "JNIHelper.h"
#include <opencv2/opencv.hpp>


JNIEXPORT void JNICALL Java_tw_cchi_medthimager_thermalproc_ThermalDumpProcessor_generateThermalImageNative
  (JNIEnv *env, jobject obj, jfloat temp0, jfloat temp255, jlong resultMatAddr) {
    // Init JNI execution environment
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
    for (int i = thermalValue0; i <= thermalValue255; i++) {
        thermalLUT[i] = sum > 255 ? (unsigned char) 255 : (unsigned char) sum;
        sum += hopPer10K;
    }

    for (int i = thermalValue255 + 1; i <= maxThermalValue; i++) {
        thermalLUT[i] = 255;
    }

    // Generate image from LUT
    for (int i = 0; i < width * height; i++) {
        img->data[i] = thermalLUT[thermalValues10[i]];
    }

    // Pass fields back to the java class
    JNIHelper::SetIntArrayField(env, obj, thermalValues10, thermalValues10Length, "thermalValues10");
}
