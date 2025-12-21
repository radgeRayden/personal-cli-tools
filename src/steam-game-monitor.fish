#!/usr/bin/env fish

set data_path $XDG_DATA_HOME
if [ -z "$XDG_DATA_HOME" ]
    set data_path "$HOME/.local/share/playtracker/"
end
set state_path $XDG_STATE_HOME
if [ -z "$XDG_STATE_HOME" ]
    set state_path "$HOME/.local/state/playtracker/"
end

mkdir -p $data_path
mkdir -p $state_path

update-steam-appid-mapping

set logfile $data_path/logfile.txt

function timestamp
    date -u "+%s"
end

while true
    set now (timestamp)
    for appid in (ps -ef | rg --pcre2 -or '$1' "SteamLaunch AppId=(\d+) (?!Install=1)" | sort -u)
        echo $now >> $state_path/steam_session_$appid
    end

    for file in $state_path/steam_session_*
        set first_heartbeat (head -n 1 $file)
        set last_heartbeat (tail -n 1 $file)
        if [ (expr $now - $last_heartbeat) -gt 60 ]
            set -l appid (string match -r '\d+$' $file)
            echo $first_heartbeat start $appid '(steam)' >>  $logfile
            echo $last_heartbeat end $appid '(steam)' >> $logfile
            rm $file
        end
    end
    sleep 10
end
