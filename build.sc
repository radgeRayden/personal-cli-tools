inline make-object (name main)
    compile-object
        default-target-triple
        compiler-file-kind-object
        module-dir .. "/obj/" .. name .. ".o"
        do
            let main = (static-typify main i32 (mutable@ rawstring))
            local-scope;

make-object "link-roulette" (import .src.link-roulette)
make-object "playtracker" (import .src.playtracker)
