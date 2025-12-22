#!/usr/bin/env fish

set data_path $XDG_DATA_HOME
if [ -z "$XDG_DATA_HOME" ]
    set data_path "$HOME/.local/share/playtracker/"
end

set state_path $XDG_STATE_HOME
if [ -z "$XDG_STATE_HOME" ]
    set state_path "$HOME/.local/state/playtracker/"
end

for f in $state_path/*_heartbeat.txt
    set -l id (head -n 1 $f)
    set -l timestamp (tail -n 1 $f)
    echo $timestamp end $id >> $data_path/playtracker/logfile.txt
    rm -f $f
end
