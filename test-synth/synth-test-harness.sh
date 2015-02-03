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
        exit 1
    fi
    if test -d "${outputdir}"
    then
        echo "${outputdir} exists. Move it and try again."
        exit 1
    fi
    mkdir "${outputdir}"
    set -x
    orderstr=""
    if ! $treemachine inittax "${inputdir}"/taxonomy.tsv "${inputdir}"/synonyms.tsv "${dbdir}" >"${outputdir}"/inittax.log 2>&1
    then 
        cat "${outputdir}"/inittax.log
        exit 1
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
            exit 1
        fi
        set +x
        shift
    done
    set -x
    if ! $treemachine jsgol life "${dbdir}" >"${outputdir}"/jsgol.log 2>&1
    then 
        cat "${outputdir}"/jsgol.log
        # exit 1
        echo "tolerating failure to dump jsgol now"
    else
        mv life.json "${outputdir}"/life.json
    fi
    if ! $treemachine graphml life "${outputdir}"/graphml.xml T "${dbdir}" >"${outputdir}"/graphml.log 2>&1
    then 
        cat "${outputdir}"/graphml.log
        exit 1
    fi
    if ! $treemachine synthesizedrafttreelist_nodeid 1 "${orderstr},taxonomy" "${dbdir}" >"${outputdir}"/synthesize.log 2>&1
    then 
        cat "${outputdir}"/synthesize.log
        exit 1
    fi
    if ! $treemachine extractdrafttree_nodeid 1 "${outputdir}"/synth.tre "${dbdir}" >"${outputdir}"/extract.log 2>&1
    then 
        cat "${outputdir}"/extract.log
        exit 1
    fi
    set +x
    python rooted-tree-diff.py "${inputdir}"/expected.tre "${outputdir}"/synth.tre || exit
}
