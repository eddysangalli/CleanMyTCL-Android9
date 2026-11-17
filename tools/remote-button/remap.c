/*
 * remap.c — instala um mapeamento scancode->keycode na tabela do kernel, via evdev.
 *
 * Serve para os botões dedicados do controle da TCL BeyondTV2 (Netflix, Globoplay), que
 * mandam MSC_SCAN mas não geram EV_KEY porque não existe keylayout para o controle.
 *
 * Baseado no diagnóstico e na abordagem de maicoopereira/tcl-beyondtv-buttons (Apache-2.0).
 * Esta versão acrescenta:
 *  - modo --probe: só tenta abrir o device e diz se o ioctl é permitido, sem alterar nada;
 *  - leitura do mapeamento atual (EVIOCGKEYCODE_V2) para provar antes/depois;
 *  - mensagens de erro por errno, em vez de silêncio.
 *
 * Compilar (armeabi-v7a, estático):
 *   $NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/armv7a-linux-androideabi26-clang \
 *       -static -Os -o remap remap.c
 *
 * Uso:
 *   remap --probe <device>
 *   remap <device> <scancode> <keycode>
 *   remap --get <device> <scancode>
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/input.h>

/* Android's old kernel headers can lack these; define what we need. */
#ifndef EVIOCSKEYCODE
#define EVIOCSKEYCODE _IOW('E', 0x04, unsigned int[2])
#endif
#ifndef EVIOCGKEYCODE
#define EVIOCGKEYCODE _IOR('E', 0x04, unsigned int[2])
#endif
#ifndef EVIOCSKEYCODE_V2
#define EVIOCSKEYCODE_V2 _IOW('E', 0x04, struct input_keymap_entry)
#endif
#ifndef EVIOCGKEYCODE_V2
#define EVIOCGKEYCODE_V2 _IOR('E', 0x04, struct input_keymap_entry)
#endif
#ifndef KEY_CNT
#define KEY_CNT (KEY_MAX + 1)
#endif

static void err(const char *what) {
    fprintf(stderr, "%s: %s (errno %d)\n", what, strerror(errno), errno);
}

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr,
                "uso:\n"
                "  %s --probe <device>\n"
                "  %s <device> <scancode> <keycode>\n"
                "  %s --get <device> <scancode>\n", argv[0], argv[0], argv[0]);
        return 2;
    }

    int probe = strcmp(argv[1], "--probe") == 0;
    int get   = strcmp(argv[1], "--get") == 0;
    const char *dev = (probe || get) ? argv[2] : argv[1];

    /* O_RDWR is the point: EVIOCSKEYCODE needs a writable fd, and the shell user is in
     * the input group, which owns /dev/input/event* as crw-rw----. */
    int fd = open(dev, O_RDWR);
    if (fd < 0) {
        err("open O_RDWR");
        fprintf(stderr, "  -> sem permissao de escrita neste device\n");
        return 1;
    }

    char name[64] = {0};
    ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
    printf("device: %s (%s)\n", dev, name[0] ? name : "sem nome");

    if (probe) {
        /* Read a known scancode; if even the read is denied, the write will be too.
         * We do NOT write: a probe must leave the device untouched. */
        struct input_keymap_entry e;
        memset(&e, 0, sizeof(e));
        e.flags = 0;
        e.len = 4;
        unsigned int sc = 0x000c0051;
        memcpy(e.scancode, &sc, 4);
        e.keycode = 0;
        if (ioctl(fd, EVIOCGKEYCODE_V2, &e) == 0) {
            printf("probe: EVIOCGKEYCODE_V2 OK -> keycode atual de 0x%08x = %u\n", sc, e.keycode);
            printf("probe: ioctl aceito neste kernel\n");
            close(fd);
            return 0;
        }
        int v2err = errno;
        unsigned int kc[2] = {sc, 0};
        if (ioctl(fd, EVIOCGKEYCODE, kc) == 0) {
            printf("probe: EVIOCGKEYCODE (v1) OK -> keycode de 0x%08x = %u\n", sc, kc[1]);
            printf("probe: ioctl aceito (v1; o V2 falhou com errno %d)\n", v2err);
            close(fd);
            return 0;
        }
        err("EVIOCGKEYCODE_V2");
        err("EVIOCGKEYCODE");
        close(fd);
        return 1;
    }

    if (get) {
        if (argc < 4) { fprintf(stderr, "--get precisa do scancode\n"); close(fd); return 2; }
        unsigned int sc = (unsigned int) strtoul(argv[3], NULL, 0);
        struct input_keymap_entry e;
        memset(&e, 0, sizeof(e));
        e.len = 4;
        memcpy(e.scancode, &sc, 4);
        if (ioctl(fd, EVIOCGKEYCODE_V2, &e) != 0) { err("EVIOCGKEYCODE_V2"); close(fd); return 1; }
        printf("scancode 0x%08x -> keycode %u\n", sc, e.keycode);
        close(fd);
        return 0;
    }

    if (argc < 4) { fprintf(stderr, "faltam scancode e keycode\n"); close(fd); return 2; }
    unsigned int scancode = (unsigned int) strtoul(argv[2], NULL, 0);
    unsigned int keycode  = (unsigned int) strtoul(argv[3], NULL, 0);

    struct input_keymap_entry e;
    memset(&e, 0, sizeof(e));
    e.flags = 0;
    e.len = 4;
    memcpy(e.scancode, &scancode, 4);
    e.keycode = (unsigned short) keycode;

    if (ioctl(fd, EVIOCSKEYCODE_V2, &e) == 0) {
        printf("OK (V2): 0x%08x -> %u\n", scancode, keycode);
        close(fd);
        return 0;
    }
    int v2err = errno;
    fprintf(stderr, "EVIOCSKEYCODE_V2 falhou: %s (errno %d)\n", strerror(v2err), v2err);

    /* v1 takes [scancode, keycode] and covers scancodes that fit in 32 bits, which these do. */
    unsigned int kc[2] = {scancode, keycode};
    if (ioctl(fd, EVIOCSKEYCODE, kc) == 0) {
        printf("OK (V1): 0x%08x -> %u\n", scancode, keycode);
        close(fd);
        return 0;
    }
    err("EVIOCSKEYCODE (V1)");
    close(fd);
    return 1;
}
