#!/usr/bin/env/python
'''
Largely based on a very helpful email from Scott McGinnis (june 21, 2012)
http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=taxonomy&term=all%5Bsb%5D&datetype=%20EDAT&reldate=30&retmax=10000&usehistory=y
http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?
db=taxonomy&
webenv=NCID_1_73988992_130.14.22.28_9001_1340281933_346641182&
query_key=1&retstart=1&retmax=500<http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=taxonomy&webenv=NCID_1_73988992_130.14.22.28_9001_1340281933_346641182&query_key=1&retstart=1&retmax=7000>&retmode=xml

'''
import sys, time
try:
    import requests
except:
    sys.exit('You must install the "requests" package by running\n  pip install requests\n\npip can be obtained from http://pypi.python.org/pypi/pip if you do not have it.')

DEBUGGING = True

frequency_in_days = 1
init_params = {
    'db' : 'taxonomy',
    'term' : 'all[sb]',
    'datetype' : 'EDAT',
    'reldate' : 1 + frequency_in_days, # add one to make sure that variable length lags don't cause us to miss an entry,
    'retmax' : 10000,
    'usehistory' : 'y',
}
# DON'T run this frequently. NCBI does not like to be hammered by scripts!
DOMAIN = 'http://eutils.ncbi.nlm.nih.gov'
QUERY_PATH = 'entrez/eutils/esearch.fcgi'
QUERY_URI = DOMAIN + '/' + QUERY_PATH
RETRIEVE_PATH = 'entrez/eutils/esummary.fcgi'
RETRIEVE_URI = DOMAIN + '/' + RETRIEVE_PATH
if not DEBUGGING:
    resp = requests.get(QUERY_URI,
                        params=init_params,
                        allow_redirects=True)
    resp.raise_for_status()
    query_result = resp.text
    print 'resp.url =', resp.url    
    print '\n\n\nresp.tex =\n'
    print query_result
