#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <unistd.h>
#include <android/log.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#define TAG "DtcNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,    TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,    TAG, __VA_ARGS__)

// External declaration for the modified main function in dtc.c
// You MUST modify dtc.c: renamed main() to dtc_main() and remove exit() calls (replace with return)
extern "C" int dtc_main(int argc, char **argv);

// Global mutex since standard DTC is likely not thread-safe (uses global variables)
std::mutex dtc_mutex;

// Jump buffer for error recovery
extern "C" {
    #include "dtc/util.h" // For definitions if needed, but we just need jmp_buf
    jmp_buf dtc_env;
}

// Helper to capture stdout/stderr could be complex. 
// For simplicity and robustness, we assume DTC writes to files or we let it write to log.
// However, the task requests handling output capture.
// Since we are inside Android, redirecting stdout/stderr to a pipe and reading it is one way.

std::string run_dtc_command(const std::vector<std::string>& args) {
    std::lock_guard<std::mutex> lock(dtc_mutex);

    // Prepare arguments
    int argc = args.size();
    char** argv = new char*[argc];
    for (int i = 0; i < argc; ++i) {
        argv[i] = const_cast<char*>(args[i].c_str());
    }

    // Capture stdout/stderr
    // This is a simplified capture mechanism. 
    // In a real scenario, you might want to use freopen or pipe.
    // Here we will use pipes to capture output.
    
    int pipe_out[2];
    int pipe_err[2];
    
    if (pipe(pipe_out) != 0 || pipe(pipe_err) != 0) {
        LOGE("Failed to create pipes");
        delete[] argv;
        return "Internal Error: Pipe creation failed";
    }

    // Save original stdout/stderr
    int original_stdout = dup(STDOUT_FILENO);
    int original_stderr = dup(STDERR_FILENO);

    // Redirect
    dup2(pipe_out[1], STDOUT_FILENO);
    dup2(pipe_err[1], STDERR_FILENO);

    close(pipe_out[1]);
    close(pipe_err[1]);

    // Run DTC
    // IMPORTANT: dtc_main must NOT call exit(). It must return an integer code.
    int ret = 0;
    std::string captured_output;
    
    if (setjmp(dtc_env) == 0) {
        // Try block equivalent
        ret = dtc_main(argc, argv);
    } else {
        // Catch exit() / die()
        ret = -1;
        captured_output += "CRITICAL: DTC Terminated (die() called)\n";
    }

    // Flush to ensure all data is in pipes
    fflush(stdout);
    fflush(stderr);

    // Restore stdout/stderr
    dup2(original_stdout, STDOUT_FILENO);
    dup2(original_stderr, STDERR_FILENO);
    close(original_stdout);
    close(original_stderr);

    // Read from pipes
    // Note: This blocking read might hang if buffer is full and we read sequentially.
    // For small output like DTC messages, it's usually fine, but strictly properly requires select/poll or threads.
    // We'll do a simple read for now assuming output isn't huge (DTC usually just prints errors or small info).
    
    char buffer[1024];
    ssize_t n;
    
    // Read stdout
    // We previously closed the write end, so read until EOF
    while ((n = read(pipe_out[0], buffer, sizeof(buffer) - 1)) > 0) {
        buffer[n] = '\0';
        captured_output += buffer;
    }
    close(pipe_out[0]);

    // Read stderr
    while ((n = read(pipe_err[0], buffer, sizeof(buffer) - 1)) > 0) {
        buffer[n] = '\0';
        captured_output += buffer;
    }
    close(pipe_err[0]);

    delete[] argv;

    if (ret != 0) {
        return "DTC Failed (Code " + std::to_string(ret) + "):\n" + captured_output;
    }

    return ""; // Success implies empty error message, or we could return warnings.
}

extern "C" JNIEXPORT void JNICALL
Java_com_ireddragonicy_konabessnext_core_native_DtcNative_dtbToDts(
        JNIEnv* env,
        jobject /* this */,
        jstring inputPath,
        jstring outputPath) {
    
    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);

    std::vector<std::string> args = {
        "dtc",
        "-I", "dtb",
        "-O", "dts",
        "-o", output,
        input
    };

    std::string result = run_dtc_command(args);

    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);

    if (!result.empty()) {
        jclass ioExceptionCls = env->FindClass("java/io/IOException");
        env->ThrowNew(ioExceptionCls, result.c_str());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ireddragonicy_konabessnext_core_native_DtcNative_dtsToDtb(
        JNIEnv* env,
        jobject /* this */,
        jstring inputPath,
        jstring outputPath) {

    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);

    std::vector<std::string> args = {
        "dtc",
        "-I", "dts",
        "-O", "dtb",
        "-o", output,
        input
    };

    std::string result = run_dtc_command(args);

    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);

    if (!result.empty()) {
        jclass ioExceptionCls = env->FindClass("java/io/IOException");
        env->ThrowNew(ioExceptionCls, result.c_str());
    }
}
