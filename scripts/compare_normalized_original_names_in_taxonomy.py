#!/usr/bin/env python
'''

Reads a '\t|\t' separated file in which:
    the column is the "normalized" label, and
    the second column is the "original" label
'''
VERY_VERBOSE = False
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
    def get_names_for_id(self, ott_id):
        try:
            return self.id2name_list[ott_id]
        except:
            return []

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
        self.num_case_i_synonym_matches = 0
        self.num_exact_synonym_matches = 0
    def report(self):
        self.num_unclassified_mismatch = self.num_mismatches
        self.num_unclassified_mismatch -= self.num_suspected_typos
        self.num_unclassified_mismatch -= self.num_misspelled
        self.num_unclassified_mismatch -= self.num_abbrev
        for status_log in self.logf:
            status_log.write('#_perfect_matches    = %d\n' % self.num_exact_matches)
            status_log.write('#_case_i_matches     = %d\n' % self.num_case_i_matches)
            status_log.write('#_perf_syn_matches   = %d\n' % self.num_exact_synonym_matches)
            status_log.write('#_case_i_syn_matches = %d\n' % self.num_case_i_synonym_matches)
            status_log.write('#_mismatches         = %d\n' % self.num_mismatches)
            status_log.write('#_suspected_typos    = %d\n' % self.num_suspected_typos)
            status_log.write('#_num_misspelled     = %d\n' % self.num_misspelled)
            status_log.write('#_abbreviated        = %d\n' % self.num_abbrev)
            status_log.write('#_unclassified       = %d\n' % self.num_unclassified_mismatch)

class AbbrevGen(object):
    def __init__(self, word_crop_list, abbrev_sep_list=None, extra_suffix='', full_sep_list=None):
        '''
        `word_crop_list` = list for each "word" in full name there should be instructions on
            where to crop the word. An integer is interpreted as the index
        abbrev_sep_list a list of strings that are intercalated into the abbreviation.
        extra_suffix a suffix to append onto the "abbreviation"
        full_sep_list the list of separators that delimit "words" in the full name or None if 
            ' ' is the separator for all words
        '''
        self.word_crop_list = word_crop_list
        self.abbrev_sep_list = abbrev_sep_list
        self.full_sep_list = full_sep_list
        self.extra_suffix = extra_suffix

    def __str__(self):
        return repr(self)

    def __repr__(self):
        if self.abbrev_sep_list is not None:
            second = ', abbrev_sep_list=' + repr(self.abbrev_sep_list)
        else:
            second = ''
        if self.extra_suffix:
            third = ', extra_suffix=' + repr(self.extra_suffix)
        else:
            third = ''
        if self.full_sep_list is not None:
            fourth = ', full_sep_list=' + repr(self.full_sep_list)
        else:
            fourth = ''
        return 'AbbrevGen(%s%s%s%s)' % (repr(self.word_crop_list), second, third, fourth)

    def __eq__(self, other):
        return (self.word_crop_list == other.word_crop_list
                and self.extra_suffix == other.extra_suffix
                and self.abbrev_sep_list == other.abbrev_sep_list
                and self.full_sep_list == other.full_sep_list)

    def generate_abbreviation(self, full_name):
        remaining = full_name
        abbrev_list = []
        word_ind = 0
        while True:
            next_sep = self.get_full_sep(word_ind)
            full_list = remaining.split(next_sep)
            word = full_list[0]
            c = self.get_full_crop(word_ind)
            ab = word[:c]
            abbrev_list.append(ab)
            abbrev_list.append(self.get_abbrev_sep(word_ind))
            if len(full_list) > 1:
                remaining = next_sep.join(full_list[1:])
            else:
                break
            word_ind += 1
        if self.extra_suffix:
            abbrev_list.append(self.extra_suffix)
        return ''.join(abbrev_list)

    def get_next_not_full_sep_regex(self, ind):
        sep = self.get_full_sep(ind)
        if len(sep) == 1:
            return '[^%s]' % sep
        else:
            raise NotImplementedError()

    def get_full_sep(self, ind):
        try:
            return self.full_sep_list[ind]
        except:
            return ' '

    def get_full_crop(self, ind):
        try:
            return self.word_crop_list[ind]
        except:
            return 0


    def get_abbrev_sep(self, ind):
        try:
            return self.abbrev_sep_list[ind]
        except:
            return ''

    def generate_regex(self, abbrev):
        '''#Does not cover the case in which the input is too short to need abbreviation.'''
        if self.full_sep_list is None:
            word_ind = 0
            abbrev_ind = 0
            la = len(abbrev)
            regex_list = []
            while True:
                len_contrib_by_curr_word = self.get_full_crop(word_ind)
                next_abbrev_sep = self.get_abbrev_sep(word_ind)
                lnas = len(next_abbrev_sep)
                if len_contrib_by_curr_word + abbrev_ind + lnas <= la:
                    is_last_word = len_contrib_by_curr_word + abbrev_ind + lnas == la
                    curr_word = abbrev[abbrev_ind:(abbrev_ind + len_contrib_by_curr_word)]
                    regex_list.append(curr_word)
                    abbrev_ind += len_contrib_by_curr_word
                    regex_list.append(self.get_next_not_full_sep_regex(word_ind) + '*')
                    expected_sep = abbrev[abbrev_ind:(abbrev_ind+lnas)]
                    if expected_sep != next_abbrev_sep:
                        return None
                    if is_last_word:
                        return regex_list
                    fs = self.get_full_sep(word_ind)
                    regex_list.append(fs)
                else:
                    return regex_list
                word_ind += 1
        else:
            raise NotImplementedError()

