using import radl.ext struct String
import C.time .C

fn clock-monotonic ()
    local ts : C.timespec
    assert ((C.clock_gettime C.CLOCK_MONOTONIC &ts) == 0)
    (ts.tv_sec * 1000000000) + ts.tv_nsec

fn timestamp-now ()
    C.time.time null

struct Date
    year : i32
    month : i32
    day : i32

    fn... from-tm (tm : C.tm)
    fn today ()
        now := (timestamp-now)
        datetime := C.localtime (typeinit@ now)
        this-type @datetime

    inline... __typecall (cls, year : i32, month : i32 = 1, day : i32 = 1)
        Struct.__typecall cls year month day
    case (cls, c-calendar-time : C.tm)
        this-type 
            c-calendar-time.tm_year + 1900
            c-calendar-time.tm_mon + 1
            c-calendar-time.tm_mday
    case (cls, date-string : String)
        ptr count := 'data date-string
        local calendar-time : C.tm
        local result := C.strptime ptr "%Y-%m-%d" &calendar-time
        if (result == null)
            result = C.strptime ptr "%Y-%m" &calendar-time
        # TODO: change to specific error
        if (result == null) (raise)
        if (@result != 0:i8) (raise)

        this-type calendar-time

fn... timestamp-day (year : i32, month : i32 = 1, day : i32 = 1)
    date-now := C.localtime (typeinit@ (timestamp-now))
    C.mktime
        typeinit@
            tm_year = year - 1900
            tm_mon = month - 1
            tm_mday = day
            tm_gmtoff = date-now.tm_gmtoff
            tm_isdst = -1
case (date : Date)
    this-function date.year date.month date.day

fn days-in-month (year month)
    # we get the days in the month by adding one month then subtracting a day.
    let next-month =
        if (month == 12)
            timestamp-day (year + 1) 1
        else
            timestamp-day year (month + 1)

    # subtracting 12 hours instead of 24 just to avoid any weirdness around the boundaries
    last-day := next-month - (60 * 60 * 12)
    date := C.localtime (typeinit@ last-day)
    date.tm_mday

fn get-month-name (month)
    local result : String
    loop (max-size = 16)
        if (max-size > 64)
            result = "format error"
            break;

        'resize result max-size
        let bytes = 
            C.strftime result 16 "%B"
                typeinit@
                    tm_mon = month - 1

        if (bytes > 0)
            'resize result bytes
            break;
        else
            max-size * 2
    result

do
    let clock-monotonic timestamp-now timestamp-day days-in-month get-month-name
    let Date
    local-scope;
