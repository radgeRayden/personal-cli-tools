using import Array radl.IO.FileStream String enum struct Map hash Buffer print Capture radl.strfmt
import C.stdlib .common .chrono

from (import C.stdio) let printf
from (import stb.sprintf) let snprintf
from chrono let Date
regex := import radl.regex

global month-names :=
    arrayof String
        "January"
        "February"
        "March"
        "April"
        "May"
        "June"
        "July"
        "August"
        "September"
        "October"
        "November"
        "December"

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
        try! (regex.RegexPattern "(\\d+) (start|end) (.+) \\((.+)\\)")

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

fn... display-period (start : Date, end : Date)
    ts-start ts-end := chrono.timestamp-day start, chrono.timestamp-day end
    capture period-filter (timestamp) {ts-start ts-end} (timestamp >= ts-start and timestamp < ts-end)
    parse-log-file (load-log-file) period-filter
    display-list;

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
            print f"Playtime for the month of ${month-names @ (today.month - 1)}"
            display-period month-start next-month
        case "year"
            today := (Date.today)
            print f"Playtime for the year of ${today.year}"
            display-period (Date today.year 1 1) (Date (today.year + 1) 1 1)
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
