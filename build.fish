#!/usr/bin/env fish

set binf ~/.local/bin

mkdir -p "./obj"
mkdir -p "./bin"
cp -f ./src/wcwidth.c ./include/

scopes -e ./build.sc

function compile -a program;
    gcc -O2 -o ./bin/$program ./obj/$program.o -I./include ./src/remimu.c ./src/wcwidth.c -DSTB_SPRINTF_IMPLEMENTATION -x c ./include/stb_sprintf.h
end

function copy_artifact -a artifact
    set -l plain_name (string match -rg "^(.+)\..+\$" (basename $artifact))
    ln -sf (realpath $artifact) $binf/$plain_name
end

compile link-roulette
compile playtracker
copy_artifact ./bin/link-roulette
copy_artifact ./bin/playtracker

set base "./src/playtracker/"
copy_artifact $base/steam-game-monitor.fish
copy_artifact $base/update-steam-appid-mapping.fish
copy_artifact $base/game-heartbeat.fish
copy_artifact $base/playtracker-crash-recovery.fish
copy_artifact $base/log-playtime.fish
