using import Array enum Map slice struct String radl.regex itertools Option
from (import radl.String+) let starts-with? ASCII-tolower
from (import C.stdlib) let strtoll strtod
from (import UTF-8) let decoder char32

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

    # data conversion errors
    UnsupportedIntegerType
    MalformedArgument
    UnrecognizedEnumValue

struct ArgumentParsingError
    index : i32
    kind : ArgumentParsingErrorKind

# DESIGN NOTES
# every parameter resolves to a full name canonical named parameter. Positional, long and
  short named flags are linked to a corresponding field in the context struct, and their shape
  in the argument list works as a shortcut on how to set this property (eg. flags setting a
  parameter to `true` without an explicit value).

spice Symbol->String (sym)
    `[(String (sym as Symbol as string))]

spice collect-enum-fields (ET)
    using import Array radl.String+

    ET as:= type
    local args : (Array Symbol)

    # Scopes CEnum or C enum?
    try
        for arg in ('args ('@ ET '__fields__))
            'append args (('@ (arg as type) 'Name) as Symbol)
    else
        for k v in ('symbols ET)
            if (not (starts-with? (String (k as string)) "_"))
                'append args k

    sc_argument_list_map_new (i32 (countof args))
        inline (i)
            arg := args @ i
            `arg
run-stage;

@@ memo
inline collect-enum-fields (ET)
    collect-enum-fields ET

inline match-string-enum (ET value)
    using import hash radl.String+ switcher print
    tolower := ASCII-tolower

    call
        switcher sw
            va-map
                inline (k)
                    case (static-eval (hash (tolower (k as string))))
                        imply k ET
                collect-enum-fields ET
            default
                raise ArgumentParsingErrorKind.UnrecognizedEnumValue
        hash (tolower value)

spice convert-argument (value T)
    T as:= type

    if (T == bool)
        spice-quote
            match (ASCII-tolower value)
            case (or "true" "on" "yes" "1")
                true
            case (or "false" "off" "no" "0")
                false
            default
                raise ArgumentParsingErrorKind.MalformedArgument
    elseif (T < integer)
        if (('bitcount T) > 64) 
            error
                .. "Cannot convert incoming argument to very wide integer " (tostring T) "."
        spice-quote
            ptr count := 'data value
            local endptr : (mutable rawstring)
            result := strtoll ptr &endptr 0
            if (@endptr != 0)
                raise ArgumentParsingErrorKind.MalformedArgument
            result
    elseif (T < real)
        if (('bitcount T) > 64) 
            error
                .. "Cannot convert incoming argument to very wide real " (tostring T) "."
        spice-quote
            ptr count := 'data value
            local endptr : (mutable rawstring)
            result := strtod ptr &endptr
            if (@endptr != 0)
                raise ArgumentParsingErrorKind.MalformedArgument
            result
    elseif (T < CEnum)
        spice-quote
            match-string-enum T value
    elseif (T == String)
        spice-quote
            imply (copy value) String
    else
        error (.. "Could not convert incoming argument to struct field of type " (tostring T))

fn destructure-list (list)
    if (empty? list)
        return (sc_argument_list_new 0 null)
    head rest := decons list
    if (head == 'square-list or head == 'curly-list)
        head rest := decons list
        `(unpack [(uncomma rest)])
    else
        `(unpack [(uncomma list)])

run-stage;

@@ memo
inline ParameterMap (sourceT)
    struct (.. "ParameterMap<" (tostring sourceT) ">")
        ParameterFunction := @ (raises
                                (function void (viewof String) (mutable& (viewof sourceT)))
                                ArgumentParsingErrorKind) 

        struct NamedParameter
            name : String
            execute : ParameterFunction
            mandatory? : bool
            default-value : String

        named-parameters : (Map String NamedParameter)
        short-names : (Map i32 String)
        parameter-aliases : (Map String String)
        positional-parameters : (Array String)

        fn define-parameters (self)
            va-map
                inline (fT)
                    k T := keyof fT.Type, unqualified fT.Type
                    name := Symbol->String k
                    'set self.named-parameters (copy name)
                        typeinit
                            name = (copy name)
                            execute = 
                                fn "argv-handler" (value ctx) 
                                    let T =
                                        static-if (T < Option)
                                            T.Type
                                        else T
                                    raising ArgumentParsingErrorKind
                                    (getattr ctx k) = (convert-argument (view value) T) as T
                sourceT.__fields__

        inline map-over-metadata (metadata mapf)
            let tuples... =
                # if the list is not defined, do nothing
                static-try
                    static-eval (destructure-list metadata)
                else ()
            va-map 
                inline (t)
                    static-if ((typeof t) == list)
                        mapf (unpack t)
                    else (mapf t)
                tuples...

        fn define-short-names (self)
            map-over-metadata sourceT.ParameterShortNames
                inline (k v)
                    short-name long-name := (char32 (static-eval (k as Symbol as string))), Symbol->String v
                    'set self.short-names short-name (copy long-name)

        fn define-aliases (self)
            map-over-metadata sourceT.ParameterAliases
                inline (...)
                    original aliases... := ...
                    va-map
                        inline (alias)
                            'set self.parameter-aliases 
                                copy (Symbol->String alias)
                                copy (Symbol->String original)
                        aliases...

        fn define-positional-parameters (self)
            map-over-metadata sourceT.PositionalParameters
                inline (param)
                    'append self.positional-parameters (copy (Symbol->String param))

        inline __typecall (cls)
            local self := super-type.__typecall cls
            'define-parameters self
            'define-short-names self
            'define-aliases self
            'define-positional-parameters self
            self

        unlet map-over-metadata

struct ArgumentParser
    fn... parse (self, argc : i32, argv : (@ rawstring), ctx)
        local parameters : (ParameterMap (typeof ctx))
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

# rules for defining the parameter struct
# 1. named parameters are defined as fields in the struct. Numeric types, strings and 
    booleans are allowed. Named parameters are prefixed with `--` in the command line.
# 2. parameters defined as booleans are flags and may take no arguments (implying true). Only the 
    form `--flag=value` takes an argument. An argument following a flag not in this format will be
    interpreted as a parameter name or positional argument.
# 3. A scope called `ParameterShortNames` defines short single character names that alias full
    name parameters. In the case of flags, those can be combined to activate multiple flags at once.
    Combined flags never take arguments. Short names are prefixed with `-` in the command line.
# 4. A scope called `ParameterAliases` defines alternative names for existing named parameters.
# 5. Positional parameters must have a full name equivalent. The field `PositionalParameters` defines
    a symbol list. The position of the symbol in the list corresponds to its position in the arguments
    list. The symbol corresponds to the parameter full name (field in the struct).
# 6. If a field is defined as an Option, that is an optional parameter. It won't cause an error if
    it isn't found in the argument stream.

using import Option
struct ProgramArguments
    shit : String
    poop : i32
    crap? : bool
    dung : (Option f32)

    ParameterShortNames := '[
        s shit,
        p poop,
        c crap?,
    ]
    ParameterAliases := '[(shit feces) (dung manure)]
    PositionalParameters := '[poop dung]

local pm : (ParameterMap ProgramArguments)

fn main (argc argv)
    local ctx : ProgramArguments
    local argp : ArgumentParser
    # 'parse argp argc argv ctx

do
    let ArgumentParser
    local-scope;
