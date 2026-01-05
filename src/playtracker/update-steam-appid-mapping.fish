#!/usr/bin/env fish

set data_path $XDG_DATA_HOME
if [ -z "$XDG_DATA_HOME" ]
    set data_path "$HOME/.local/share/playtracker/"
end

for library in (cat $HOME/.steam/steam/steamapps/libraryfolders.vdf | rg -or '$1/steamapps' "path\"\s+\"(\S+)\"")
    for app in $library/*.acf
        cat $app | rg -or '$1 $2' --multiline --multiline-dotall "appid\"\s+\"(\d+).+name\"\s+\"([^\\\"]+)" >> $data_path/app-id-mapping.txt
    end
end

cat $data_path/app-id-mapping.txt | sort -u > $data_path/app-id-mapping.txt.new
mv $data_path/app-id-mapping.txt.new $data_path/app-id-mapping.txt
