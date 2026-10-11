"""macOS alarm installation and LaunchAgent entry point. Credentials live in Keychain."""
import ctypes
from ctypes import byref, c_void_p, c_uint32
import getpass
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import sys
import tempfile
import ast

import agent
import alarm


def keychain(service, secret=None):
    security = ctypes.CDLL('/System/Library/Frameworks/Security.framework/Security')
    account = getpass.getuser().encode()
    name = service.encode()
    length, data, item = c_uint32(), c_void_p(), c_void_p()
    security.SecKeychainFindGenericPassword.argtypes = [c_void_p, c_uint32, ctypes.c_char_p,
        c_uint32, ctypes.c_char_p, ctypes.POINTER(c_uint32), ctypes.POINTER(c_void_p), ctypes.POINTER(c_void_p)]
    status = security.SecKeychainFindGenericPassword(None, len(name), name, len(account), account,
                                                     byref(length), byref(data), byref(item))
    if status == 0:
        try:
            return ctypes.string_at(data, length.value).decode()
        finally:
            security.SecKeychainItemFreeContent.argtypes = [c_void_p, c_void_p]
            security.SecKeychainItemFreeContent(None, data)
            core = ctypes.CDLL('/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation')
            core.CFRelease.argtypes = [c_void_p]
            core.CFRelease(item)
    if status != -25300 or secret is None:
        raise RuntimeError('Unlock your login Keychain and allow this program to access its Gokul credential.')
    encoded = secret.encode()
    security.SecKeychainAddGenericPassword.argtypes = [c_void_p, c_uint32, ctypes.c_char_p,
        c_uint32, ctypes.c_char_p, c_uint32, ctypes.c_char_p, c_void_p]
    if security.SecKeychainAddGenericPassword(None, len(name), name, len(account), account,
                                             len(encoded), encoded, None) != 0:
        raise RuntimeError('Could not save the credential in your login Keychain.')
    return secret


def service(cfg):
    return 'in.gokulsweets.alarm.' + str(cfg['branch_id']) + '.' + cfg['agent_id'] + '.' + cfg['api_url']


def launch_definition(root, python, label):
    return {'Label': label, 'ProgramArguments': [python, str(root / 'macos.py'), str(root / 'config.local.json')],
            'WorkingDirectory': str(root), 'RunAtLoad': True, 'KeepAlive': True, 'ThrottleInterval': 10,
            'AbandonProcessGroup': False,
            'StandardOutPath': '/dev/null', 'StandardErrorPath': '/dev/null'}


def install_certificates(root):
    # python.org's OpenSSL runtime may have no default CA bundle on a fresh Mac.
    # Use the operating system's root certificates without disabling TLS verification.
    result = subprocess.run(['/usr/bin/security', 'find-certificate', '-a', '-p',
                             '/System/Library/Keychains/SystemRootCertificates.keychain'],
                            check=True, capture_output=True, text=True)
    if '-----BEGIN CERTIFICATE-----' not in result.stdout:
        raise RuntimeError('macOS root certificates could not be loaded.')
    (root / 'cacert.pem').write_text(result.stdout, encoding='ascii')


def stop_service(domain, label):
    result = subprocess.run(['/bin/launchctl', 'bootout', domain + '/' + label], capture_output=True)
    if result.returncode and subprocess.run(['/bin/launchctl', 'print', domain + '/' + label], capture_output=True).returncode == 0:
        raise RuntimeError('Could not stop the alarm. No installed files were changed.')


def activate(root, staged, plist, staged_plist, domain, label):
    """Back up before stopping; restore files and startup registration on any failure."""
    names = ('agent.py', 'alarm.py', 'macos.py', 'cacert.pem', 'config.local.json')
    backup = staged / 'backup'
    backup.mkdir()
    originals = [name for name in names if (root / name).exists()]
    for name in originals:
        shutil.copyfile(root / name, backup / name)
    had_plist = plist.exists()
    if had_plist:
        shutil.copyfile(plist, backup / 'launch.plist')
    stop_service(domain, label)
    try:
        for name in names:
            os.replace(staged / name, root / name)
        os.replace(staged_plist, plist)
        subprocess.run(['/bin/launchctl', 'bootstrap', domain, str(plist)], check=True)
    except BaseException as failure:
        try:
            stop_service(domain, label)
            for name in names:
                if name in originals:
                    os.replace(backup / name, root / name)
                else:
                    (root / name).unlink(missing_ok=True)
            if had_plist:
                os.replace(backup / 'launch.plist', plist)
                subprocess.run(['/bin/launchctl', 'bootstrap', domain, str(plist)], check=True)
            else:
                plist.unlink(missing_ok=True)
        except BaseException as rollback:
            raise RuntimeError('Upgrade and rollback failed. Recovery files retained at ' + str(staged)) from rollback
        raise RuntimeError('Upgrade failed; previous installation restored and restarted.') from failure


