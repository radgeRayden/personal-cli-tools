using import Array radl.IO.FileStream String enum struct Map hash Buffer print Capture
import C.stdlib .common
from (import C.stdio) let printf
from (import stb.sprintf) let snprintf
regex := import radl.regex

fn utf8-len (str)
    using import itertools UTF-8
    local len : usize
    ->>
        str
        decoder
        map ((x) -> (len += 1))
        drain
    deref len

fn char-repeat (ch count)
    local str : String
    'resize str count
    for c in str
        c = ch
    str

enum EntryKind plain
    Start
    End

struct LogEntry
    timestamp : u64
    kind : EntryKind
    game : String
    platform : String

struct GameInfo
    name : String
    platform : String
    playtime : u64
    pending? : bool
    last-start : u64

struct GameInfoKey
    name : String
    platform : String

    inline __hash (self)
        hash (hash self.name) (hash self.platform)

    inline __== (thisT otherT)
        static-if ((thisT == this-type) and (otherT == this-type))
            inline (lhs rhs)
                (lhs.name == rhs.name) and (lhs.platform == lhs.platform)

struct AppContext
    log-entries : (Array LogEntry)
    games : (Map GameInfoKey GameInfo)
    game-list : (Array GameInfo)

global ctx : AppContext

fn show-help ()
    print 
        """"usage: playtracker [command]
            Commands:
            display (default): display statistics
                flags:
                    --month (default)
                    --year
                    --period <start> [<end>]
            import <logfile>: import an external logfile


fn parse-log-file (logfile filter)
    entries := ctx.log-entries
    local regexp = 
        try! (regex.RegexPattern "(\\d+) (start|end) (.+) \\((.+)\\)")

    try
        for line in ('lines logfile)
            result := 'match regexp line
            try ('unwrap result)
            then (info)
                caps := info.captures
                timestamp := C.stdlib.strtoul (caps @ 1) null 10
                let kind =
                    match (caps @ 2)
                    case "start"
                        EntryKind.Start
                    case "end"
                        EntryKind.End
                    default
                        continue;

                # TODO: what do we do when a session goes across period boundaries?
                if (filter timestamp)
                    'append entries
                        LogEntry timestamp kind (copy (caps @ 3)) (copy (caps @ 4))
    else
        # return 2
        return;

    games := ctx.games
    for entry in entries
        k := GameInfoKey entry.game entry.platform
        try ('get games k)
        then (info)
            switch entry.kind
            case 'Start
                info.pending? = true
                info.last-start = entry.timestamp
            case 'End
                if (not info.pending?)
                    continue;
                info.pending? = false
                info.playtime += (entry.timestamp - info.last-start)
            default
                abort;
        else
            if (entry.kind == 'End)
                continue;
            'set games (copy k)
                GameInfo (copy entry.game) (copy entry.platform) 0 true (copy entry.timestamp)

    game-list := ctx.game-list
    for k game in games
        'append game-list (copy game)
    'sort game-list ((x) -> (- (i64 x.playtime)))
    ()

fn display-list ()
    local formatted-time = heapbuffer char 64
    for i in (range 16) (formatted-time @ i = 0)
    for i game in (enumerate ctx.game-list)
        t := game.playtime
        hours minutes seconds := t // 3600, (t // 60) % 60, t % 60
        ptr count := 'data formatted-time
        snprintf ptr (i32 count) "%02d:%02d%:%02d" hours minutes seconds
        local formatted-name = game.name .. " " .. (char-repeat c"." (50 - (utf8-len game.name) - 1))
        printf "%3d. %10s | %s %s\n" (i + 1) ('data formatted-time) (formatted-name as rawstring) (game.platform as rawstring)

fn display-monthly-stats ()
    ;

fn import-log-file (path)
    ()

fn main (argc argv)
    switch argc
    case 1
        display-monthly-stats;
    case 2
        if (('from-rawstring String (argv @ 1)) == "help")
            show-help;
            return 0
    case 3
        if (('from-rawstring String (argv @ 1)) == "import")
            import-log-file ('from-rawstring String (argv @ 2))
        else (show-help)
        return 1
    default
        show-help;
        return 1

    path := argv @ 1
    let logfile =
        try (FileStream path FileMode.Read)
        except (ex)
            print ex
            return 1

    capture filter-all (timestamp) {} true
    # start end := 1764547200, 1767139200
    # capture filter-period (timestamp) {start end} (timestamp >= start and timestamp <= end)
    parse-log-file logfile filter-all
    display-list;

    0

sugar-if main-module?
    name argc argv := (script-launch-args)
    argv* := alloca-array rawstring (argc + 1)
    argv* @ 0 = name
    for i in (range 1 (argc + 1))
        argv* @ i = argv @ (i - 1)
    main (argc + 1) argv*
else
    main
