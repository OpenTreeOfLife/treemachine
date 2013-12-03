#!/bin/bash
if [ "$1" == "" ]
    then
        printf "Usage: \n./getNexson.sh study1 study2 ...\n"
fi
while [ "$1" != "" ]; do
    echo Getting study $1
    wget -O $1.gz -q http://www.reelab.net/phylografter/study/export_gzipNexSON.json/$1
    gunzip $1.gz
    shift
done
