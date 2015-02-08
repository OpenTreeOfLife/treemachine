#!/bin/bash
function testsynthesis {
    tag="$1"
    shift
    outputdir="${tag}/out"
    dbdir="${tag}/test.db"
    inputdir="${tag}"
    treemachinejar=../target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar
    treemachine="java -enableassertions -jar $treemachinejar"
    if test -d "${dbdir}"
    then
        echo "${dbdir} exists. Move it and try again."
        exit 1
    fi
    if test -d "${outputdir}"
    then
        echo "${outputdir} exists. Overwriting its contents..."
    else
        mkdir "${outputdir}"
    fi
    set -x
    orderstr=""
    if ! $treemachine inittax "${inputdir}"/taxonomy.tsv "${inputdir}"/synonyms.tsv "${dbdir}" >"${outputdir}"/inittax.log 2>&1
    then 
        cat "${outputdir}"/inittax.log
        exit 1
    fi
    set +x
    rm "${outputdir}"/tree-order.txt
    echo '#inputs in order:' > "${outputdir}"/summary.tre
    while (( "$#" ))
    do
        treetag="$1"
        echo $treetag >> "${outputdir}"/tree-order.txt
        if test -z $orderstr
        then
            orderstr="tree${treetag}"
        else
            orderstr="${orderstr},tree${treetag}"
        fi
        cat "${inputdir}"/tree"${treetag}".tre >> "${outputdir}"/summary.tre
        echo >> "${outputdir}"/summary.tre
        set -x
        if ! $treemachine addnewick "${inputdir}"/tree"${treetag}".tre F life tree"${treetag}" "${dbdir}" >"${outputdir}"/addnewick-tree"${treetag}".log 2>&1
        then 
            cat "${outputdir}"/addnewick-tree"${treetag}".log
            exit 1
        fi
        if ! $treemachine mapcompat "${dbdir}" tree"${treetag}" >"${outputdir}"/mapcompat-tree"${treetag}".log 2>&1
        then
            cat "${outputdir}"/mapcompat-tree"${treetag}".log
            exit 1
        fi
        if ! $treemachine exporttodot life "${outputdir}"/pre-synthgraph-after-tree"${treetag}".dot T "${dbdir}" >"${outputdir}"/pre-synthgraph-after-tree"${treetag}".log 2>&1
        then
            cat "${outputdir}"/pre-synthgraph-after-tree"${treetag}".log
            exit 1
        fi
        dot "${outputdir}"/pre-synthgraph-after-tree"${treetag}".dot -Tsvg -o "${outputdir}"/pre-synthgraph-after-tree"${treetag}".svg

        set +x
        shift
    done
    for rep in 1 #2 3 4
    do
        for treetag in $(cat "${outputdir}"/tree-order.txt)
        do
            set -x
            if ! $treemachine pgdelind "${dbdir}" tree"${treetag}" >"${outputdir}"/pgdelind-tree"${treetag}".log 2>&1
            then
                cat "${outputdir}"/pgdelind-tree"${treetag}".log
                exit 1
            fi
            if ! $treemachine addnewick "${inputdir}"/tree"${treetag}".tre F life tree"${treetag}" "${dbdir}" >"${outputdir}"/rep${rep}-addnewick-tree"${treetag}".log 2>&1
            then
                cat "${outputdir}"/rep${rep}-addnewick-tree"${treetag}".log
                exit 1
            fi
            if ! $treemachine mapcompat "${dbdir}" tree"${treetag}" >"${outputdir}"/rep${rep}-mapcompat-tree"${treetag}".log 2>&1
            then 
                cat "${outputdir}"/rep${rep}-mapcompat-tree"${treetag}".log
                exit 1
            fi
            if ! $treemachine exporttodot life "${outputdir}"/pre-synthgraph-rep${rep}-after-tree"${treetag}".dot T "${dbdir}" >"${outputdir}"/pre-synthgraph-rep${rep}-after-tree"${treetag}".log 2>&1
            then
                cat "${outputdir}"/pre-synthgraph-rep${rep}-after-tree"${treetag}".log
                exit 1
            fi
            dot "${outputdir}"/pre-synthgraph-rep${rep}-after-tree"${treetag}".dot -Tsvg -o "${outputdir}"/pre-synthgraph-rep${rep}-after-tree"${treetag}".svg
            set +x
        done
    done
    set -x
    if ! $treemachine exporttodot life "${outputdir}"/pre-synthgraph.dot T "${dbdir}" >"${outputdir}"/pre-synthgraph.log 2>&1
    then 
        cat "${outputdir}"/pre-synthgraph.log
        exit 1
    fi
    dot "${outputdir}"/pre-synthgraph.dot -Tsvg -o "${outputdir}"/pre-synthgraph.svg

    if ! $treemachine synthesizedrafttreelist_nodeid 1 "${orderstr},taxonomy" "${dbdir}" >"${outputdir}"/synthesize.log 2>&1
    then 
        cat "${outputdir}"/synthesize.log
        exit 1
    fi

    if ! $treemachine exporttodot life "${outputdir}"/graph.dot T "${dbdir}" >"${outputdir}"/graph.log 2>&1
    then 
        cat "${outputdir}"/graph.log
        exit 1
    fi
    dot "${outputdir}"/graph.dot -Tsvg -o "${outputdir}"/graph.svg

    if ! $treemachine extractdrafttree_nodeid 1 "${outputdir}"/synth.tre "${dbdir}" >"${outputdir}"/extract.log 2>&1
    then 
        cat "${outputdir}"/extract.log
        exit 1
    fi
    echo "#expected output" >> "${outputdir}"/summary.tre
    cat "${inputdir}"/expected.tre >> "${outputdir}"/summary.tre
    echo >> "${outputdir}"/summary.tre
    echo "#synthetic tree returned" >> "${outputdir}"/summary.tre
    cat "${outputdir}"/synth.tre >> "${outputdir}"/summary.tre
    echo >> "${outputdir}"/summary.tre
        
    set +x
    if python rooted-tree-diff.py "${inputdir}"/expected.tre "${outputdir}"/synth.tre
    then
        echo "# Passed" >> "${outputdir}"/summary.tre
    else
        echo "# FAILED" >> "${outputdir}"/summary.tre
        exit 1
    fi
}
