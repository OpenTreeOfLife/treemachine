#!/usr/bin/env/python
'''
Largely based on a very helpful email from Scott McGinnis (june 21, 2012)
http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=taxonomy&term=all%5Bsb%5D&datetype=%20EDAT&reldate=30&retmax=10000&usehistory=y
http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?
db=taxonomy&
webenv=NCID_1_73988992_130.14.22.28_9001_1340281933_346641182&
query_key=1&retstart=1&retmax=500<http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=taxonomy&webenv=NCID_1_73988992_130.14.22.28_9001_1340281933_346641182&query_key=1&retstart=1&retmax=7000>&retmode=xml


DO NOT run this frequently. NCBI does not like to be hammered by scripts!

'''
import sys, time
try:
    import requests
except:
    sys.exit('You must install the "requests" package by running\n  pip install requests\n\npip can be obtained from http://pypi.python.org/pypi/pip if you do not have it.')

from xml.etree import ElementTree
from cStringIO import StringIO


# Set this to the # of days since the last query
frequency_in_days = 1

################################################################################
# Initial query to get WebEnv, QueryKey and list of new ID's
#
init_params = {
    'db' : 'taxonomy',
    'term' : 'all[sb]',
    'datetype' : 'EDAT',
    'reldate' : 1 + frequency_in_days, # add one to make sure that variable length lags don't cause us to miss an entry,
    'retmax' : 10000,
    'usehistory' : 'y',
}

DOMAIN = 'http://eutils.ncbi.nlm.nih.gov'
QUERY_PATH = 'entrez/eutils/esearch.fcgi'
QUERY_URI = DOMAIN + '/' + QUERY_PATH

resp = requests.get(QUERY_URI,
                    params=init_params,
                    allow_redirects=True)
resp.raise_for_status() # will raise an exception on failure
query_result = resp.text
#print 'resp.url =', resp.url    
#print '\n\n\nresp.tex =\n'
#print query_result

################################################################################
# Use ElementTree to parse the results out of the XML return
#
query_result_stream = StringIO(query_result)
query_parse_tree = ElementTree.parse(query_result_stream)
web_env_list = query_parse_tree.findall('WebEnv')
assert(len(web_env_list) == 1)
web_env = web_env_list[0].text
query_key_list = query_parse_tree.findall('QueryKey')
assert(len(query_key_list) == 1)
query_key = query_key_list[0].text
print web_env, query_key
id_list_element_list = query_parse_tree.findall('IdList') # Id elements occur in an IdList element
assert(len(id_list_element_list) == 1)
id_list_element = id_list_element_list[0]
id_element_list = [i.text for i in id_list_element.findall('Id')]

################################################################################
# Code below retrieves info on each new ID
#
class NewTaxonFromSummary(object):
    '''Simple conversion of the data in a retrieved DocSum element into a python
     object with python attributes that correspond to the "Item Name" values.
    '''
    def __init__(self, et_element):
        il = et_element.findall('Id')
        assert(len(il) == 1)
        self.taxon_id = il[0].text
        for item_el in et_element.findall('Item'):
            n = item_el.attrib.get('Name')
            self.__dict__[n] = item_el.text


num_new_entities = len(id_element_list)
sys.stderr.write("%d new taxa\n" %   num_new_entities)

# we want to get the records 500 at a time.
max_num_returns = 500

# the web_env and query_key occur in a URL that is part of the retmax parameter
#   we'll call this the 'context'
#
RETRIEVE_PATH = 'entrez/eutils/esummary.fcgi'
RETRIEVE_URI = DOMAIN + '/' + RETRIEVE_PATH
context = '<' + RETRIEVE_URI + '?db=taxonomy&webenv=' + web_env + '&query_key=' + query_key + '&retstart=1&retmax=10000>'

ret_start = 1
new_taxa_list = []       
while True:
    ################################################################################
    # Compose the query parameters for the next max_num_returns records

    retrieve_params = { 'db' : 'taxonomy',
                        'webenv' : web_env,
                        'query_key' : query_key,
                        'retstart' : ret_start,
                        'retmax' : str(ret_start + max_num_returns - 1) + context,
                        'retmode' : 'xml'
                        }
    resp = requests.get(RETRIEVE_URI,
                        params=retrieve_params,
                        allow_redirects=True)
    resp.raise_for_status() # will raise an exception on failure
    # xml coming back claimes to be utf-8, 
    # but element tree disagrees and gives 'xml.etree.ElementTree.ParseError: encoding specified in XML declaration is incorrect: line 1, column 30'
    # if we do not encode it as utf-8
    #
    retrieve_result_non_utf8 = unicode(resp.content) 
    retrieve_result = retrieve_result_non_utf8.encode('utf-8')
    #print resp.url
    #print "RESULT"
    #print retrieve_result
    #print "ENDRESULT"
    
    ################################################################################
    # Parse each taxon into a NewTaxonFromSummary object and add it to 
    # new_taxa_list
    
    retrieve_stream = StringIO(retrieve_result)
    retrieve_parse_tree = ElementTree.parse(retrieve_stream)
    for ret_result in retrieve_parse_tree.findall('DocSum'): # each taxon's summary is in a DocSum element
        new_taxon = NewTaxonFromSummary(ret_result)
        new_taxa_list.append(new_taxon)

    ret_start += max_num_returns
    if ret_start <= num_new_entities:
        time.sleep(3) # this is here to avoid hammering NCBI.
    else:
        break

################################################################################
# This is just an example of reformatting the returned taxa
for new_taxon in new_taxa_list:
    print "taxon_id =", new_taxon.taxon_id, " ; Rank =", new_taxon.Rank, (' ; ScientificName =\"%s\"' % new_taxon.ScientificName)
