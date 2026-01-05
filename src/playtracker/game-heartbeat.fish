#!/usr/bin/env fish

set state_path $XDG_STATE_HOME
if [ -z "$XDG_STATE_HOME" ]
    set state_path "$HOME/.local/state/playtracker/"
end

if [ -z $argv[1] ]
    echo "usage: game-heartbeat 'game_hash'"
    exit 1
end

set hash $argv[1]
set filepath $state_path/"$hash"_heartbeat.txt

while true
    sleep 10
    if [ -f "$filepath" ]
        echo (date -u "+%s") >> $filepath
    else
        exit 0
    end
end
