#!/usr/bin/env python
'''

Reads a '\t|\t' separated file in which:
    the column is the "normalized" label, and
    the second column is the "original" label
'''
from time import gmtime, strftime
import getpass
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


def read_previous_diagnoses(fo):
    '''
    Returns a dictionary with the original name as the key
    the value is a list of [code, details string, normalized name]

    '''
    d = {}
    for line in fo:
        ls = line.split('\t|\t')
        normalized, original = ls[2], ls[3]
        diagnostic_code = ls[0]
        diagnostic_details = ls[1]
        d[original] = [diagnostic_code, diagnostic_details, normalized]
    return d

class NormalizationLogger(object):
    def __init__(self, log_files):
        self.logf = log_files
        self.num_exact_matches = 0
        self.num_case_i_matches = 0
        self.num_mismatches = 0
        self.num_suspected_typos = 0
        self.num_misspelled = 0
        self.num_abbrev = 0
    def report(self):
        self.num_unclassified_mismatch = self.num_mismatches
        self.num_unclassified_mismatch -= self.num_suspected_typos
        self.num_unclassified_mismatch -= self.num_misspelled
        self.num_unclassified_mismatch -= self.num_abbrev
        for status_log in self.logf:
            status_log.write('#_perfect_matches = %d\n' % self.num_exact_matches)
            status_log.write('#_case_i_matches  = %d\n' % self.num_case_i_matches)
            status_log.write('#_mismatches      = %d\n' % self.num_mismatches)
            status_log.write('#_suspected_typos = %d\n' % self.num_suspected_typos)
            status_log.write('#_num_misspelled  = %d\n' % self.num_misspelled)
            status_log.write('#_abbreviated     = %d\n' % self.num_abbrev)
            status_log.write('#_unclassified    = %d\n' % self.num_unclassified_mismatch)

def query_user(normalized, original, from_prev_run, norm_logger):
    c, d = query_user_impl(normalized, original, from_prev_run)
    if c == 'MISSPELLED':
        norm_logger.num_misspelled += 1
    elif c == 'TYPO':
        norm_logger.num_suspected_typos += 1
    elif c == 'ABBREVIATION':
        norm_logger.num_abbrev += 1
    return c, d
def query_user_impl(normalized, original, from_prev_run):
    sys.stderr.write('"%s" ==>> "%s" caused by:\n' % (original, normalized))
    def_choice = from_prev_run[0]
    def_details = from_prev_run[1]
    cause_list = [['TYPO', ''],
                  ['MISSPELLED', ''],
                  ['ABBREVIATION', ''],
                  ['UNCLASSIFIED', ''],
                 ]
    for i in range(len(cause_list)):
        c, d = cause_list[i]
        if c == def_choice:
            sys.stderr.write('%d * %s\t|\t%s\n' % (1 + i, c, def_details))
        else:
            sys.stderr.write('%d   %s\t|\t%s\n' % (1 + i, c, d))
    for i in range(10):
        if i > 0:
            sys.stderr.write('Invalid choice, try again...\n')
        resp = raw_input('your response ? ')
        if not resp:
            return def_choice, def_details
        try:
            ind = resp.split()[0]
            n = int(resp) - 1
            if n >= 0 and  n < len(cause_list):
                c = cause_list[n][0]
                d = cause_list[n][0]
                gd= resp[len(ind):].strip()
                if gd:
                    return c, gd
                return c, d
        except:
            pass
    raise RuntimeError('number of query attempts exceeded')
