// 北斗·群星之眼 —— 天权中枢伪终端中继（自研实现）
//
// 由 Shizuku（shell 身份）/ Dhizuku（设备所有者）/ su（root）启动为独立进程，
// 内部再分配一个 pty 并 fork shell，然后把 pty master 与自身 stdio 之间的字节双向透传。
// 于是宿主应用虽只拿到一对管道，却得到一个**具备完整 pty 语义、且以特权身份运行**的交互终端
// （回显、行编辑、彩色、作业控制、SIGWINCH 皆正常）。
//
// 用法：libtianquanpty.so <shell> <rows> <cols> <sizeFile>
// 环境：BEIDOU_RC       —— 初始化脚本内容（写入 $HOME/.beidou_rc，并以 ENV 指向它）
//       BEIDOU_RC_FILE  —— 初始化脚本落盘路径（缺省 $HOME/.beidou_rc）

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>

static int set_pty_size(int fd, int rows, int cols) {
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    return ioctl(fd, TIOCSWINSZ, &ws);
}

/** 从尺寸文件读取 "行 列"，与上次不同则应用 */
static void sync_size_from_file(int master, const char *path, int *lastRows, int *lastCols) {
    if (path == NULL || *path == '\0') return;
    FILE *f = fopen(path, "r");
    if (f == NULL) return;
    int r = 0, c = 0;
    if (fscanf(f, "%d %d", &r, &c) == 2 && r > 0 && c > 0) {
        if (r != *lastRows || c != *lastCols) {
            set_pty_size(master, r, c);
            *lastRows = r;
            *lastCols = c;
        }
    }
    fclose(f);
}

/** 把 BEIDOU_RC 的内容写入落盘路径，供子 shell 以 ENV 读取 */
static void write_rc(void) {
    const char *content = getenv("BEIDOU_RC");
    if (content == NULL || *content == '\0') return;

    const char *path = getenv("BEIDOU_RC_FILE");
    char defaultPath[512];
    if (path == NULL || *path == '\0') {
        const char *home = getenv("HOME");
        if (home == NULL || *home == '\0') home = "/data/local/tmp";
        snprintf(defaultPath, sizeof(defaultPath), "%s/.beidou_rc", home);
        path = defaultPath;
    }

    FILE *f = fopen(path, "w");
    if (f == NULL) return;
    fputs(content, f);
    fclose(f);
    setenv("ENV", path, 1);
}

int main(int argc, char **argv) {
    const char *shell = (argc > 1) ? argv[1] : "/system/bin/sh";
    int rows = (argc > 2) ? atoi(argv[2]) : 24;
    int cols = (argc > 3) ? atoi(argv[3]) : 80;
    const char *sizeFile = (argc > 4) ? argv[4] : NULL;

    write_rc();

    // 预创建尺寸文件并放开写权限：宿主应用（不同 uid）靠它下发窗口尺寸变化，
    // 因为管道无法传递 SIGWINCH。
    if (sizeFile != NULL && *sizeFile != '\0') {
        FILE *sf = fopen(sizeFile, "w");
        if (sf != NULL) {
            fprintf(sf, "%d %d\n", rows, cols);
            fclose(sf);
            chmod(sizeFile, 0666);
        }
    }

    char slaveName[256];
    int master = posix_openpt(O_RDWR | O_NOCTTY);
    if (master < 0) {
        fprintf(stderr, "posix_openpt: %s\n", strerror(errno));
        return 1;
    }
    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        fprintf(stderr, "grantpt/unlockpt: %s\n", strerror(errno));
        return 1;
    }
    if (ptsname_r(master, slaveName, sizeof(slaveName)) != 0) {
        fprintf(stderr, "ptsname_r: %s\n", strerror(errno));
        return 1;
    }
    set_pty_size(master, rows, cols);

    pid_t pid = fork();
    if (pid < 0) {
        fprintf(stderr, "fork: %s\n", strerror(errno));
        return 1;
    }
    if (pid == 0) {
        setsid();
        int slave = open(slaveName, O_RDWR);
        if (slave < 0) _exit(127);
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        close(master);

        char *args[3];
        args[0] = (char *) shell;
        args[1] = "-i";
        args[2] = NULL;
        execv(shell, args);
        _exit(127);
    }

    // ── 中继循环：stdin(宿主) ↔ pty master ──
    signal(SIGPIPE, SIG_IGN);
    signal(SIGCHLD, SIG_IGN);   // 避免僵尸，退出靠 EOF 检测

    int lastRows = rows;
    int lastCols = cols;
    int ticks = 0;
    char buf[16384];

    for (;;) {
        struct pollfd fds[2];
        fds[0].fd = STDIN_FILENO;  fds[0].events = POLLIN; fds[0].revents = 0;
        fds[1].fd = master;        fds[1].events = POLLIN; fds[1].revents = 0;

        int r = poll(fds, 2, 500);
        if (r < 0) {
            if (errno == EINTR) continue;
            break;
        }

        // 每约 1 秒轮询一次尺寸文件（管道无法传 SIGWINCH）
        if (++ticks >= 2) {
            ticks = 0;
            sync_size_from_file(master, sizeFile, &lastRows, &lastCols);
        }

        if (fds[0].revents & POLLIN) {
            ssize_t n = read(STDIN_FILENO, buf, sizeof(buf));
            if (n <= 0) break;              // 宿主关闭 → 结束
            ssize_t off = 0;
            while (off < n) {
                ssize_t w = write(master, buf + off, (size_t) (n - off));
                if (w <= 0) break;
                off += w;
            }
        }

        if (fds[1].revents & POLLIN) {
            ssize_t n = read(master, buf, sizeof(buf));
            if (n <= 0) break;              // shell 退出
            ssize_t off = 0;
            while (off < n) {
                ssize_t w = write(STDOUT_FILENO, buf + off, (size_t) (n - off));
                if (w <= 0) break;
                off += w;
            }
        }

        if (fds[1].revents & (POLLHUP | POLLERR | POLLNVAL)) break;
    }

    kill(pid, SIGHUP);
    close(master);
    return 0;
}
