using import include

let C =
    include
        """"#include <time.h>
            #include <stdlib.h>
            #include <sys/wait.h>
            #include <unistd.h>

do
    from C.extern let clock_gettime system fork execv waitpid exit
    from C.define let CLOCK_MONOTONIC
    from C.struct let timespec
    local-scope;
