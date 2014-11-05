#!/bin/bash

## script to grab individual nexson(s) from Phylografter
## Usage:
##     ./getNexson.sh study1ID study2ID ...
## where study*ID is the Phylografter study identifier. For example, to get studies 420 and 1428, type:
##     ./getNexson.sh 1428 420
## It may be necessary to change access permission to make the script executable. Type:
##     chmod 755 getNexson.sh

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
