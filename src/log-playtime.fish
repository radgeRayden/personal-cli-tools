#!/usr/bin/env fish

set data_path $XDG_DATA_HOME
if [ -z "$XDG_DATA_HOME" ]
    set data_path "$HOME/.local/share/playtracker/"
end
set state_path $XDG_STATE_HOME
if [ -z "$XDG_STATE_HOME" ]
    set state_path "$HOME/.local/state/playtracker/"
end

set this_dir (dirname (status --current-filename))
set operation $argv[1]
set id $argv[2]
set hash (printf "%s" $id | md5sum | cut -f 1 -d " ")

if [ $operation = "start" ]
    echo $id > $state_path/"$hash"_heartbeat.txt
    nohup game-heartbeat $hash &
    disown
else
    rm -f $state_path/"$hash"_heartbeat.txt
end

echo (date -u "+%s") $operation $id >> $data_path/logfile.txt
