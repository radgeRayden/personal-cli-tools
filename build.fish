#!/usr/bin/env fish

mkdir -p "./obj"
mkdir -p "./bin"
scopes -e ./build.sc

function compile -a program;
    gcc -O2 -o ./bin/$program ./obj/$program.o -I./include ./src/remimu.c -DSTB_SPRINTF_IMPLEMENTATION -x c ./include/stb_sprintf.h
    cp -f ./bin/$program ~/.local/bin/$program
end

compile link-roulette
compile playtracker