class MappingBlob(object):
    '''Structure that holds info one how one mapping (typically one phylografter study)
    has been accomplished (a record of mappings used) and where the study should record its info
    about the mapping.
    '''
    def __init__(self,
                 auto_diagnosed_filepath,
                 manually_diagnosed_filepath,
                 status_filepath,
                 logfile_filepath,
                 taxonomy):
        for fp in [auto_diagnosed_filepath, manually_diagnosed_filepath, status_filepath, logfile_filepath]:
            out_dir = os.path.dirname(fp)
            if os.path.exists(out_dir):
                if not os.path.isdir(out_dir):
                    sys.exit('Directory "%s" is in the way\n' % out_dir)
            else:
                os.makedirs(out_dir)
        self.auto_diagnosed_filepath = auto_diagnosed_filepath
        self.manually_diagnosed_filepath = manually_diagnosed_filepath
        self.status_filepath = status_filepath
        self.logfile_filepath = logfile_filepath
        try:
            md = open(manually_diagnosed_filepath, 'rU')
            self.previously_diagnosed = read_previous_diagnoses(md)
            md.close()
        except:
            self.previously_diagnosed = {}
        self.auto_diagnosed = open(auto_diagnosed_filepath, 'w')
        self.manually_diagnosed = open(manually_diagnosed_filepath, 'a')
        self.status_log = open(status_filepath, 'w')
        self.growing_log = open(logfile_filepath, 'a')
        self.norm_logger = NormalizationLogger([self.status_log, self.growing_log])
        self.timestamp = strftime("%d %b %Y %H:%M:%S", gmtime()) # not sure if this should be a per-study or per annotation timestamp
        self.curr_username = getpass.getuser()
        self.abbrev_collection = [] # pairs of [# usages, AbbrevGen] objects
        self.taxonomy = taxonomy

    def display_analysis_of_nontrivial_mapping(self, s):
        sys.stdout.write(s)

    def analyze_mapping(self, original, normalized, ott_id):
        if normalized == original:
            self.norm_logger.num_exact_matches += 1
            return 
        if normalized.lower() == original.lower():
            self.norm_logger.num_case_i_matches += 1
            return
        name_list = self.taxonomy.get_names_for_id(ott_id)
        synonym_list = [name for name in name_list if name.lower() != normalized.lower()]
        suffix = '\t|\t%s\t|\t%s\t|\t%s\t|\t%s\n' % (normalized, original, self.timestamp, self.curr_username)
        for name in synonym_list:
            matched_syn= False
            if name == original:
                self.norm_logger.num_exact_synonym_matches += 1
                matched_syn = True
            elif name.lower() == original.lower():
                self.norm_logger.num_case_i_synonym_matches += 1
                matched_syn = True
            if matched_syn:
                s = 'SYNONYM\t|\t%s%s' % (name, suffix)
                self.auto_diagnosed.write(s)
                self.display_analysis_of_nontrivial_mapping(s)
                return
        self.norm_logger.num_mismatches += 1
        full_name_list = [normalized] + synonym_list
        for name in full_name_list:
            ld = levenshtein(name, original)
            if ld < 3 and abs(len(name) - len(original)) < 3:
                if name == normalized:
                    self.norm_logger.num_suspected_typos += 1
                    s = 'TYPO\t|\tlevenshtein=%d%s' % (ld, suffix)
                else:
                    self.norm_logger.num_suspected_syn_typos += 1
                    s = 'TYPOSYNONYMS\t|\t%s levenshtein=%d%s' % (name, ld, suffix)
                self.auto_diagnosed.write(s)
                self.display_analysis_of_nontrivial_mapping(s)
                return

        from_prev_run = self.previously_diagnosed.get(original, ['UNCLASSIFIED', ''])
        fpr_code = from_prev_run[0]
        fpr_details = from_prev_run[0]
        diag_code, diag_details = self.query_user(original,
                                                  normalized,
                                                  synonym_list,
                                                  from_prev_run,
                                                  self.norm_logger)
        if diag_code != 'UNCLASSIFIED' and (
            diag_code != fpr_code or diag_details != fpr_details):
            s = '%s\t|\t%s%s' % (diag_code, diag_details, suffix)
            self.manually_diagnosed.write(s)
            self.display_analysis_of_nontrivial_mapping(s)

    def report(self):
         self.norm_logger.report()

    def query_user(self, original, normalized, synonym_list, from_prev_run, norm_logger):
        c, d = self._query_user_impl(original, normalized, synonym_list, from_prev_run)
        if c == 'MISSPELLED':
            norm_logger.num_misspelled += 1
        elif c == 'TYPO':
            norm_logger.num_suspected_typos += 1
        elif c == 'ABBREVIATION':
            norm_logger.num_abbrev += 1
        return c, d

    def diagnose_possible_abbrev(self, original, normalized):
        '''
        Returns None of a list of AbbrevGen objects that could be used to abbreviate 
        the string normalized as the abbreviation original (not an exhaustive list)
        '''
        NO_CROPPING_INDEX = 100000
        lo = original.lower()
        ln = normalized.lower()
        len_o = len(lo)
        norm_words = ln.split()
        if len(norm_words) == 1:
            len_n = len(ln)
            len_short = min(len_n, len_o)
            shared_pref_index = 0
            while shared_pref_index < len_short:
                if ln[shared_pref_index] == lo[shared_pref_index]:
                    shared_pref_index += 1
                else:
                    break
            if shared_pref_index == 0:
                return None
            if shared_pref_index == len_o - 1:
                shared_pref_index = NO_CROPPING_INDEX# uncropped
                suffix = ''
            else:
                suffix = original[shared_pref_index:]
            return [AbbrevGen([shared_pref_index], extra_suffix=suffix)]
        else:
            o_separator_list = [' ', '_', '-']
            lo_offset = 0
            crop_list = []
            abbrev_sep_list = []
            non_empty_sep_entered = False
            for word in norm_words:
                len_w = len(word)
                n_index = 0
                o_index = lo_offset
                if VERY_VERBOSE:
                    sys.stderr.write('Trying to match "%s" to "%s"\n' % (word, lo[o_index:]))
                while (n_index < len_w) and (o_index < len_o):
                    if word[n_index] == lo[o_index]:
                        n_index += 1
                        o_index += 1
                    else:
                        break
                if n_index == 0:
                    if crop_list:
                        suffix = original[o_index:]
                        if not non_empty_sep_entered:
                            abbrev_sep_list = None
                        return [AbbrevGen(crop_list,
                                          abbrev_sep_list=abbrev_sep_list,
                                          extra_suffix=suffix)]
                    return None
                if o_index < len_o:
                    next_o = original[o_index]
                    if next_o in o_separator_list:
                        crop_list.append(NO_CROPPING_INDEX)
                        abbrev_sep_list.append(next_o)
                        non_empty_sep_entered = True
                        o_index += 1
                    else:
                        crop_list.append(n_index)
                        abbrev_sep_list.append('')
                else:
                    crop_list.append(n_index)
                lo_offset = o_index
            if crop_list:
                suffix = original[o_index:]
                if not non_empty_sep_entered:
                    abbrev_sep_list = None
                return [AbbrevGen(crop_list,
                                  abbrev_sep_list=abbrev_sep_list,
                                  extra_suffix=suffix)]
            return None


    def _query_user_impl(self, original, normalized, synonym_list, from_prev_run):
        sys.stderr.write('"%s" was normalized to "%s" caused by:\n' % (original, normalized))
        def_choice = from_prev_run[0]
        def_details = from_prev_run[1]
        syn_lower = [i.lower() for i in synonym_list]
        # filter the list of previously used abbreviations...
        plausible_abbrev = []
        for nag in self.abbrev_collection:
            ag = nag[1]
            a = ag.generate_abbreviation(normalized).lower()
            if (a == original.lower()) or (a in syn_lower):
                plausible_abbrev.append(nag)
            #    sys.stderr.write('Previously used abbrev could work\n')
            #else:
            #    sys.stderr.write('Previously used abbrev would have generated "%s"\n' % a)
        ind_of_last_reused = len(plausible_abbrev)
        
        # If we don't have any possible abbreviation generators, try to create some...
        if not plausible_abbrev:
            new_possible_abbrev = []
            for n in [normalized] + synonym_list:
                x = self.diagnose_possible_abbrev(original, n)
                if x:
                    new_possible_abbrev.extend(x)
            to_add = []
            for pa in new_possible_abbrev:
                new_abbrev = True
                for a in plausible_abbrev:
                    if pa == a[1]:
                        new_abbrev = False
                        break
                if new_abbrev:
                    plausible_abbrev.append([0, pa])

        cause_list = [['TYPO', ''],
                      ['MISSPELLED', ''],
                     ]
        choice2abbrev = {}
        for n, pa in enumerate(plausible_abbrev):
            s = '%s # => "%s" (used %d time(s) previously)' % (pa[1], pa[1].generate_abbreviation(original), pa[0])
            is_new = bool(n >= ind_of_last_reused)
            choice2abbrev[len(cause_list)] = [pa, is_new]
            cause_list.append(['ABBREVIATION', s])
        cause_list.append(['UNCLASSIFIED', ''])

        def_index = None
        # if the default is useless, and we have an abbreviation that has been used, make
        #   that abbrev the default.
        if def_choice == 'UNCLASSIFIED' and (not def_details) and len(plausible_abbrev) > 0:
            k = choice2abbrev.keys()
            k.sort()
            def_index = k[0]

        empty_response_means = len(cause_list) - 1
        for i in range(len(cause_list)):
            c, d = cause_list[i]
            is_default = False
            if def_index is None:
                if c == def_choice and (c != 'ABBREVIATION' or d.startswith(def_details)):
                    is_default = True
            elif def_index == i:
                is_default = True
            if is_default:
                empty_response_means = i
                sys.stderr.write('%d * %s\t|\t%s\n' % (1 + i, c, d))
            else:
                sys.stderr.write('%d   %s\t|\t%s\n' % (1 + i, c, d))
        for i in range(10):
            if i > 0:
                sys.stderr.write('Invalid choice, try again...\n')
            resp = raw_input('your response ? ')
            n = -1
            if not resp:
                n = empty_response_means
                ind = 1 + n
            try:
                ind = resp.split()[0]
                n = int(ind) - 1
            except:
                pass
            if n >= 0 and  n < len(cause_list):
                c = cause_list[n][0]
                d = cause_list[n][1]
                gd = resp[(1 + n):].strip()
                if n in choice2abbrev:
                    abbrev, is_new = choice2abbrev[n]
                    abbrev[0] = abbrev[0] + 1
                    if is_new:
                        self.abbrev_collection.append(abbrev)
                    d = '#'.join(d.split('#')[:-1]).strip() #remove the comment added to the  str form
                if gd:
                    return c, gd
                return c, d
        raise RuntimeError('number of query attempts exceeded')

