import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('menu_probe',Path(__file__).with_name('measure-menu-reads.py'))
probe=importlib.util.module_from_spec(spec);spec.loader.exec_module(probe)

class MenuProbeTest(unittest.TestCase):
    def run_probe(self, mode=None, state_status=404):
        calls=[]
        def request(origin,path,body=None):
            calls.append((path,body))
            data={}; status=200
            if path.endswith('/features'):data={'smartAvailability':True}
            elif '/menu-state?' in path:status=state_status;data={'version':'v1'} if status==200 else None
            elif path.startswith('/api/menu?'):data={'revision':'r1','items':[{'productId':1,'available':True},{'productId':2,'available':False}]}
            elif '/catalog/' in path:data={'categories':[{'products':[{'id':1,'available':True,'saleMode':'WEIGHT','minimumWeightGrams':500},{'id':2,'available':True}]}]}
            return {'status':status,'elapsedMs':1,'bytes':50,'cacheMode':mode if body else None,'cacheControl':None}, data
        with patch.object(probe,'request',side_effect=request),patch.object(probe.time,'sleep'):
            result=probe.measure('https://gokul.test',1,'2026-10-23',3,2)
        return result,calls

    def test_probe_only_reads_public_configuration_and_opted_in_preview(self):
        result,calls=self.run_probe('enabled')
        bodies=[(path,body) for path,body in calls if body]
        self.assertEqual(len(bodies),3)
        self.assertTrue(all(path.startswith('/api/branches/1/availability?menuPreview=true&compact=true&versioned=true') for path,body in bodies))
        self.assertTrue(all(body['items']==[{'productId':1,'weightGrams':500}] for path,body in bodies))
        self.assertEqual(result['sampleProducts'],1)
        self.assertEqual(result['effectivePreviewCache'],'enabled')

    def test_missing_diagnostic_headers_are_not_misreported_as_disabled(self):
        self.assertEqual(self.run_probe()[0]['effectivePreviewCache'],'unverified (version endpoint unavailable)')
        self.assertEqual(self.run_probe('disabled')[0]['effectivePreviewCache'],'disabled')
        self.assertEqual(self.run_probe(state_status=200)[0]['effectivePreviewCache'],'enabled')

if __name__=='__main__':unittest.main()
