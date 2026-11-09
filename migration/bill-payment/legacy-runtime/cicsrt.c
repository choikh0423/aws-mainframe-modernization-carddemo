/*
 * cicstask: runs one CICS task for the demo region.
 *
 *   cicstask PROGRAM      (fd 3 = requests to the region, fd 4 = replies)
 *
 * The region sends the EIB and COMMAREA, this launcher calls the translated
 * COBOL program, and every CALL 'CICSRT' from it is shipped to the region as
 * one framed request.  The region answers with the updated EIB and data
 * areas and whether the task ends (RETURN / XCTL / abend).
 */
#include <stddef.h>
#include <libcob.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define EIB_LEN 85
#define ARGS_LEN (16 + 8 + 8 + 64 + 1 + 1 + 4 * 4)
#define FD_OUT 3
#define FD_IN 4

static unsigned char eib[EIB_LEN];
static unsigned char commarea[32768];

static void xwrite(const void *p, size_t n) {
    const unsigned char *b = p;
    while (n) {
        ssize_t w = write(FD_OUT, b, n);
        if (w <= 0) { perror("cicstask write"); exit(90); }
        b += w; n -= (size_t)w;
    }
}

static void xread(void *p, size_t n) {
    unsigned char *b = p;
    while (n) {
        ssize_t r = read(FD_IN, b, n);
        if (r <= 0) { perror("cicstask read"); exit(91); }
        b += r; n -= (size_t)r;
    }
}

static void put_u32(uint32_t v) { xwrite(&v, 4); }
static uint32_t get_u32(void) { uint32_t v; xread(&v, 4); return v; }

static int32_t arg_len(const unsigned char *args, int i) {
    int32_t v;
    memcpy(&v, args + 16 + 8 + 8 + 64 + 2 + 4 * i, 4);
    return v;
}

int CICSRT(unsigned char *eibp, unsigned char *args, unsigned char *a1,
           unsigned char *a2, unsigned char *a3) {
    unsigned char *areas[3] = {a1, a2, a3};
    int32_t lens[3];
    for (int i = 0; i < 3; i++) lens[i] = arg_len(args, i + 1);
    xwrite("CALL", 4);
    xwrite(args, ARGS_LEN);
    xwrite(eibp, EIB_LEN);
    for (int i = 0; i < 3; i++) {
        put_u32((uint32_t)lens[i]);
        if (lens[i] > 0) xwrite(areas[i], (size_t)lens[i]);
    }
    xread(eibp, EIB_LEN);
    for (int i = 0; i < 3; i++) {
        uint32_t n = get_u32();
        if (n > 0) xread(areas[i], n);
    }
    unsigned char end;
    xread(&end, 1);
    if (end) {
        cob_stop_run(0);
    }
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: cicstask PROGRAM\n"); return 2; }
    xread(eib, EIB_LEN);
    uint32_t calen = get_u32();
    if (calen > sizeof(commarea)) return 3;
    if (calen) xread(commarea, calen);
    cob_init(0, NULL);
    int (*prog)(unsigned char *, unsigned char *) =
        (int (*)(unsigned char *, unsigned char *))cob_resolve(argv[1]);
    if (!prog) {
        fprintf(stderr, "cicstask: %s\n", cob_resolve_error());
        xwrite("NPGM", 4);
        return 4;
    }
    prog(eib, commarea);
    xwrite("GOBK", 4);
    cob_stop_run(0);
    return 0;
}
