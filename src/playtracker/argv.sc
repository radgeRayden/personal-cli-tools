using import Array enum Map slice struct String radl.regex itertools
from (import radl.String+) let starts-with?
import UTF-8

struct NamedParameter
    name : String
    execute : (@ (function void (viewof String)))
    mandatory? : bool
    default-value : String

struct PositionalParameter
    execute : (@ (function void (viewof String)))
    mandatory? : bool
    default-value : String

struct FlagParameter
    name : String
    short-name : i32
    execute : (@ (function void))

enum Argument
    Key : String
    Pair : String String
    Flag : i32
    Value : String

enum ArgumentParsingErrorKind plain
    IncompleteArgument
    ExpectedArgument
    UnexpectedArgument
    UnrecognizedFlag
    UnrecognizedParameter
    MissingPositionalArgument

struct ArgumentParsingError
    index : i32
    kind : ArgumentParsingErrorKind

struct ArgumentParser
    named-parameters : (Map String NamedParameter)
    flags : (Map String FlagParameter)
    flag-short-names : (Map i32 String)
    positional-parameters : (Array PositionalParameter)

    fn... parse (self, argc : i32, argv : (@ rawstring))
        local arguments : (Array Argument)
        # canonicalize argument list
        local pair-pattern := try! (RegexPattern "^--(.+?)=(.+)$")
        for i in (range argc)
            arg := 'from-rawstring String (argv @ i)
            try ('unwrap ('match pair-pattern arg))
            then (info)
                k v := info.captures @ 1, info.captures @ 2
                'append arguments (Argument.Pair (copy k) (copy v))
                continue;
            else ()

            if (starts-with? arg "--")
                'append arguments (Argument.Key (trim (rslice arg 2)))
                continue;

            if (starts-with? arg "-")
                flags := rslice arg 1
                ->> flags UTF-8.decoder
                    map ((c) -> ('append arguments (Argument.Flag c)))
                continue;

            'append arguments (Argument.Value arg)

        inline error (i kind)
            raise (ArgumentParsingError i (getattr ArgumentParsingErrorKind kind))

        inline get-arg (i)
            if (i >= (countof arguments))
                error i 'ExpectedArgument
            else
                arguments @ i

        inline get-flag (short-name)
            'get self.flags ('get self.flag-short-names short-name)

        loop (i = 0)
            if (i >= (countof arguments))
                break;
            dispatch (arguments @ i)
            case Key (k)
                # could be a named parameter or a long flag
                try ('get self.named-parameters k)
                then (param)
                    next := i + 1
                    value := get-arg next
                    if (('literal value) == Argument.Value.Literal)
                        inner := 'unsafe-extract-payload value String
                        param.execute inner
                    else
                        error next 'IncompleteArgument
                    repeat (i + 2)
                else ()

                try ('get self.flags k)
                then (flag)
                    flag.execute;
                    repeat (i + 1)
                else (error i 'UnrecognizedParameter)
            case Pair (k v)
                try ('get self.named-parameters k)
                then (param)
                    param.execute v
                else (error i 'UnrecognizedParameter)
                i + 1
            case Flag (f)
                try (get-flag f)
                then (flag) (flag.execute)
                else (error i 'UnrecognizedFlag)
                i + 1
            case Value (v)
                if (empty? self.positional-parameters)
                    error i 'UnexpectedArgument
                param := 'remove self.positional-parameters 0
                param.execute v
                i + 1
            default
                assert false
