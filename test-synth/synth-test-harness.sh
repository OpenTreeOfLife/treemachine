#!/bin/bash
function testsynthesis {
    tag="$1"
    shift
    outputdir="${tag}-out"
    dbdir="${tag}-test.db"
    inputdir="${tag}"
    treemachinejar=../target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar
    treemachine="java -jar $treemachinejar"
    if test -d "${dbdir}"
    then
        echo "${dbdir} exists. Move it and try again."
        exit
    fi
    if test -d "${outputdir}"
    then
        echo "${outputdir} exists. Move it and try again."
        exit
    fi
    mkdir "${outputdir}"
    echo "about to do the treemachine commands and capture their stdout, stderr..."
    set -x
    orderstr=""
    if ! $treemachine inittax "${inputdir}"/taxonomy.tsv "${inputdir}"/synonyms.tsv "${dbdir}" >"${outputdir}"/inittax.log 2>&1
    then 
        cat "${outputdir}"/inittax.log
        exit
    fi
    set +x
    while (( "$#" ))
    do
        treetag="$1"
        if test -z $orderstr
        then
            orderstr="tree${treetag}"
        else
            orderstr="${orderstr},tree${treetag}"
        fi
        set -x
        if ! $treemachine addnewick "${inputdir}"/tree"${treetag}".tre F life tree"${treetag}" "${dbdir}" >"${outputdir}"/addnewick-tree"${treetag}".log 2>&1
        then 
            cat "${outputdir}"/addnewick-tree"${treetag}".log
            exit
        fi
        set +x
        shift
    done
    set -x
    if ! $treemachine graphml life "${outputdir}"/graphml.xml T "${dbdir}" >"${outputdir}"/graphml.log 2>&1
    then 
        cat "${outputdir}"/graphml.log
        exit
    fi
    if ! $treemachine synthesizedrafttreelist_nodeid 1 "${orderstr},taxonomy" "${dbdir}" >"${outputdir}"/synthesize.log 2>&1
    then 
        cat "${outputdir}"/synthesize.log
        exit
    fi
    if ! $treemachine extractdrafttree_nodeid 1 "${outputdir}"/synth.tre "${dbdir}" >"${outputdir}"/extract.log 2>&1
    then 
        cat "${outputdir}"/extract.log
        exit
    fi
    set +x
    cat "${outputdir}"/synth.tre
}
