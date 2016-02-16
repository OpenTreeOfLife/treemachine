# This module is imported by two test_about.py files

def check_about(parameters):

    import sys, os
    from opentreetesting import test_http_json_method, config
    DOMAIN = config('host', 'apihost')
    SUBMIT_URI = DOMAIN + '/v3/tree_of_life/about'
    test, result, _ = test_http_json_method(SUBMIT_URI, "POST",
                                              data=parameters,
                                              expected_status=200,
                                              return_bool_data=True)
    if not test:
        sys.exit(1)


    """ Doc says:
    "synth_id" : "opentree4.1",
      "root_node_id" : "ott93302",    # in v2 this was an integer
      "num_source_trees" : 484,
      "date_completed" : "2016-02-09 18:14:43",
      "taxonomy_version" : "2.9draft12",
      "num_tips" : 2424255,
      "num_source_studies" : 478,
      "filtered_flags" : [ "major_rank_conflict", "major_rank_conflict_inherited", "environmental", "unclassified_inherited", "unclassified", "viral", "barren", "not_otu", "incertae_sedis", "incertae_sedis_inherited", "extinct_inherited", "extinct", "hidden", "unplaced", "unplaced_inherited", "was_container", "inconsistent", "hybrid", "merged" ],

      "root_ott_id" : 93302
      "root_taxon_name" : "cellular organisms",

      maybe: "root_taxon" : { ... }

    """

    code = [0]
    def lose(): code[0] = 1

    def check_parameter_type(name, typo):
        value = result.get(name)
        if not isinstance(value, typo):
            if value == None:
                sys.stderr.write('{} missing\n'.format(name))
                print result.keys()
            else:
                sys.stderr.write('{} is {} which is not a {}\n'.format(name, value, typo))
                lose()
            return None
        return value

    check_parameter_type(u'root_node_id', unicode)

    num_source_trees = check_parameter_type(u'num_source_trees', int)
    if num_source_trees != None and num_source_trees < 2:
        sys.stderr.write('not very many source trees: {}\n'.format(num_source_trees))
        lose()

    check_parameter_type(u'date_completed', unicode)

    num_tips = check_parameter_type(u'num_tips', int)
    if num_tips != None and num_tips < 3:
        sys.stderr.write('not very many tips: {}\n'.format(num_tips))
        lose()

    check_parameter_type(u'taxonomy_version', unicode)

    num_source_trees = check_parameter_type(u'num_source_trees', int)
    if num_source_trees != None and num_source_trees < 2:
        sys.stderr.write('not very trees: {}\n'.format(num_source_trees))
        lose()

    num_source_studies = check_parameter_type(u'num_source_studies', int)
    if num_source_studies != None and num_source_studies < 2:
        sys.stderr.write('not very studies: {}\n'.format(num_source_studies))
        lose()

    check_parameter_type(u'filtered_flags', list)

    if u'root_ott_id' in result:
        check_parameter_type(u'root_ott_id', int)
        check_parameter_type(u'root_taxon_name', unicode)

    source_list = parameters.get(u'source_list')
    if source_list == None or not source_list:
        if u'sources' in result:
            sys.stderr.write('source_list false but there are sources in result: {}\n'.format(source_list))
            lose()
    else:
        sources = check_parameter_type(u'sources', list)
        metas = check_parameter_type(u'source_id_map', list)
        if sources != None:
            for source in sources:
                if not isinstance(source, str):
                    sys.stderr.write('ill-formed source: {} should be a string\n'.format(source))
                    lose()
                    break
            for meta in metas:
                if (isinstance(meta, dict) and
                    u'study_id' in meta and
                    u'tree_id' in meta):
                    True
                else:
                    sys.stderr.write('ill-formed meta: {} should be {}"study_id": ..., "tree_id": ...{}\n'.format(source, '{', '}'))
                    lose()
                    break
    sys.exit(code[0])
