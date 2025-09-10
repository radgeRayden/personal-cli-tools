using import Array Option String radl.strfmt print
import .C
from (import C.errno) let errno
from (import C.string) let strerror

fn get-environment-variable (name)
    using import C.stdlib
    env-var := getenv name
    if (env-var != null)
        (Option String)
            'from-rawstring String env-var
    else
        (Option String) none

fn get-home-directory ()
    'force-unwrap (get-environment-variable "HOME")

fn get-config-directory ()
    try ('unwrap (get-environment-variable "XDG_CONFIG_HOME"))
    then (dir) dir
    else f"${(get-home-directory)}/.config"

fn get-data-directory ()
    try ('unwrap (get-environment-variable "XDG_DATA_HOME"))
    then (dir) dir
    else f"${(get-home-directory)}/.local/share"

fn clock-monotonic ()
    local ts : C.timespec
    assert ((C.clock_gettime C.CLOCK_MONOTONIC &ts) == 0)
    (ts.tv_sec * 1000000000) + ts.tv_nsec

fn exit (code)
    C.exit code
    unreachable;

fn execute-program (path args...)
    args... := (va-join path args...) null
    local args : (Array rawstring)

    # NOTE: for now the dupe seems necessary to make the borrow checker happy. It's not an
    # issue because the use will never outlive this function, but I'd like to remove it if possible.
    va-map ((arg) -> ('append args (dupe (arg as rawstring)))) args...

    pid := (C.fork)
    if (pid == 0)
        ptr count := 'data args
        result := C.execv path (@ (ptr as (mutable@ (array (mutable@ i8)))))
        exit 1
    elseif (pid == -1)
        err := (errno)
        print f"Failed to create process: ${err} \"${(strerror err)}\""
        -1
    else
        local status : i32
        C.waitpid pid &status 0
        # the exit code is in the 8 least significant bits
        (status & 0xFF00) >> 8

do
    let get-environment-variable get-home-directory get-config-directory get-data-directory \
        clock-monotonic execute-program exit
    local-scope;
