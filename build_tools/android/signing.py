"""Materialize release-only Android secrets on the temporary runner."""
import argparse
import base64
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def private_paths():
    temp = Path(os.environ['RUNNER_TEMP']).resolve()
    return temp / 'android-release.p12', temp / 'android-release.json', ROOT / 'zmala_phone/app/google-services.json'


def prepare_signing():
    key, config, firebase = private_paths()
    names = ('ZMALA_ANDROID_KEYSTORE_BASE64', 'ZMALA_ANDROID_SIGNING_JSON', 'ZMALA_FIREBASE_CLIENT_JSON')
    if any(not os.environ.get(name) for name in names):
        raise ValueError('Configure the three Zmala Android Actions secrets before releasing')
    signing = json.loads(os.environ[names[1]])
    for name in ('storePassword', 'keyAlias', 'keyPassword', 'certificateSha256'):
        if not signing.get(name):
            raise ValueError('Incomplete signing configuration')
    client = json.loads(os.environ[names[2]])
    if not any(c.get('client_info', {}).get('android_client_info', {}).get('package_name') == 'org.zmala.phone'
               for c in client.get('client', [])):
        raise ValueError('Firebase configuration does not belong to org.zmala.phone')
    key_bytes = base64.b64decode(os.environ[names[0]], validate=True)
    signing['storeFile'] = str(key)
    for path, content in ((key, key_bytes), (config, json.dumps(signing).encode()),
                          (firebase, json.dumps(client).encode())):
        # Exclusive creation, restrictive mode from the first write, never log contents.
        with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb') as stream:
            stream.write(content)
    print('Private release inputs prepared.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'cleanup'))
    if parser.parse_args().action == 'prepare':
        prepare_signing()
    else:
        for path in private_paths():
            path.unlink(missing_ok=True)
