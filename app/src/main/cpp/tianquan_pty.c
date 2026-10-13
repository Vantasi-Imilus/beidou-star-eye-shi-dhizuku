// 北斗·群星之眼 —— 天权中枢原生伪终端助手（自研实现）
//
// 为终端会话分配真正的 pty（posix_openpt + fork + TIOCSCTTY），
// 使 shell 的行编辑、回显、彩色输出、作业控制按终端语义正常工作。
// 仅使用 bionic 提供的接口与 fork 后异步信号安全的调用。

#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "TianquanPty"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/** 分配一对伪终端，返回 master fd，slave 路径写入 slaveName */
static int open_pty(char *slaveName, size_t nameLen) {
    int master = posix_openpt(O_RDWR | O_NOCTTY);
    if (master < 0) {
        LOGE("posix_openpt failed: %s", strerror(errno));
        return -1;
    }
    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        LOGE("grantpt/unlockpt failed: %s", strerror(errno));
        close(master);
        return -1;
    }
    if (ptsname_r(master, slaveName, nameLen) != 0) {
        LOGE("ptsname_r failed: %s", strerror(errno));
        close(master);
        return -1;
    }
    return master;
}

/**
 * 启动一个带 pty 的 shell 会话。
 *
 * @param out 长度 ≥1 的 int 数组，出参：out[0] = 子进程 pid
 * @return pty master fd（调用方以 ParcelFileDescriptor.adoptFd 接管），失败返回 -1
 */
JNIEXPORT jint JNICALL
Java_org_beidou_stareye_feature_tianquan_TianquanPty_open(
        JNIEnv *env, jclass clazz,
        jstring shell_,
        jobjectArray envArr_,
        jstring cwd_,
        jint rows, jint cols,
        jintArray out_) {

    const char *shellPath = (*env)->GetStringUTFChars(env, shell_, NULL);
    if (shellPath == NULL) return -1;

    // 工作目录在父进程内取好，子进程只做 chdir（异步信号安全）
    const char *cwd = cwd_ ? (*env)->GetStringUTFChars(env, cwd_, NULL) : NULL;

    // 参数与环境在 fork 之前于父进程内准备好，避免子进程调用非异步信号安全函数
    char *argv[3];
    argv[0] = (char *) shellPath;
    argv[1] = "-i";
    argv[2] = NULL;

    jsize envCount = envArr_ ? (*env)->GetArrayLength(env, envArr_) : 0;
    char **envp = (char **) calloc((size_t) envCount + 1, sizeof(char *));
    if (envp == NULL) {
        (*env)->ReleaseStringUTFChars(env, shell_, shellPath);
        return -1;
    }
    for (jsize i = 0; i < envCount; i++) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, envArr_, i);
        if (item == NULL) continue;
        const char *cs = (*env)->GetStringUTFChars(env, item, NULL);
        envp[i] = cs ? strdup(cs) : NULL;
        if (cs) (*env)->ReleaseStringUTFChars(env, item, cs);
        (*env)->DeleteLocalRef(env, item);
    }
    envp[envCount] = NULL;

    char slaveName[256];
    int master = open_pty(slaveName, sizeof(slaveName));
    if (master < 0) {
        (*env)->ReleaseStringUTFChars(env, shell_, shellPath);
        if (cwd) (*env)->ReleaseStringUTFChars(env, cwd_, cwd);
        for (jsize i = 0; i < envCount; i++) free(envp[i]);
        free(envp);
        return -1;
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    ioctl(master, TIOCSWINSZ, &ws);

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(master);
        (*env)->ReleaseStringUTFChars(env, shell_, shellPath);
        if (cwd) (*env)->ReleaseStringUTFChars(env, cwd_, cwd);
        for (jsize i = 0; i < envCount; i++) free(envp[i]);
        free(envp);
        return -1;
    }

    if (pid == 0) {
        // 子进程：仅调用异步信号安全函数
        setsid();
        int slave = open(slaveName, O_RDWR);
        if (slave < 0) _exit(127);
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        close(master);
        if (cwd != NULL) chdir(cwd);
        execve(shellPath, argv, envp);
        _exit(127);
    }

    // 父进程
    (*env)->ReleaseStringUTFChars(env, shell_, shellPath);
    if (cwd) (*env)->ReleaseStringUTFChars(env, cwd_, cwd);
    for (jsize i = 0; i < envCount; i++) free(envp[i]);
    free(envp);

    jint pidValue = (jint) pid;
    (*env)->SetIntArrayRegion(env, out_, 0, 1, &pidValue);
    return master;
}

/** 同步终端尺寸到 pty（触发内核向子进程发送 SIGWINCH） */
JNIEXPORT void JNICALL
Java_org_beidou_stareye_feature_tianquan_TianquanPty_setSize(
        JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    ioctl(fd, TIOCSWINSZ, &ws);
}

/** 判断子进程是否已退出（非阻塞），返回退出码；仍在运行返回 -1 */
JNIEXPORT jint JNICALL
Java_org_beidou_stareye_feature_tianquan_TianquanPty_pollExit(
        JNIEnv *env, jclass clazz, jint pid) {
    int status = 0;
    pid_t r = waitpid((pid_t) pid, &status, WNOHANG);
    if (r <= 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}
