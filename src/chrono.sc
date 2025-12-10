using import radl.ext struct
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

    fn today ()
        now := (timestamp-now)
        datetime := C.localtime (typeinit@ now)
        this-type 
            datetime.tm_year + 1900
            datetime.tm_mon + 1
            datetime.tm_mday

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

do
    let clock-monotonic timestamp-now timestamp-day days-in-month
    let Date
    local-scope;
