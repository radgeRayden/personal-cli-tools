#!/usr/bin/env fish

set binf ~/.local/bin

mkdir -p "./obj"
mkdir -p "./bin"
cp -f ./src/wcwidth.c ./include/

scopes -e ./build.sc

function compile -a program;
    gcc -O2 -o ./bin/$program ./obj/$program.o -I./include ./src/remimu.c ./src/wcwidth.c -DSTB_SPRINTF_IMPLEMENTATION -x c ./include/stb_sprintf.h
    ln -sf (realpath ./bin/$program) $binf/$program
end

function copy_script -a script
    ln -sf (realpath ./src/$script.fish) $binf/$script
end

compile link-roulette
compile playtracker
copy_script steam-game-monitor
copy_script update-steam-appid-mapping
copy_script game-heartbeat
copy_script playtracker-crash-recovery
copy_script log-playtime