if __name__ == '__main__':
    import sys
    if len(sys.argv) < 7:
        sys.exit('''Expecting at least 6 arguments:
1. filepath an OTT taxonomy file,
2. filepath to an OTT synonyms file, and
3. an existing directory for the results
4. a format string with %d in it to indicate the spot for integer interpolation. This will be the generator for the paths to the normalized name files.
5. the first integer to be interpolated
6. the last integer to be interpolated

The normalized name files should be:
    utf-8 encoded,
    "\\t|\\t" delimited file
    with the first column being the normalized name, 
    and the second column being theoriginal name.
''')
    import codecs
    import os
    emitting_repr = 'EMIT_REPR' in os.environ
    reading_repr = 'READING_REPR' in os.environ
    repr_file = 'ott_repr.py'
    output = sys.stdout
    taxonomy_file = sys.argv[1]
    synonyms_file = sys.argv[2]
    results_dir = sys.argv[3]
    if not os.path.exists(results_dir) or not os.path.isdir(results_dir):
        sys.exit('Expecting an existing directory for output, found "%s"' % results_dir)
    fn_fmt = sys.argv[4]
    file_counter_start= int(sys.argv[5])
    file_counter_last = int(sys.argv[6])
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

    assert file_counter_start <= file_counter_last
    for file_counter in range(file_counter_start, 1 + file_counter_last):
        fn = fn_fmt % file_counter
        if not os.path.exists(fn):
            sys.stderr.write('File "%s" not found. Skipping...\n' % fn)
            continue
        infile = codecs.open(fn, mode='r', encoding='utf-8')
        ##############
        # hacky system for creating output for diagnosis of mismatches
        out_dir = os.path.join(results_dir, 'out-%d' % file_counter)
        if os.path.exists(out_dir):
            if not os.path.isdir(out_dir):
                sys.exit('Directory "%s" is in the way\n' % out_dir)
        else:
            os.makedirs(out_dir)
        auto_diagnosed_filename = os.path.join(out_dir, 'auto-diagnosed.txt')
        manually_diagnosed_filename = os.path.join(out_dir, 'manually-diagnosed.txt')
        status_filename = os.path.join(out_dir, 'status')
        logfile_filename = os.path.join(out_dir, 'log')
        try:
            md = open(manually_diagnosed_filename, 'rU')
            previously_diagnosed = read_previous_diagnoses(md)
            md.close()
        except:
            previously_diagnosed = {}
        auto_diagnosed = open(auto_diagnosed_filename, 'w')
        manually_diagnosed = open(manually_diagnosed_filename, 'a')
        status_log = open(status_filename, 'w')
        growing_log = open(logfile_filename, 'a')
        
        timestamp = strftime("%d %b %Y %H:%M:%S", gmtime())
        username = getpass.getuser()

        norm_logger = NormalizationLogger([status_log, growing_log])
        for line_num, line in enumerate(infile):
            ls = line.split('\t|\t')
            if len(ls) < 2:
                if line.strip():
                    sys.exit('Expecting at least two columns. Found:\n%sin file "%s"' % (line, fn))
                continue
            normalized, original = ls[0], ls[1]
            if normalized == original:
                norm_logger.num_exact_matches += 1
            elif normalized.lower() == original.lower():
                norm_logger.num_case_i_matches += 1
            else:
                norm_logger.num_mismatches += 1
                suffix = '\t|\t%s\t|\t%s\t|\t%s\t|\t%s\n' % (normalized, original, timestamp, username)
                ld = levenshtein(normalized, original)
                if ld < 3 and abs(len(normalized) - len(original)) < 3:
                    norm_logger.num_suspected_typos += 1
                    s = 'TYPO\t|\tlevenshtein=%d%s' % (ld, suffix)
                    auto_diagnosed.write(s)
                    sys.stdout.write(s)
                else:
                    from_prev_run = previously_diagnosed.get(original, ['UNCLASSIFIED', ''])
                    fpr_code = from_prev_run[0]
                    fpr_details = from_prev_run[0]
                    diag_code, diag_details = query_user(normalized, 
                                                         original,
                                                         from_prev_run,
                                                         norm_logger)
                    if diag_code != 'UNCLASSIFIED' and (
                        diag_code != fpr_code or diag_details != fpr_details):
                        s = '%s\t|\t%s%s' % (diag_code, diag_details, suffix)
                        manually_diagnosed.write(s)
                        sys.stdout.write(s)
        norm_logger.report()