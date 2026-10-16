// One MNN network with one float input and one float output, for MnnModel.kt.
#include <jni.h>

#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>

#include <cstring>
#include <memory>
#include <vector>

namespace {

struct Model {
    std::shared_ptr<MNN::Interpreter> net;
    MNN::Session* session = nullptr;
    std::vector<int> inputShape;
    std::vector<int> outputShape;
};

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_vpr_screenlate_experiments_ocrcompare_MnnModel_nativeLoad(JNIEnv* env, jclass, jstring path, jint threads) {
    const char* file = env->GetStringUTFChars(path, nullptr);
    std::shared_ptr<MNN::Interpreter> net(MNN::Interpreter::createFromFile(file), MNN::Interpreter::destroy);
    env->ReleaseStringUTFChars(path, file);
    if (!net) return 0;
    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CPU;
    config.numThread = threads;
    MNN::BackendConfig backend;
    backend.precision = MNN::BackendConfig::Precision_Normal;
    config.backendConfig = &backend;
    auto* model = new Model();
    model->net = net;
    model->session = net->createSession(config);
    if (model->session == nullptr) {
        delete model;
        return 0;
    }
    return reinterpret_cast<jlong>(model);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_vpr_screenlate_experiments_ocrcompare_MnnModel_nativeRun(JNIEnv* env, jclass, jlong handle, jfloatArray data,
                                                                  jintArray shape) {
    auto* model = reinterpret_cast<Model*>(handle);
    std::vector<int> dims(env->GetArrayLength(shape));
    env->GetIntArrayRegion(shape, 0, static_cast<jsize>(dims.size()), dims.data());
    auto* input = model->net->getSessionInput(model->session, nullptr);
    if (dims != model->inputShape) {
        model->net->resizeTensor(input, dims);
        model->net->resizeSession(model->session);
        model->inputShape = dims;
    }
    MNN::Tensor host(input, MNN::Tensor::CAFFE);
    env->GetFloatArrayRegion(data, 0, env->GetArrayLength(data), host.host<float>());
    input->copyFromHostTensor(&host);
    model->net->runSession(model->session);
    auto* output = model->net->getSessionOutput(model->session, nullptr);
    MNN::Tensor result(output, MNN::Tensor::CAFFE);
    output->copyToHostTensor(&result);
    model->outputShape = result.shape();
    auto size = static_cast<jsize>(result.elementSize());
    jfloatArray out = env->NewFloatArray(size);
    env->SetFloatArrayRegion(out, 0, size, result.host<float>());
    return out;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_vpr_screenlate_experiments_ocrcompare_MnnModel_nativeOutputShape(JNIEnv* env, jclass, jlong handle) {
    auto* model = reinterpret_cast<Model*>(handle);
    auto size = static_cast<jsize>(model->outputShape.size());
    jintArray out = env->NewIntArray(size);
    env->SetIntArrayRegion(out, 0, size, model->outputShape.data());
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_vpr_screenlate_experiments_ocrcompare_MnnModel_nativeRelease(JNIEnv*, jclass, jlong handle) {
    auto* model = reinterpret_cast<Model*>(handle);
    model->net->releaseSession(model->session);
    delete model;
}
