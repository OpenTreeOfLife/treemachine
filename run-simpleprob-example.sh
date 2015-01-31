#!/bin/sh
outputdir="simpleprob-out"
dbdir=simpleprob-test.db
inputdir=simpleprob
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
java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar inittax "${inputdir}"/taxonomy.tsv "${inputdir}"/synonyms.tsv "${dbdir}" >"${outputdir}"/inittax.log 2>&1 || exit
java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar addnewick "${inputdir}"/tree1.tre F life tree1 "${dbdir}" >"${outputdir}"/addnewick-tree1.log 2>&1 || exit
java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar addnewick "${inputdir}"/tree2.tre F life tree2 "${dbdir}" >"${outputdir}"/addnewick-tree2.log 2>&1 || exit
java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar graphml life "${outputdir}"/graphml.xml T "${dbdir}" >"${outputdir}"/graphml.log 2>&1 || exit
java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar synthesizedrafttreelist_nodeid 1 tree2,tree1,taxonomy "${dbdir}" >"${outputdir}"/synthesize.log 2>&1 || exit
java -jar target/treemachine-0.0.1-SNAPSHOT-jar-with-dependencies.jar extractdrafttree_nodeid 1 "${outputdir}"/synth.tre "${dbdir}" >"${outputdir}"/extract.log 2>&1 || exit

cat "${outputdir}"/inittax.log
cat "${outputdir}"/addnewick-tree1.log
cat "${outputdir}"/addnewick-tree2.log
cat "${outputdir}"/graphml.log
cat "${outputdir}"/synthesize.log
cat "${outputdir}"/extract.log