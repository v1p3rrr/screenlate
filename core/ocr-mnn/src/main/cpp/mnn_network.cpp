// One MNN network with one float input and one float output, for MnnNetwork.kt.
#include <jni.h>

#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>

// Not in MNN's public headers: the runtime's garbage collection, which frees the buffers of the largest input.
#include "core/Backend.hpp"

#include <cstdint>
#include <memory>
#include <vector>

namespace {

struct Network {
    std::shared_ptr<MNN::Interpreter> interpreter;
    MNN::RuntimeInfo runtime;
    MNN::Session* session = nullptr;
    std::vector<int> inputShape;
    std::vector<int> outputShape;
};

// Runtime::onGabageCollect: 0 frees cached memory only, 100 also the buffers of the intermediate tensors.
constexpr int FULL_COLLECTION = 100;

void throwJava(JNIEnv* env, const char* type, const char* message) {
    jclass error = env->FindClass(type);
    if (error != nullptr) env->ThrowNew(error, message);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_vpr_screenlate_core_ocr_mnn_MnnNetwork_nativeLoad(JNIEnv* env, jclass, jstring path, jint threads) {
    const char* file = env->GetStringUTFChars(path, nullptr);
    if (file == nullptr) return 0;
    std::shared_ptr<MNN::Interpreter> interpreter(MNN::Interpreter::createFromFile(file), MNN::Interpreter::destroy);
    env->ReleaseStringUTFChars(path, file);
    if (!interpreter) return 0;
    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CPU;
    config.numThread = threads;
    MNN::BackendConfig backend;
    backend.precision = MNN::BackendConfig::Precision_Normal;
    config.backendConfig = &backend;
    auto runtime = MNN::Interpreter::createRuntime({config});
    if (runtime.first.empty()) return 0;
    auto* session = interpreter->createSession(config, runtime);
    if (session == nullptr) return 0;
    auto* network = new Network();
    network->interpreter = interpreter;
    network->runtime = runtime;
    network->session = session;
    return reinterpret_cast<jlong>(network);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_vpr_screenlate_core_ocr_mnn_MnnNetwork_nativeRun(JNIEnv* env, jclass, jlong handle, jfloatArray data,
                                                         jintArray shape) {
    auto* network = reinterpret_cast<Network*>(handle);
    std::vector<int> dims(env->GetArrayLength(shape));
    env->GetIntArrayRegion(shape, 0, static_cast<jsize>(dims.size()), dims.data());
    int64_t elements = 1;
    for (int dim : dims) {
        if (dim <= 0) {
            throwJava(env, "java/lang/IllegalArgumentException", "Every dimension must be positive");
            return nullptr;
        }
        elements *= dim;
    }
    if (elements != env->GetArrayLength(data)) {
        throwJava(env, "java/lang/IllegalArgumentException", "The data does not match the shape");
        return nullptr;
    }
    auto* input = network->interpreter->getSessionInput(network->session, nullptr);
    if (dims != network->inputShape) {
        network->interpreter->resizeTensor(input, dims);
        network->interpreter->resizeSession(network->session);
        network->inputShape = dims;
    }
    MNN::Tensor host(input, MNN::Tensor::CAFFE);
    if (host.elementSize() != elements) {
        throwJava(env, "java/lang/IllegalStateException", "The network did not take the input shape");
        return nullptr;
    }
    env->GetFloatArrayRegion(data, 0, static_cast<jsize>(elements), host.host<float>());
    input->copyFromHostTensor(&host);
    if (network->interpreter->runSession(network->session) != MNN::NO_ERROR) {
        throwJava(env, "java/lang/IllegalStateException", "The network failed to run");
        return nullptr;
    }
    auto* output = network->interpreter->getSessionOutput(network->session, nullptr);
    MNN::Tensor result(output, MNN::Tensor::CAFFE);
    output->copyToHostTensor(&result);
    network->outputShape = result.shape();
    auto size = static_cast<jsize>(result.elementSize());
    jfloatArray out = env->NewFloatArray(size);
    if (out == nullptr) return nullptr;
    env->SetFloatArrayRegion(out, 0, size, result.host<float>());
    return out;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_vpr_screenlate_core_ocr_mnn_MnnNetwork_nativeOutputShape(JNIEnv* env, jclass, jlong handle) {
    auto* network = reinterpret_cast<Network*>(handle);
    auto size = static_cast<jsize>(network->outputShape.size());
    jintArray out = env->NewIntArray(size);
    if (out == nullptr) return nullptr;
    env->SetIntArrayRegion(out, 0, size, network->outputShape.data());
    return out;
}

// Frees the memory that larger inputs took and leaves the session sized for the small input shape. MNN keeps one
// buffer for the intermediate tensors, as large as the largest input yet, until its runtime collects it; collected,
// the tensors have no memory until the session resizes, and it resizes only for a shape other than its current one.
// So the session goes to a wider shape first and back to the small one after the collection, which takes a small
// buffer again.
extern "C" JNIEXPORT void JNICALL
Java_com_vpr_screenlate_core_ocr_mnn_MnnNetwork_nativeTrim(JNIEnv* env, jclass, jlong handle, jintArray shape) {
    auto* network = reinterpret_cast<Network*>(handle);
    std::vector<int> dims(env->GetArrayLength(shape));
    env->GetIntArrayRegion(shape, 0, static_cast<jsize>(dims.size()), dims.data());
    if (dims.empty()) {
        throwJava(env, "java/lang/IllegalArgumentException", "The shape is empty");
        return;
    }
    auto wider = dims;
    wider.back() *= 2;
    auto* input = network->interpreter->getSessionInput(network->session, nullptr);
    network->interpreter->resizeTensor(input, wider);
    network->interpreter->resizeSession(network->session);
    for (auto& runtime : network->runtime.first) runtime.second->onGabageCollect(FULL_COLLECTION);
    if (network->runtime.second) network->runtime.second->onGabageCollect(FULL_COLLECTION);
    network->interpreter->resizeTensor(input, dims);
    network->interpreter->resizeSession(network->session);
    network->inputShape = dims;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_vpr_screenlate_core_ocr_mnn_MnnNetwork_nativeMemory(JNIEnv*, jclass, jlong handle) {
    auto* network = reinterpret_cast<Network*>(handle);
    float megabytes = 0;
    network->interpreter->getSessionInfo(network->session, MNN::Interpreter::MEMORY, &megabytes);
    return megabytes;
}

extern "C" JNIEXPORT void JNICALL
Java_com_vpr_screenlate_core_ocr_mnn_MnnNetwork_nativeRelease(JNIEnv*, jclass, jlong handle) {
    auto* network = reinterpret_cast<Network*>(handle);
    network->interpreter->releaseSession(network->session);
    delete network;
}
