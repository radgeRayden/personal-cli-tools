using import include

let C =
    include
        """"#define _XOPEN_SOURCE 800
            #define _DEFAULT_SOURCE
            #include <time.h>
            #include <stdlib.h>
            #include <sys/wait.h>
            #include <unistd.h>
            #include "wcwidth.c"

do
    from C.extern let clock_gettime system fork execv waitpid exit mktime localtime strftime strptime
    from C.define let CLOCK_MONOTONIC
    from C.struct let timespec tm
    wcwidth := C.extern.mk_wcwidth
    wcswidth := C.extern.mk_wcswidth
    local-scope;
