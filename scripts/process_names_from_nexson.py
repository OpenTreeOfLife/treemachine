#!/usr/bin/env python
import json

def _get_ottol_id_from_meta_dict(m):
    if m['@property'] == 'ot:ottolid':
        return m.get('$')
    return None

def _get_original_name_from_meta_dict(m):
    if m['@property'] == 'ot:originalLabel':
        return m.get('$')
    return None

def _get_type_of_meta_from_list_or_dict(m, fn):
    if isinstance(m, dict):
        return fn(m)
    assert isinstance(m, list)
    for m_element in m:
        i = fn(m_element)
        if i is not None:
            return i
    return None

def get_ottol_id_from_meta(m):
    """Returns the OTT id from a meta element or a list of meta elements or None.
    """
    return _get_type_of_meta_from_list_or_dict(m, _get_ottol_id_from_meta_dict)

def get_original_name_from_meta(m):
    """Returns the OTT id from a meta element or a list of meta elements or None.
    """
    return _get_type_of_meta_from_list_or_dict(m, _get_original_name_from_meta_dict)

if __name__ == '__main__':
    import sys
    for fn in sys.argv[1:]:
        try:
            fo = open(fn, 'rU')
        except:
            sys.exit('File "%s" could not be opened' % fn)
        obj = json.load(fo)
        otu_list = obj['nexml']['otus']['otu']
        for otu in otu_list:
            m = otu['meta']
            ottol_id = get_ottol_id_from_meta(m)
            original_label = get_original_name_from_meta(m)
            normalized_label = otu['@label']
            print '%s\t|\t%s\t|\t%s' % (normalized_label, original_label, ottol_id)