using import Array Map print Set String struct radl.IO.FileStream radl.strfmt

from (import radl.regex) let RegexPattern
import .C .common radl.random C.errno

struct LinkInfo
    url : String
    title : String

struct AppContext
    visited-links-path : String
    unvisited-links-path : String
    backup-path : String
    unvisited-links : (Array LinkInfo)
    visited-links : (Array LinkInfo)
    first-import? : bool

global ctx : AppContext

fn load-link-list (file list)
    local unique-links : (Set String)

    # we assume the already loaded list is unique
    for link in list
        'insert unique-links (copy link.url)

    pattern := try! (RegexPattern "^([^ ]+) \\| (.+)$")
    for line in ('lines file)
        result := try! ('match pattern line)
        try ('unwrap result)
        then (info)
            link := LinkInfo (copy (info.captures @ 1)) (copy (info.captures @ 2))
            if (not ('in? unique-links link.url))
                'insert unique-links (copy link.url)
                'append list link
        else ()

fn copy-file (source destination force?)
    let exit-code = 
        if force?
            common.execute-program "/usr/bin/cp" "-f" source destination
        else
            common.execute-program "/usr/bin/cp" source destination
    #TODO: turn into an error
    exit-code == 0

fn backup-links ()
    success? := copy-file ctx.unvisited-links-path ctx.backup-path true
    if (not success?)
        print "Could not make link file backup. Exiting."
        common.exit 1

fn restore-backup ()
    print "Backup is being restored, if existing."
    copy-file ctx.backup-path ctx.unvisited-links-path true
    common.exit 1

fn serialize-links ()
    if (not ctx.first-import?)
        backup-links;
    try (FileStream ctx.unvisited-links-path FileMode.Write)
    then (file)
        try
            for link in ctx.unvisited-links
                'write file f""""${link.url} | ${link.title}
        except (ex)
            print "Write error:" ex
            restore-backup;
    else 
        print "Error: Could not write links file."
        common.exit 1

fn append-to-visited (link)
    try 
        file := (FileStream ctx.visited-links-path FileMode.Append)
        'write file f""""${link.url} | ${link.title}
    except (ex)
        print "Write error:" ex
        restore-backup;

fn select-link ()
    try
        FileStream ctx.unvisited-links-path FileMode.Read
    then (file)
        try! (load-link-list file ctx.unvisited-links)
        if ((countof ctx.unvisited-links) > 0)
            local rng = (radl.random.RNG (u64 (common.clock-monotonic)))
            idx := rng (countof ctx.unvisited-links)
            link := copy (ctx.unvisited-links @ idx)
            print f"Opening link: ${link.url} | ${link.title}"
            common.execute-program "/usr/bin/xdg-open" link.url
            ctx.unvisited-links @ idx = ('pop ctx.unvisited-links)
            serialize-links;
            append-to-visited link
        else
            print "No links left to visit. Congratulations!"
    except (ex)
        print "Could not open links file. Perhaps import some? Error:" ex

fn import-link-file (path)
    # load existing links
    try
        FileStream ctx.unvisited-links-path FileMode.Read
    then (file)
        try! (load-link-list file ctx.unvisited-links)
    else
        ctx.first-import? = true

    # then merge with new file
    try
        FileStream path FileMode.Read
    then (file)
        try! (load-link-list file ctx.unvisited-links)
    except (ex)
        print "Error importing file:" ex
        common.exit 1

    serialize-links;

fn show-help ()
    print "Usage: link-roulette [--import link-file.txt]"

fn main (argc argv)
    data-directory := (common.get-data-directory) .. "/link-roulette"
    common.execute-program "/usr/bin/mkdir" "-p" (copy data-directory)

    ctx.unvisited-links-path = f"${data-directory}/links.txt"
    ctx.visited-links-path = f"${data-directory}/visited-links.txt"
    ctx.backup-path = f"${data-directory}/links.txt.backup"

    if (argc < 2)
        select-link;
    else
        if (('from-rawstring String (argv @ 1)) == S"--import")
            if (argc < 3)
                show-help;
                common.exit 1
            import-link-file ('from-rawstring String (argv @ 2))
        else
            show-help;
            common.exit 1
    0

sugar-if main-module?
    main 0 0
else
    main