def install(profile):
    cfg = agent.load_config(profile)
    if cfg['station'] != 'KITCHEN':
        raise ValueError('Select the KITCHEN station profile for the macOS alarm.')
    root = Path.home() / 'Library/Application Support/GokulAlarm' / str(cfg['branch_id'])
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(root, 0o700)
    installed = root / 'config.local.json'
    if installed.exists():
        old = agent.load_config(installed)
        if any(cfg[k] != old[k] for k in ('api_url', 'branch_id', 'station', 'agent_id', 'printer_code')):
            raise ValueError('An upgrade must use the same installed kitchen profile.')
        keychain(service(cfg))
    else:
        secret = getpass.getpass('Private backend PRINT_AGENT_API_KEY (input hidden): ')
        if not secret.strip():
            raise ValueError('The private backend key is required.')
        keychain(service(cfg), secret)
    label = 'in.gokulsweets.alarm.' + str(cfg['branch_id'])
    domain = 'gui/' + str(os.getuid())
    folder = Path.home() / 'Library/LaunchAgents'
    folder.mkdir(parents=True, exist_ok=True)
    plist = folder / (label + '.plist')
    staged = Path(tempfile.mkdtemp(prefix='.upgrade-', dir=root))
    os.chmod(staged, 0o700)
    try:
        for name in ('agent.py', 'alarm.py', 'macos.py'):
            source = Path(__file__).parent / name
            shutil.copyfile(source, staged / name)
            ast.parse((staged / name).read_text(encoding='utf-8'), filename=name)
        shutil.copyfile(profile, staged / 'config.local.json')
        os.chmod(staged / 'config.local.json', 0o600)
        install_certificates(staged)
        staged_plist = staged / 'launch.plist'
        with staged_plist.open('wb') as stream:
            plistlib.dump(launch_definition(root, sys.executable, label), stream)
        os.chmod(staged_plist, 0o600)
        activate(root, staged, plist, staged_plist, domain, label)
    except RuntimeError as exc:
        if 'Recovery files retained' in str(exc):
            raise
        shutil.rmtree(staged)
        raise
    except BaseException:
        shutil.rmtree(staged)
        raise
    else:
        shutil.rmtree(staged)
    print('Installed. In Admin, test computer sound, confirm you hear it, then enable the alarm.')
    print('Starts automatically when this Mac account signs in. Keep the lid open and speakers audible.')
    print('This Mac installation plays kitchen alarms; Windows remains the supported USB printer installer.')


def main():
    if sys.platform != 'darwin':
        raise RuntimeError('This installer requires macOS.')
    if len(sys.argv) > 1 and sys.argv[1] == '--install':
        profile = input('Drag config.local.json here, then press Return: ').strip()
        # Finder terminal drag-and-drop escapes spaces; accept its shell quoting without executing it.
        import shlex
        parts = shlex.split(profile)
        if len(parts) != 1:
            raise ValueError('Select exactly one downloaded profile.')
        install(Path(parts[0]).expanduser())
        return
    path = Path(sys.argv[1])
    cfg = agent.load_config(path)
    os.environ['SSL_CERT_FILE'] = str(path.parent / 'cacert.pem')
    os.environ['PRINT_AGENT_API_KEY'] = keychain(service(cfg))
    # -w makes caffeinate exit with this process, including abnormal termination.
    awake = subprocess.Popen(['/usr/bin/caffeinate', '-i', '-w', str(os.getpid())])
    import signal
    def terminate(signum, frame):
        raise SystemExit(0)
    signal.signal(signal.SIGTERM, terminate)
    try:
        alarm.supervise(cfg, path)
    finally:
        awake.terminate()


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print('Gokul alarm needs attention:', str(exc), file=sys.stderr)
        sys.exit(1)
