#!/usr/bin/env python3
"""Guard the personal Samsung release identity before publishing an APK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'io.github.gbettineschi.betternightlight'
CERTIFICATE = 'b478b60b64740b48da9cf33504214f3e0b1c2d4f4996f856d5946e3080f47f6f'


def version(properties, tag):
    values = dict(re.findall(r'^\s*(samsungVersion(?:Name|Code))\s*=\s*(\S+)\s*$', properties.read_text(), re.M))
    name = values.get('samsungVersionName', '')
    code = values.get('samsungVersionCode', '')
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)-samsung\.[1-9]\d*', name) or not code.isdecimal() or not 0 < int(code) <= 2_100_000_000:
        raise ValueError('Invalid Android version name/code in gradle.properties.')
    if tag and tag != 'v' + name:
        raise ValueError('Tag does not match samsungVersionName in gradle.properties.')
    return name, int(code)


def inspect_apk(apk):
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    signed = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    if re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', signed) != [CERTIFICATE]:
        raise ValueError('APK signing certificate differs from the installed Samsung release.')
    badging = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(apk)], text=True)
    identity = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    manifest = subprocess.check_output([str(tools/'aapt'), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
    if not identity or identity[1] != PACKAGE or 'application-debuggable' in badging or 'SamsungProbeActivity' in manifest:
        raise ValueError('APK package or release/debug manifest is invalid.')
    return identity[3], int(identity[2])


def notes(changelog, name):
    match = re.search(r'^## ' + re.escape(name) + r'(?:[ \t]+[^\n]*)?\n(.*?)(?=^## |\Z)', changelog.read_text(), re.M | re.S)
    if not match or not match[1].strip():
        raise ValueError('Missing release notes for ' + name)
    return match[1].strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--properties', type=Path, default=ROOT/'gradle.properties')
    commands = parser.add_subparsers(dest='command', required=True)
    for mode in ['version', 'notes', 'verify']:
        sub = commands.add_parser(mode)
        sub.add_argument('--tag', default='')
        if mode == 'notes':
            sub.add_argument('--changelog', type=Path, default=ROOT/'CHANGELOG.md')
        if mode == 'verify':
            sub.add_argument('apk', type=Path)
            sub.add_argument('--previous-dir', type=Path)
    args = parser.parse_args()
    name, code = version(args.properties, args.tag)
    if args.command == 'version':
        print(f'version_name={name}\nversion_code={code}')
    elif args.command == 'notes':
        print(notes(args.changelog, name))
    else:
        if inspect_apk(args.apk) != (name, code):
            raise ValueError('APK version differs from gradle.properties.')
        if args.previous_dir is not None:
            previous = list(args.previous_dir.glob('*.apk'))
            if len(previous) != 1:
                raise ValueError('Expected exactly one previous APK for the update check.')
            if inspect_apk(previous[0])[1] >= code:
                raise ValueError('Android versionCode must increase above the published APK.')
        print(json.dumps({'version': name, 'androidVersionCode': code,
                          'androidCertificateSha256': CERTIFICATE,
                          'sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                          'sourceCommit': subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True).strip()}, indent=2))


if __name__ == '__main__':
    main()
