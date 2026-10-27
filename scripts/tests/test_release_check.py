"""Exercise release CLI guards without a phone, private key or network."""
from pathlib import Path
import json
import os
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
CERT = 'b478b60b64740b48da9cf33504214f3e0b1c2d4f4996f856d5946e3080f47f6f'
PACKAGE = 'io.github.gbettineschi.betternightlight'

class ReleaseChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.props = self.folder / 'gradle.properties'
        self.props.write_text('samsungVersionName=1.0.4-samsung.4\nsamsungVersionCode=1000007\n')
        self.env = os.environ.copy()
        self.env['ANDROID_HOME'] = str(self.folder / 'sdk')
        tools = self.folder / 'sdk/build-tools/36.0.0'
        tools.mkdir(parents=True)
        for name,body in {
            'apksigner': 'print(Path(sys.argv[-1] + ".cert").read_text())',
            'aapt': 'print(Path(sys.argv[3] + (".badging" if sys.argv[2] == "badging" else ".manifest")).read_text())',
        }.items():
            tool = tools / name
            tool.write_text('#!' + sys.executable + '\nfrom pathlib import Path\nimport sys\n' + body + '\n')
            tool.chmod(0o755)
        self.apk = self.make_apk(self.folder / 'new.apk', 1000007, '1.0.4-samsung.4')

    def make_apk(self, path, code, version, package=PACKAGE, cert=CERT, extra='', manifest=''):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b'public APK fixture')
        Path(str(path)+'.cert').write_text('Signer #1 certificate SHA-256 digest: '+cert)
        Path(str(path)+'.badging').write_text(f"package: name='{package}' versionCode='{code}' versionName='{version}'\n"+extra)
        Path(str(path)+'.manifest').write_text(manifest)
        return path

    def cli(self, *args):
        return subprocess.run([sys.executable,str(ROOT/'scripts/release-check.py'),'--properties',str(self.props),*map(str,args)],env=self.env,capture_output=True,text=True)

    def test_next_samsung_version_is_taken_from_properties(self):
        result = self.cli('version','--tag','v1.0.4-samsung.4')
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('version_name=1.0.4-samsung.4',result.stdout)
        self.assertIn('version_code=1000007',result.stdout)

    def test_tag_mismatch_and_invalid_android_versions_fail_closed(self):
        result = self.cli('version','--tag','v1.0.4-samsung.3')
        self.assertNotEqual(result.returncode,0)
        self.assertIn('does not match',result.stderr)
        for name,code in [('1.0.4-samsung.0',1000007),('1.0.4-samsung.4',0),('1.0.4-samsung.4',2100000001),('1.0.4-samsung.04',1000007)]:
            with self.subTest(name=name,code=code):
                self.props.write_text(f'samsungVersionName={name}\nsamsungVersionCode={code}\n')
                result=self.cli('version')
                self.assertNotEqual(result.returncode,0)
                self.assertIn('Invalid',result.stderr)

    def test_verified_apk_can_update_the_existing_release(self):
        previous=self.folder/'previous'
        self.make_apk(previous/'old.apk',1000006,'1.0.4-samsung.3')
        result=self.cli('verify',self.apk,'--previous-dir',previous)
        self.assertEqual(result.returncode,0,result.stderr)
        data=json.loads(result.stdout)
        self.assertEqual((data['version'],data['androidVersionCode'],data['androidCertificateSha256']),('1.0.4-samsung.4',1000007,CERT))

    def test_wrong_key_package_version_debug_flag_or_probe_are_rejected(self):
        for change in [{'cert':'0'*64},{'package':'upstream.app'},{'code':1000006},{'version':'1.0.4-samsung.3'},{'extra':'application-debuggable'},{'manifest':'samsung.SamsungProbeActivity'}]:
            with self.subTest(change=change):
                values={'code':1000007,'version':'1.0.4-samsung.4',**change}
                self.make_apk(self.apk,**values)
                result=self.cli('verify',self.apk)
                self.assertNotEqual(result.returncode,0)
                self.assertIn('APK',result.stderr)

    def test_reused_or_decreasing_version_code_is_rejected(self):
        previous=self.folder/'previous'
        for code in [1000007,1000008]:
            with self.subTest(code=code):
                self.make_apk(previous/'old.apk',code,'1.0.4-samsung.3')
                result=self.cli('verify',self.apk,'--previous-dir',previous)
                self.assertNotEqual(result.returncode,0)
                self.assertIn('increase',result.stderr)

    def test_missing_or_multiple_previous_apks_fail_closed(self):
        previous=self.folder/'previous';previous.mkdir()
        for count in [0,2]:
            with self.subTest(count=count):
                for n in range(count):self.make_apk(previous/f'{n}.apk',1000006,'1.0.4-samsung.3')
                result=self.cli('verify',self.apk,'--previous-dir',previous)
                self.assertNotEqual(result.returncode,0)
                self.assertIn('one previous APK',result.stderr)

    def test_release_notes_must_describe_the_requested_version(self):
        notes=self.folder/'CHANGELOG.md'
        notes.write_text('# Changelog\n\n## 1.0.4-samsung.4 — 2026-10-27\n\nA reviewed update.\n\n## 1.0.4-samsung.3\nOld notes.\n')
        result=self.cli('notes','--changelog',notes)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(result.stdout.strip(),'A reviewed update.')
        notes.write_text('# Changelog\n\n## 1.0.4-samsung.4\n\nA reviewed update.\n')
        result=self.cli('notes','--changelog',notes)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(result.stdout.strip(),'A reviewed update.')
        notes.write_text('# Changelog\n\n## 1.0.4-samsung.3\nOld notes.\n')
        result=self.cli('notes','--changelog',notes)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('Missing release notes',result.stderr)

if __name__ == '__main__':unittest.main()
