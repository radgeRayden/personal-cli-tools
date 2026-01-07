using import Array radl.IO.FileStream String enum struct Map hash Buffer print Capture radl.strfmt slice \
    itertools
import C.stdlib ..common ..chrono UTF-8 ..C

from (import C.stdio) let printf
from (import stb.sprintf) let snprintf
from chrono let Date
regex := import radl.regex

inline mut! (T)
    local _? : T
    _?

fn string-console-width (str)
    local decoded : (Array i32)
    ->> str UTF-8.decoder (view decoded)
    ptr count := 'data decoded
    C.wcswidth ptr count

inline take-limit (init mapf limitf)
    inline take-limit-inner (coll)
        vvv bind child
        do
            let init full? done push = ((coll as Collector))
            local-scope;

        Collector
            inline "start" ()
                _ true init (child.init)
            inline "valid?" (ok? result state...)
                ok? and (child.full? state...)
            inline "finalize" (ok? result state...)
                child.done state...
            inline "insert" (src ok? result state...)
                src := (src)
                result := mapf result src
                ok? := limitf result
                _ ok?
                    if ok?
                        _ result (child.push (() -> src) state...)
                    else
                        _ result state...

fn char-repeat (ch count)
    assert (count >= 0)
    local str : String
    'resize str count
    for c in str
        c = ch
    str

enum EntryKind plain
    Start
    End

struct LogEntry
    timestamp : i64
    kind : EntryKind
    game : String
    platform : String

struct GameInfo
    name : String
    platform : String
    playtime : i64
    pending? : bool
    last-start : i64

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
    steam-games : (Map u32 String)
    total-playtime : i64

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

fn load-steam-appid-mappings ()
    path := f"${(common.get-data-directory)}/playtracker/app-id-mapping.txt"
    local regexp =
        try! (regex.RegexPattern "(\\d+) (.+)$")

    mappings := FileStream path FileMode.Read
    for line in ('lines mappings)
        try ('unwrap ('match regexp line))
        then (info)
            caps := info.captures
            appid := C.stdlib.strtoul (caps @ 1) null 10
            name := copy (caps @ 2)
            'set ctx.steam-games (appid as u32) name

fn load-log-file ()
    path := f"${(common.get-data-directory)}/playtracker/logfile.txt"
    let logfile =
        try (FileStream path FileMode.Read)
        except (ex)
            print f"Could not open logfile. Make sure it exists at ${path}"
            print ex
            common.exit 1

fn parse-log-file (logfile filter)
    entries := ctx.log-entries
    local regexp = 
        try! (regex.RegexPattern "^(\\d+) (start|end) (.+) \\((.+)\\)$")

    try
        for line in ('lines logfile)
            try ('unwrap ('match regexp line))
            then (info)
                caps := info.captures
                timestamp := C.stdlib.strtol (caps @ 1) null 10
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
            else ()
    except (ex)
        print "Error parsing log file:" ex
        common.exit 2

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
                session-time := entry.timestamp - info.last-start
                info.playtime += session-time
                ctx.total-playtime += session-time
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

fn format-game-name (name)
    local name = name
    local width = string-console-width name

    if (width >= 50)
        decoded := ->> name UTF-8.decoder (mut! (Array i32))
        let lhs =
            ->> decoded
                take-limit 0 ((t x) -> (t + (C.wcwidth x)))
                    (t) -> (t <= 22)
                UTF-8.encoder
                mut! String

        let rrhs =
            ->> ('reverse decoded)
                take-limit 0 ((t x) -> (t + (C.wcwidth x)))
                    (t) -> (t <= 22)
                mut! (Array i32)
        name = .. lhs "..." (->> ('reverse rrhs) UTF-8.encoder (mut! String))
        width = string-console-width name

    .. name " " (char-repeat c"." (50 - width - 1))

fn... display-list (line-count : i32 = 20)
    try (load-steam-appid-mappings)
    except (ex) (print f"Could not load steam appid mappings: ${ex}")

    local formatted-time = heapbuffer char 64
    for i in (range 16) (formatted-time @ i = 0)
    for i game in (zip (range line-count) ctx.game-list)
        if (game.platform == "steam")
            game.name = copy ('getdefault ctx.steam-games ((C.stdlib.strtoul game.name null 10) as u32) S"Unknown Steam Game")
        t := game.playtime
        hours minutes seconds := t // 3600, (t // 60) % 60, t % 60
        ptr count := 'data formatted-time
        snprintf ptr (i32 count) "%02d:%02d%:%02d" hours minutes seconds

        local formatted-name = format-game-name (copy game.name)
        printf "%3d. %10s | %s %s\n" (i + 1) ('data formatted-time) (formatted-name as rawstring) (game.platform as rawstring)

fn... calculate-period (start : Date, end : Date)
    ts-start ts-end := chrono.timestamp-day start, chrono.timestamp-day end
    capture period-filter (timestamp) {ts-start ts-end} (timestamp >= ts-start and timestamp < ts-end)
    parse-log-file (load-log-file) period-filter

fn main (argc argv)
    switch argc
    case 1
        logfile := (load-log-file)
        capture allow-all (timestamp) {} true
        parse-log-file logfile allow-all
        display-list;
    case 2
        match ('from-rawstring String (argv @ 1))
        case "help"
            show-help;
        case "month"
            today := (Date.today)
            month-start := Date today.year today.month 1
            let next-month =
                if (today.month == 12)
                    Date (today.year + 1) 1 1
                else
                    Date today.year (today.month + 1) 1

            calculate-period month-start next-month

            total-hours := ctx.total-playtime // 3600
            fractional-hour := ((ctx.total-playtime % 3600) * 10) // 3600
            print f"Playtime for the month of ${(chrono.get-month-name today.month)}, ${today.year} (${total-hours}.${fractional-hour} hours)"
            display-list;
        case "year"
            today := (Date.today)
            calculate-period (Date today.year 1 1) (Date (today.year + 1) 1 1)
            total-hours := ctx.total-playtime // 3600
            fractional-hour := ((ctx.total-playtime % 3600) * 10) // 3600
            print f"Playtime for the year of ${today.year} (${total-hours}.${fractional-hour} hours)"
            display-list;
        default
            show-help;
            return 1
    case 3
        return 1
    default
        show-help;
        return 1

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
