#!/usr/bin/env fish

set binf ~/.local/bin

mkdir -p "./obj"
mkdir -p "./bin"
scopes -e ./build.sc

function compile -a program;
    gcc -O2 -o ./bin/$program ./obj/$program.o -I./include ./src/remimu.c -DSTB_SPRINTF_IMPLEMENTATION -x c ./include/stb_sprintf.h
    ln -sf (realpath ./bin/$program) $binf/$program
end

compile link-roulette
compile playtracker
ln -sf (realpath ./src/steam-game-monitor.fish) $binf/steam-game-monitor
ln -sf (realpath ./src/update-steam-appid-mapping.fish) $binf/update-steam-appid-mapping
