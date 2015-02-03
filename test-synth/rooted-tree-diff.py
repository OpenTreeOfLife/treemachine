#!/usr/bin/env python
import sys
import re
BRAINDEAD_NEWICK_SPLITTER = re.compile(r'([(),])')
OTT_SUFFIX = re.compile(r'_ott\d+$')

def grab_next_clade(token_list, ind):
    assert token_list[ind] == '('
    n_excess_open = 1
    next_clade = []
    while True:
        ind += 1
        n = token_list[ind]
        if n == '(':
            n_excess_open += 1
        elif n == ')':
            n_excess_open -= 1
            if n_excess_open == 0:
                break
        next_clade.append(n)
    if ind + 1 < len(token_list):
        n = token_list[ind + 1]
        if n not in [',', ')']: # consume clade label
            return ind + 2, next_clade
    return ind + 1, next_clade

def split_into_clades_newicks(token_list):
    ind = 0
    clade_newicks = []
    while ind < len(token_list):
        word = token_list[ind]
        if word == '(':
            ind, cn = grab_next_clade(token_list, ind)
            clade_newicks.append(cn)
        else:
            if word == ')':
                return clade_newicks
            if word != ',':
                leaf_name = token_list[ind]
                clade_newicks.append([leaf_name])
            ind += 1
    return clade_newicks

def append_clades(tokens, clade_set):
    clade_newick_list = split_into_clades_newicks(tokens)
    leaves = []
    for clade_newick in clade_newick_list:
        if len(clade_newick) > 1:
            cl = append_clades(clade_newick, clade_set)
            leaves.extend(cl)
        else:
            leaves.append(clade_newick[0])
    leaves.sort()
    tl = tuple(leaves)
    if len(clade_newick_list) > 1:
        assert tl not in clade_set
    clade_set.add(tl)
    return tl

def clean_name(tok):
    tok = tok.strip()
    if tok.startswith("'"):
        assert tok.endswith("'")
        tok = tok[1:-1]
    assert tok
    x = OTT_SUFFIX.split(tok)
    if len(x) > 1:
        assert len(x) == 2
    return x[0]

def parse_newick(newick):
    newick = newick.strip()
    print newick
    assert newick.endswith(';')
    newick = newick[:-1]
    assert newick.startswith('(')
    tokens = [clean_name(i) for i in BRAINDEAD_NEWICK_SPLITTER.split(newick) if i.strip()]
    clades = set()
    append_clades(tokens, clades)
    for c in clades:
        sys.stderr.write('clade extracted: {}\n'.format(', '.join(c)))
    return clades

if __name__ == '__main__':
    first, second = sys.argv[1:]
    with open(first, 'rU') as inp:
        ref = parse_newick(inp.read())
    with open(second, 'rU') as inp:
        result = parse_newick(inp.read())
    missing = [c for c in ref if c not in result]
    extra = [c for c in result if c not in ref]
    if missing or extra:
        sys.stdout.write('ERROR: Trees differ.\n')
        missing.sort()
        if missing:
            for m in missing:
                sys.stdout.write('Missing clade: {}\n'.format(', '.join(m)))
        extra.sort()
        if extra:
            for m in extra:
                sys.stdout.write('Extra clade: {}\n'.format(', '.join(m)))
        sys.stderr.write('FAILURE!\n')
        sys.exit(1)
    sys.stderr.write('SUCCESS! recovered expected tree.\n')
    sys.exit(0)