#!/bin/sh
taxon=$1
if test -z "$taxon"
then
    echo "ottol_parent: Expecting a taxon name as the first argument"
    exit 1
fi

if test -z "$2"
then
    if test -z "${OTTOL_TAXONOMY}"
    then
        echo "Either the environmental variable OTTOL_TAXONOMY must point to the a taxonomy in"
        echo "node_id,parent_id,name"
        echo "format, or you must specify the path to such a file as your second argument."
        exit 1
    else
        TAXONOMY_PATH="${OTTOL_TAXONOMY}"
    fi
else
    TAXONOMY_PATH="$2"
fi
if ! test -f "${TAXONOMY_PATH}"
then
    echo "The specified taxonomy file \"${TAXONOMY_PATH}\" is not a file"
    exit 1
fi
    

row=$(grep "${taxon}$" "${TAXONOMY_PATH}")
if test -z "$row"
then
    echo "ottol_parent: taxon \"${taxon}\" not found"
    exit 1
fi
parent_id=$(echo $row | awk 'BEGIN { FS = "," } ; {print $2}')

prow=$(grep "^${parent_id}," "${TAXONOMY_PATH}")
if test -z "$prow"
then
    echo "ottol_parent: parent taxon with id \"${parent_id}\" not found"
    exit 1
fi
parent_name=$(echo $prow | awk 'BEGIN { FS = "," } ; {print $3}')
echo "${parent_name}"
