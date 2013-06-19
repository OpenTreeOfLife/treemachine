#!/usr/bin/env python
'''

Reads a '\t|\t' separated file in which:
    the column is the "normalized" label, and
    the second column is the "original" label
'''

class OTT(object):
    def __init__(self, taxonomy, synonyms):
        id2name_list = {}
        id2parent = {}
        name2id = {}
        homonyms_name2id_list = {}
        self.id2name_list = id2name_list
        self.id2parent = id2parent
        self.name2id = name2id
        self.homonyms_name2id_list = homonyms_name2id_list
        if taxonomy is not None:
            skipped = ''' '''
            ti = iter(taxonomy)
            ti.next()
            for line in ti:
                ls = line.split('\t|\t')
                o_id = int(ls[0])
                try:
                    p_id = int(ls[1])
                    id2parent[o_id] = p_id
                except:
                    pass
                n = ls[2]
                u = ls[5].strip()
                if u and n != u:
                    id2name_list[o_id] = [u, n]
                    if n in name2id:
                        h_list = homonyms_name2id_list.setdefault(n, [])
                        if not h_list:
                            h_list.append(name2id[n])
                        if o_id not in h_list:
                            h_list.append(o_id)
                    else:
                        name2id[n]= o_id
                    if u in name2id:
                        h_list = homonyms_name2id_list.setdefault(u, [])
                        if not h_list:
                            h_list.append(name2id[u])
                        if o_id not in h_list:
                            h_list.append(o_id)
                    
                    name2id[u]= o_id
                else:
                    id2name_list[o_id] = [n]
                    name2id[n]= o_id
            for line in synonyms:
                ls = line.split('\t|\t')
                o_id = int(ls[1])
                n = ls[0]
                id2name_list[o_id].append(n)
                if n in name2id:
                    h_list = homonyms_name2id_list.setdefault(n, [])
                    if not h_list:
                        h_list.append(name2id[n])
                    if o_id not in h_list:
                        h_list.append(o_id)
                else:
                    name2id[n]= o_id

def levenshtein(s1, s2):
    '''
    Calculates the Levenshtein distance between two strings.
    This implementation is from:
        http://en.wikibooks.org/wiki/Algorithm_implementation/Strings/Levenshtein_distance#Python
    released under the Creative Commons Attribution/Share-Alike License;
    Last modification date on the page was 30 May 2013, at 09:54.
    '''
    if len(s1) < len(s2):
        return levenshtein(s2, s1)
    # len(s1) >= len(s2)
    if len(s2) == 0:
        return len(s1)
    previous_row = xrange(len(s2) + 1)
    for i, c1 in enumerate(s1):
        current_row = [i + 1]
        for j, c2 in enumerate(s2):
            insertions = previous_row[j + 1] + 1 # j+1 instead of j since previous_row and current_row are one character longer
            deletions = current_row[j] + 1       # than s2
            substitutions = previous_row[j] + (c1 != c2)
            current_row.append(min(insertions, deletions, substitutions))
        previous_row = current_row
    return previous_row[-1]

if __name__ == '__main__':
    import sys
    if len(sys.argv) < 4:
        sys.exit('''Expecting at least 3 arguments:
 1. filepath an OTT taxonomy file,
 2. filepath to an OTT synonyms file, and
 3. at least one filepath to normalized name file.

 The normalized name file should be:
    a utf-8 encoded,
    "\\t|\\t" delimited file
    with the first column being the normalized name, 
    and the second column being theoriginal name.
''')
    import codecs
    import os
    emitting_repr = 'EMIT_REPR' in os.environ
    reading_repr = 'READING_REPR' in os.environ
    repr_file = 'ott_repr.py'
    num_exact_matches = 0
    num_case_i_matches = 0
    num_mismatches = 0
    output = sys.stdout
    taxonomy_file = sys.argv[1]
    synonyms_file = sys.argv[2]
    if reading_repr:
        import cPickle
        ott = cPickle.load(open(repr_file, 'r'))
        sys.exit(0)
    elif emitting_repr:
        ott = OTT(open(taxonomy_file, 'rU'), open(synonyms_file, 'rU'))
        fo = open(repr_file, 'w')
        import cPickle
        cPickle.dump(obj=ott, file=fo, protocol=cPickle.HIGHEST_PROTOCOL)
        sys.exit(0)
    elif 'NO_OTT_VALIDATION' in os.environ:
        ott = OTT(None, None)
    else:
        ott = OTT(open(taxonomy_file, 'rU'), open(synonyms_file, 'rU'))

    for fn in sys.argv[3:]:
        infile = codecs.open(fn, mode='r', encoding='utf-8')
        for line_num, line in enumerate(infile):
            ls = line.split('\t|\t')
            if len(ls) < 2:
                if line.strip():
                    sys.exit('Expecting at least two columns. Found:\n%sin file "%s"' % (line, fn))
                continue
            normalized, original = ls[0], ls[1]
            if normalized == original:
                num_exact_matches += 1
            elif normalized.lower() == original.lower():
                num_case_i_matches += 1
            else:
                num_mismatches += 1
                ld = levenshtein(normalized, original)
                output.write('"%s" != "%s (Levenshtein distance = %d)"\n' % (normalized, original, ld))
    sys.stderr.write('#_perfect_matches = %d\n' % num_exact_matches)
    sys.stderr.write('#_case_i_matches  = %d\n' % num_case_i_matches)
    sys.stderr.write('#_mismatches      = %d\n' % num_mismatches)
