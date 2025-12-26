using import include

let C =
    include
        """"#include <time.h>
            #include <stdlib.h>
            #include <sys/wait.h>
            #include <unistd.h>
            #include "wcwidth.c"

do
    from C.extern let clock_gettime system fork execv waitpid exit mktime localtime
    from C.define let CLOCK_MONOTONIC
    from C.struct let timespec tm
    wcwidth := C.extern.mk_wcwidth
    wcswidth := C.extern.mk_wcswidth
    local-scope;