else:
    query_result = u'<?xml version="1.0" ?>\n<!DOCTYPE eSearchResult PUBLIC "-//NLM//DTD eSearchResult, 11 May 2002//EN" "http://www.ncbi.nlm.nih.gov/entrez/query/DTD/eSearch_020511.dtd">\n<eSearchResult>\n<Count>648</Count><RetMax>648</RetMax><RetStart>0</RetStart><QueryKey>1</QueryKey><WebEnv>NCID_1_36714255_130.14.18.97_5555_1340940863_1443669168</WebEnv>\n<IdList>\n\t<Id>1200656</Id>\n\t<Id>1200655</Id>\n\t<Id>1200511</Id>\n\t<Id>1200510</Id>\n\t<Id>1198096</Id>\n\t<Id>1203035</Id>\n\t<Id>1202775</Id>\n\t<Id>1202774</Id>\n\t<Id>1201210</Id>\n\t<Id>1201209</Id>\n\t<Id>1201208</Id>\n\t<Id>1201207</Id>\n\t<Id>1201206</Id>\n\t<Id>1201205</Id>\n\t<Id>1201204</Id>\n\t<Id>1201203</Id>\n\t<Id>1201202</Id>\n\t<Id>1201201</Id>\n\t<Id>1201200</Id>\n\t<Id>1201199</Id>\n\t<Id>1201198</Id>\n\t<Id>1201197</Id>\n\t<Id>1201196</Id>\n\t<Id>1201195</Id>\n\t<Id>1201194</Id>\n\t<Id>1201193</Id>\n\t<Id>1201192</Id>\n\t<Id>1201191</Id>\n\t<Id>1201190</Id>\n\t<Id>1201189</Id>\n\t<Id>1201188</Id>\n\t<Id>1201187</Id>\n\t<Id>1201186</Id>\n\t<Id>1201185</Id>\n\t<Id>1201184</Id>\n\t<Id>1201183</Id>\n\t<Id>1201182</Id>\n\t<Id>1201181</Id>\n\t<Id>1201180</Id>\n\t<Id>1201179</Id>\n\t<Id>1201178</Id>\n\t<Id>1201177</Id>\n\t<Id>1201176</Id>\n\t<Id>1201175</Id>\n\t<Id>1201174</Id>\n\t<Id>1201143</Id>\n\t<Id>1201086</Id>\n\t<Id>1201085</Id>\n\t<Id>1201084</Id>\n\t<Id>1201082</Id>\n\t<Id>1201081</Id>\n\t<Id>1201080</Id>\n\t<Id>1201079</Id>\n\t<Id>1200294</Id>\n\t<Id>1200293</Id>\n\t<Id>1200292</Id>\n\t<Id>1200291</Id>\n\t<Id>1200290</Id>\n\t<Id>1200289</Id>\n\t<Id>1200288</Id>\n\t<Id>1200287</Id>\n\t<Id>1200286</Id>\n\t<Id>1200285</Id>\n\t<Id>1203599</Id>\n\t<Id>1202450</Id>\n\t<Id>1201225</Id>\n\t<Id>1201224</Id>\n\t<Id>1201223</Id>\n\t<Id>1201222</Id>\n\t<Id>1201038</Id>\n\t<Id>1200394</Id>\n\t<Id>1198325</Id>\n\t<Id>1198324</Id>\n\t<Id>1198322</Id>\n\t<Id>1194106</Id>\n\t<Id>1201232</Id>\n\t<Id>1201231</Id>\n\t<Id>1201043</Id>\n\t<Id>1200393</Id>\n\t<Id>1194146</Id>\n\t<Id>1194108</Id>\n\t<Id>1194107</Id>\n\t<Id>1194105</Id>\n\t<Id>1194104</Id>\n\t<Id>1204360</Id>\n\t<Id>1201230</Id>\n\t<Id>1201229</Id>\n\t<Id>1201228</Id>\n\t<Id>1198293</Id>\n\t<Id>1198292</Id>\n\t<Id>1198132</Id>\n\t<Id>1198131</Id>\n\t<Id>1198130</Id>\n\t<Id>1198129</Id>\n\t<Id>1198128</Id>\n\t<Id>1198127</Id>\n\t<Id>1198126</Id>\n\t<Id>1198125</Id>\n\t<Id>1198124</Id>\n\t<Id>1198123</Id>\n\t<Id>1187064</Id>\n\t<Id>1203069</Id>\n\t<Id>1203033</Id>\n\t<Id>1202771</Id>\n\t<Id>1202770</Id>\n\t<Id>1202457</Id>\n\t<Id>1202456</Id>\n\t<Id>1201233</Id>\n\t<Id>1201221</Id>\n\t<Id>1201220</Id>\n\t<Id>1201219</Id>\n\t<Id>1201218</Id>\n\t<Id>1201010</Id>\n\t<Id>1194166</Id>\n\t<Id>1202561</Id>\n\t<Id>1200971</Id>\n\t<Id>1200970</Id>\n\t<Id>1202959</Id>\n\t<Id>1202451</Id>\n\t<Id>1201036</Id>\n\t<Id>1201009</Id>\n\t<Id>1201008</Id>\n\t<Id>1201007</Id>\n\t<Id>1201006</Id>\n\t<Id>1201005</Id>\n\t<Id>1201004</Id>\n\t<Id>1201000</Id>\n\t<Id>1200999</Id>\n\t<Id>1200998</Id>\n\t<Id>1200997</Id>\n\t<Id>1188263</Id>\n\t<Id>1187063</Id>\n\t<Id>1203471</Id>\n\t<Id>1202769</Id>\n\t<Id>1202454</Id>\n\t<Id>1202448</Id>\n\t<Id>1201295</Id>\n\t<Id>1201290</Id>\n\t<Id>1201289</Id>\n\t<Id>1201288</Id>\n\t<Id>1201287</Id>\n\t<Id>1201227</Id>\n\t<Id>1201226</Id>\n\t<Id>1201003</Id>\n\t<Id>1201002</Id>\n\t<Id>1201001</Id>\n\t<Id>1200996</Id>\n\t<Id>1200681</Id>\n\t<Id>1202131</Id>\n\t<Id>1198323</Id>\n\t<Id>1194416</Id>\n\t<Id>1194167</Id>\n\t<Id>1182311</Id>\n\t<Id>1201234</Id>\n\t<Id>1194192</Id>\n\t<Id>1194191</Id>\n\t<Id>1194190</Id>\n\t<Id>1185406</Id>\n\t<Id>1185405</Id>\n\t<Id>1185404</Id>\n\t<Id>1200790</Id>\n\t<Id>1200789</Id>\n\t<Id>1176757</Id>\n\t<Id>1201154</Id>\n\t<Id>1201099</Id>\n\t<Id>1201098</Id>\n\t<Id>1201097</Id>\n\t<Id>1201096</Id>\n\t<Id>1201095</Id>\n\t<Id>1201094</Id>\n\t<Id>1201092</Id>\n\t<Id>1201050</Id>\n\t<Id>1201029</Id>\n\t<Id>1201028</Id>\n\t<Id>1201027</Id>\n\t<Id>1201026</Id>\n\t<Id>1201025</Id>\n\t<Id>1201020</Id>\n\t<Id>1200779</Id>\n\t<Id>1200778</Id>\n\t<Id>1200777</Id>\n\t<Id>1200776</Id>\n\t<Id>1200774</Id>\n\t<Id>1200772</Id>\n\t<Id>1199423</Id>\n\t<Id>1199422</Id>\n\t<Id>1199419</Id>\n\t<Id>1199135</Id>\n\t<Id>1199092</Id>\n\t<Id>1198974</Id>\n\t<Id>1198973</Id>\n\t<Id>1198972</Id>\n\t<Id>1198970</Id>\n\t<Id>1198969</Id>\n\t<Id>1198968</Id>\n\t<Id>1198967</Id>\n\t<Id>1198966</Id>\n\t<Id>1198965</Id>\n\t<Id>1198964</Id>\n\t<Id>1198963</Id>\n\t<Id>1198962</Id>\n\t<Id>1198961</Id>\n\t<Id>1198960</Id>\n\t<Id>1198959</Id>\n\t<Id>1198958</Id>\n\t<Id>1198957</Id>\n\t<Id>1198956</Id>\n\t<Id>1198955</Id>\n\t<Id>1198954</Id>\n\t<Id>1198953</Id>\n\t<Id>1198952</Id>\n\t<Id>1198951</Id>\n\t<Id>1198950</Id>\n\t<Id>1198949</Id>\n\t<Id>1198948</Id>\n\t<Id>1198947</Id>\n\t<Id>1198946</Id>\n\t<Id>1198945</Id>\n\t<Id>1198944</Id>\n\t<Id>1198943</Id>\n\t<Id>1198942</Id>\n\t<Id>1198941</Id>\n\t<Id>1198940</Id>\n\t<Id>1198939</Id>\n\t<Id>1198938</Id>\n\t<Id>1194727</Id>\n\t<Id>1194726</Id>\n\t<Id>1184221</Id>\n\t<Id>1182205</Id>\n\t<Id>1182204</Id>\n\t<Id>1182203</Id>\n\t<Id>1182194</Id>\n\t<Id>1182163</Id>\n\t<Id>1182162</Id>\n\t<Id>1181315</Id>\n\t<Id>1181312</Id>\n\t<Id>1202717</Id>\n\t<Id>1202531</Id>\n\t<Id>1201153</Id>\n\t<Id>1201103</Id>\n\t<Id>1201102</Id>\n\t<Id>1201101</Id>\n\t<Id>1201100</Id>\n\t<Id>1201057</Id>\n\t<Id>1201056</Id>\n\t<Id>1201055</Id>\n\t<Id>1201054</Id>\n\t<Id>1201053</Id>\n\t<Id>1201052</Id>\n\t<Id>1201051</Id>\n\t<Id>1200946</Id>\n\t<Id>1200869</Id>\n\t<Id>1200868</Id>\n\t<Id>1200867</Id>\n\t<Id>1200866</Id>\n\t<Id>1200865</Id>\n\t<Id>1200362</Id>\n\t<Id>1199096</Id>\n\t<Id>1199095</Id>\n\t<Id>1199094</Id>\n\t<Id>1199093</Id>\n\t<Id>1188806</Id>\n\t<Id>1188805</Id>\n\t<Id>1188804</Id>\n\t<Id>1188803</Id>\n\t<Id>1188802</Id>\n\t<Id>1188801</Id>\n\t<Id>1188800</Id>\n\t<Id>1188799</Id>\n\t<Id>1188798</Id>\n\t<Id>1202390</Id>\n\t<Id>1202389</Id>\n\t<Id>1202388</Id>\n\t<Id>1202387</Id>\n\t<Id>1202386</Id>\n\t<Id>1202385</Id>\n\t<Id>1202384</Id>\n\t<Id>1202383</Id>\n\t<Id>1202382</Id>\n\t<Id>1202381</Id>\n\t<Id>1202380</Id>\n\t<Id>1202379</Id>\n\t<Id>1202378</Id>\n\t<Id>1202377</Id>\n\t<Id>1202376</Id>\n\t<Id>1202375</Id>\n\t<Id>1202374</Id>\n\t<Id>1202373</Id>\n\t<Id>1202372</Id>\n\t<Id>1202371</Id>\n\t<Id>1202370</Id>\n\t<Id>1202369</Id>\n\t<Id>1202368</Id>\n\t<Id>1202367</Id>\n\t<Id>1202366</Id>\n\t<Id>1202365</Id>\n\t<Id>1202364</Id>\n\t<Id>1202363</Id>\n\t<Id>1202362</Id>\n\t<Id>1202361</Id>\n\t<Id>1202360</Id>\n\t<Id>1202359</Id>\n\t<Id>1202358</Id>\n\t<Id>1202357</Id>\n\t<Id>1202356</Id>\n\t<Id>1202355</Id>\n\t<Id>1202354</Id>\n\t<Id>1202353</Id>\n\t<Id>1202352</Id>\n\t<Id>1202351</Id>\n\t<Id>1202350</Id>\n\t<Id>1202349</Id>\n\t<Id>1202348</Id>\n\t<Id>1202347</Id>\n\t<Id>1202346</Id>\n\t<Id>1202345</Id>\n\t<Id>1202344</Id>\n\t<Id>1202343</Id>\n\t<Id>1202342</Id>\n\t<Id>1202341</Id>\n\t<Id>1202340</Id>\n\t<Id>1202339</Id>\n\t<Id>1202338</Id>\n\t<Id>1202337</Id>\n\t<Id>1202336</Id>\n\t<Id>1202335</Id>\n\t<Id>1201216</Id>\n\t<Id>1199181</Id>\n\t<Id>1199180</Id>\n\t<Id>1199179</Id>\n\t<Id>1199178</Id>\n\t<Id>1199177</Id>\n\t<Id>1199176</Id>\n\t<Id>1199175</Id>\n\t<Id>1199174</Id>\n\t<Id>1199173</Id>\n\t<Id>1199172</Id>\n\t<Id>1199171</Id>\n\t<Id>1199170</Id>\n\t<Id>1199169</Id>\n\t<Id>1199167</Id>\n\t<Id>1199166</Id>\n\t<Id>1199165</Id>\n\t<Id>1199164</Id>\n\t<Id>1199163</Id>\n\t<Id>1199161</Id>\n\t<Id>1199160</Id>\n\t<Id>1199159</Id>\n\t<Id>1198121</Id>\n\t<Id>1198120</Id>\n\t<Id>1195726</Id>\n\t<Id>1195725</Id>\n\t<Id>1192170</Id>\n\t<Id>1202906</Id>\n\t<Id>1201093</Id>\n\t<Id>1200944</Id>\n\t<Id>1203276</Id>\n\t<Id>1203275</Id>\n\t<Id>1203274</Id>\n\t<Id>1203273</Id>\n\t<Id>1202999</Id>\n\t<Id>1202647</Id>\n\t<Id>1202646</Id>\n\t<Id>1202645</Id>\n\t<Id>1202644</Id>\n\t<Id>1202643</Id>\n\t<Id>1202642</Id>\n\t<Id>1202641</Id>\n\t<Id>1202640</Id>\n\t<Id>1202639</Id>\n\t<Id>1202638</Id>\n\t<Id>1202637</Id>\n\t<Id>1202636</Id>\n\t<Id>1202635</Id>\n\t<Id>1202634</Id>\n\t<Id>1202633</Id>\n\t<Id>1202632</Id>\n\t<Id>1202631</Id>\n\t<Id>1202630</Id>\n\t<Id>1202629</Id>\n\t<Id>1202628</Id>\n\t<Id>1202627</Id>\n\t<Id>1202626</Id>\n\t<Id>1202625</Id>\n\t<Id>1202624</Id>\n\t<Id>1202623</Id>\n\t<Id>1202622</Id>\n\t<Id>1202621</Id>\n\t<Id>1202620</Id>\n\t<Id>1202619</Id>\n\t<Id>1202618</Id>\n\t<Id>1202617</Id>\n\t<Id>1202616</Id>\n\t<Id>1202615</Id>\n\t<Id>1202614</Id>\n\t<Id>1202613</Id>\n\t<Id>1202612</Id>\n\t<Id>1202611</Id>\n\t<Id>1202610</Id>\n\t<Id>1202609</Id>\n\t<Id>1202608</Id>\n\t<Id>1202607</Id>\n\t<Id>1202606</Id>\n\t<Id>1202605</Id>\n\t<Id>1202604</Id>\n\t<Id>1202603</Id>\n\t<Id>1202602</Id>\n\t<Id>1202601</Id>\n\t<Id>1202600</Id>\n\t<Id>1202599</Id>\n\t<Id>1202598</Id>\n\t<Id>1202597</Id>\n\t<Id>1202596</Id>\n\t<Id>1202595</Id>\n\t<Id>1202594</Id>\n\t<Id>1202593</Id>\n\t<Id>1202592</Id>\n\t<Id>1202591</Id>\n\t<Id>1202590</Id>\n\t<Id>1202589</Id>\n\t<Id>1202588</Id>\n\t<Id>1202587</Id>\n\t<Id>1202586</Id>\n\t<Id>1202585</Id>\n\t<Id>1202584</Id>\n\t<Id>1202583</Id>\n\t<Id>1202582</Id>\n\t<Id>1202581</Id>\n\t<Id>1202580</Id>\n\t<Id>1202579</Id>\n\t<Id>1202578</Id>\n\t<Id>1202577</Id>\n\t<Id>1202576</Id>\n\t<Id>1202575</Id>\n\t<Id>1202574</Id>\n\t<Id>1202573</Id>\n\t<Id>1202572</Id>\n\t<Id>1202571</Id>\n\t<Id>1202570</Id>\n\t<Id>1202569</Id>\n\t<Id>1202568</Id>\n\t<Id>1202567</Id>\n\t<Id>1202566</Id>\n\t<Id>1202565</Id>\n\t<Id>1202564</Id>\n\t<Id>1202563</Id>\n\t<Id>1202562</Id>\n\t<Id>1204387</Id>\n\t<Id>1203005</Id>\n\t<Id>1200843</Id>\n\t<Id>1203020</Id>\n\t<Id>1202162</Id>\n\t<Id>1202560</Id>\n\t<Id>1202559</Id>\n\t<Id>1202558</Id>\n\t<Id>1202557</Id>\n\t<Id>1201395</Id>\n\t<Id>1201300</Id>\n\t<Id>1201299</Id>\n\t<Id>1201169</Id>\n\t<Id>1201168</Id>\n\t<Id>1200804</Id>\n\t<Id>1202961</Id>\n\t<Id>1202960</Id>\n\t<Id>1202951</Id>\n\t<Id>1202950</Id>\n\t<Id>1202949</Id>\n\t<Id>1202947</Id>\n\t<Id>1202946</Id>\n\t<Id>1202942</Id>\n\t<Id>1204325</Id>\n\t<Id>1203398</Id>\n\t<Id>1202662</Id>\n\t<Id>1202661</Id>\n\t<Id>1202660</Id>\n\t<Id>1202659</Id>\n\t<Id>1202658</Id>\n\t<Id>1202657</Id>\n\t<Id>1202656</Id>\n\t<Id>1202655</Id>\n\t<Id>1202654</Id>\n\t<Id>1202653</Id>\n\t<Id>1202652</Id>\n\t<Id>1202651</Id>\n\t<Id>1202650</Id>\n\t<Id>1202649</Id>\n\t<Id>1202648</Id>\n\t<Id>1201019</Id>\n\t<Id>1200500</Id>\n\t<Id>1183161</Id>\n\t<Id>1183160</Id>\n\t<Id>1183159</Id>\n\t<Id>1183158</Id>\n\t<Id>1183157</Id>\n\t<Id>1183156</Id>\n\t<Id>1201387</Id>\n\t<Id>1201386</Id>\n\t<Id>1201385</Id>\n\t<Id>1201384</Id>\n\t<Id>1201383</Id>\n\t<Id>1201382</Id>\n\t<Id>1201381</Id>\n\t<Id>1201380</Id>\n\t<Id>1201378</Id>\n\t<Id>1200982</Id>\n\t<Id>1201391</Id>\n\t<Id>1191330</Id>\n\t<Id>1200668</Id>\n\t<Id>1197259</Id>\n\t<Id>1197258</Id>\n\t<Id>1197257</Id>\n\t<Id>1197256</Id>\n\t<Id>1197252</Id>\n\t<Id>1197251</Id>\n\t<Id>1197250</Id>\n\t<Id>1197249</Id>\n\t<Id>1197248</Id>\n\t<Id>1197247</Id>\n\t<Id>1196214</Id>\n\t<Id>1196213</Id>\n\t<Id>1204477</Id>\n\t<Id>1204416</Id>\n\t<Id>1203520</Id>\n\t<Id>1203515</Id>\n\t<Id>1203514</Id>\n\t<Id>1203513</Id>\n\t<Id>1203512</Id>\n\t<Id>1203511</Id>\n\t<Id>1203500</Id>\n\t<Id>1199138</Id>\n\t<Id>1196848</Id>\n\t<Id>1196847</Id>\n\t<Id>1196846</Id>\n\t<Id>1196515</Id>\n\t<Id>1196514</Id>\n\t<Id>1196513</Id>\n\t<Id>1196512</Id>\n\t<Id>1196511</Id>\n\t<Id>1196510</Id>\n\t<Id>1196509</Id>\n\t<Id>1196508</Id>\n\t<Id>1196507</Id>\n\t<Id>1196506</Id>\n\t<Id>1196505</Id>\n\t<Id>1196504</Id>\n\t<Id>1196503</Id>\n\t<Id>1196501</Id>\n\t<Id>1196500</Id>\n\t<Id>1196499</Id>\n\t<Id>1196498</Id>\n\t<Id>1196497</Id>\n\t<Id>1196496</Id>\n\t<Id>1196495</Id>\n\t<Id>1196494</Id>\n\t<Id>1196493</Id>\n\t<Id>1196492</Id>\n\t<Id>1196491</Id>\n\t<Id>1196490</Id>\n\t<Id>1196489</Id>\n\t<Id>1196487</Id>\n\t<Id>1196486</Id>\n\t<Id>1196485</Id>\n\t<Id>1196484</Id>\n\t<Id>1196483</Id>\n\t<Id>1196482</Id>\n\t<Id>1196481</Id>\n\t<Id>1196480</Id>\n\t<Id>1196479</Id>\n\t<Id>1196478</Id>\n\t<Id>1196477</Id>\n\t<Id>1196476</Id>\n\t<Id>1196475</Id>\n\t<Id>1196474</Id>\n\t<Id>1196473</Id>\n\t<Id>1196472</Id>\n\t<Id>1196471</Id>\n\t<Id>1196470</Id>\n\t<Id>1196469</Id>\n\t<Id>1196468</Id>\n\t<Id>1196467</Id>\n\t<Id>1196466</Id>\n\t<Id>1196465</Id>\n\t<Id>1196464</Id>\n\t<Id>1196463</Id>\n\t<Id>1196462</Id>\n\t<Id>1196461</Id>\n\t<Id>1196460</Id>\n\t<Id>1196459</Id>\n\t<Id>1196458</Id>\n\t<Id>1196457</Id>\n\t<Id>1196211</Id>\n\t<Id>1196210</Id>\n\t<Id>1196209</Id>\n\t<Id>1196208</Id>\n\t<Id>1196207</Id>\n\t<Id>1196206</Id>\n\t<Id>1196205</Id>\n\t<Id>1196204</Id>\n\t<Id>1196203</Id>\n\t<Id>1196202</Id>\n\t<Id>1196201</Id>\n\t<Id>1196200</Id>\n\t<Id>1196199</Id>\n\t<Id>1196198</Id>\n\t<Id>1196197</Id>\n\t<Id>1196196</Id>\n\t<Id>1202732</Id>\n\t<Id>1204473</Id>\n\t<Id>1200507</Id>\n\t<Id>1200506</Id>\n\t<Id>1200505</Id>\n\t<Id>1199137</Id>\n\t<Id>1199136</Id>\n\t<Id>1199133</Id>\n\t<Id>1199132</Id>\n\t<Id>1199131</Id>\n\t<Id>1199130</Id>\n\t<Id>1199129</Id>\n\t<Id>1199127</Id>\n\t<Id>1199126</Id>\n\t<Id>1199125</Id>\n\t<Id>1199124</Id>\n\t<Id>1199123</Id>\n\t<Id>1199122</Id>\n\t<Id>1199121</Id>\n\t<Id>1199120</Id>\n\t<Id>1199119</Id>\n\t<Id>1199118</Id>\n\t<Id>1199117</Id>\n\t<Id>1199116</Id>\n\t<Id>1199115</Id>\n\t<Id>1199114</Id>\n\t<Id>1199113</Id>\n\t<Id>1201167</Id>\n\t<Id>1201166</Id>\n\t<Id>1201165</Id>\n\t<Id>1201164</Id>\n\t<Id>1201163</Id>\n\t<Id>1201162</Id>\n\t<Id>1200952</Id>\n\t<Id>1200951</Id>\n\t<Id>1200950</Id>\n\t<Id>1194221</Id>\n\t<Id>1194220</Id>\n\t<Id>1194219</Id>\n\t<Id>1194218</Id>\n\t<Id>1194217</Id>\n\t<Id>1194216</Id>\n</IdList>\n<TranslationSet/><TranslationStack>   <TermSet>    <Term>all[sb]</Term>    <Field>sb</Field>    <Count>910210</Count>    <Explode>Y</Explode>   </TermSet>   <TermSet>    <Term>2012/06/26[EDAT]</Term>    <Field>EDAT</Field>    <Count>0</Count>    <Explode>Y</Explode>   </TermSet>   <TermSet>    <Term>2012/06/28[EDAT]</Term>    <Field>EDAT</Field>    <Count>0</Count>    <Explode>Y</Explode>   </TermSet>   <OP>RANGE</OP>   <OP>AND</OP>  </TranslationStack><QueryTranslation>all[sb] AND 2012/06/26[EDAT] : 2012/06/28[EDAT]</QueryTranslation>\n\n</eSearchResult>\n'