if __name__ == '__main__':
    import sys
    # ab = AbbrevGen([3, 3])
    # comment = """ 
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
    repr_filepath = 'ott_repr.py'
    output = sys.stdout
    taxonomy_filepath = sys.argv[1]
    synonyms_filepath = sys.argv[2]
    results_dir = sys.argv[3]
    if not os.path.exists(results_dir) or not os.path.isdir(results_dir):
        sys.exit('Expecting an existing directory for output, found "%s"' % results_dir)
    fn_fmt = sys.argv[4]
    file_counter_start= int(sys.argv[5])
    file_counter_last = int(sys.argv[6])
    if reading_repr:
        import cPickle
        ott = cPickle.load(open(repr_filepath, 'r'))
        sys.exit(0)
    elif emitting_repr:
        ott = OTT(open(taxonomy_filepath, 'rU'), open(synonyms_filepath, 'rU'))
        fo = open(repr_filepath, 'w')
        import cPickle
        cPickle.dump(obj=ott, file=fo, protocol=cPickle.HIGHEST_PROTOCOL)
        sys.exit(0)
    elif 'NO_OTT_VALIDATION' in os.environ:
        ott = OTT(None, None)
    else:
        ott = OTT(open(taxonomy_filepath, 'rU'), open(synonyms_filepath, 'rU'))

    assert file_counter_start <= file_counter_last
    for file_counter in range(file_counter_start, 1 + file_counter_last):
        fp = fn_fmt % file_counter
        if not os.path.exists(fp):
            sys.stderr.write('File "%s" not found. Skipping...\n' % fp)
            continue
        infile = codecs.open(fp, mode='r', encoding='utf-8')
        ##############
        # hacky system for creating output for diagnosis of mismatches
        out_dir = os.path.join(results_dir, 'out-%d' % file_counter)
        curr_study = MappingBlob(auto_diagnosed_filepath=os.path.join(out_dir, 'auto-diagnosed.txt'),
                                 manually_diagnosed_filepath=os.path.join(out_dir, 'manually-diagnosed.txt'),
                                 status_filepath=os.path.join(out_dir, 'status'),
                                 logfile_filepath=os.path.join(out_dir, 'log'),
                                 taxonomy=ott)
        for line_num, line in enumerate(infile):
            ls = line.split('\t|\t')
            if len(ls) < 2:
                if line.strip():
                    sys.exit('Expecting at least two columns. Found:\n%sin file "%s" at line %d' % (line, fn, line_num))
                continue
            normalized, original = ls[0], ls[1]
            try:
                ott_id = ls[3]
            except:
                ott_id = ''
            curr_study.analyze_mapping(original, normalized, ott_id)
        curr_study.report()

    # """ # end of comment