from xml.etree import ElementTree
from cStringIO import StringIO
query_result_stream = StringIO(query_result)
query_parse_tree = ElementTree.parse(query_result_stream)
web_env_list = query_parse_tree.findall('WebEnv')
assert(len(web_env_list) == 1)
web_env = web_env_list[0].text
query_key_list = query_parse_tree.findall('QueryKey')
assert(len(query_key_list) == 1)
query_key = query_key_list[0].text
print web_env, query_key
id_list_element_list = query_parse_tree.findall('IdList')
assert(len(id_list_element_list) == 1)
id_list_element = id_list_element_list[0]
id_element_list = [i.text for i in id_list_element.findall('Id')]

num_new_entities = len(id_element_list)
print  num_new_entities, 'new taxa'
context = '<' + RETRIEVE_URI + '?db=taxonomy&webenv=' + web_env + '&query_key=' + query_key + '&retstart=1&retmax=7000>'
max_num_returns = 500
ret_start = 1
retrieve_params = { 'db' : 'taxonomy',
                    'webenv' : web_env,
                    'query_key' : query_key,
                    'retstart' : ret_start,
                    'retmax' : str(ret_start + max_num_returns - 1) + context,
                    'retmode' : 'xml'
                    }

class NewTaxonFromSummary(object):
    def __init__(self, et_element):
        il = et_element.findall('Id')
        assert(len(il) == 1)
        self._taxon_id = il[0].text
        for item_el in et_element.findall('Item'):
            n = item_el.attrib.get('Name')
            self.__dict__[n] = item_el.text;
results = 
while ret_start <= num_new_entities:
    resp = requests.get(RETRIEVE_URI,
                        params=retrieve_params,
                        allow_redirects=True)
    retrieve_parse_tree = ElementTree.parse(resp.text)

    print resp.url
    time.sleep(3)
    ret_start += max_num_returns
    
