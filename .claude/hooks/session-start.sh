#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo "[session-start] Setting up Gradle and Android environment..."

python3 - <<'PYEOF'
import os, sys, re, subprocess, tempfile, zipfile, shutil, urllib.request, urllib.parse

proxy_url = os.environ.get('HTTPS_PROXY', '')

def enable_basic_auth_tunneling(jdk_path, label):
    net_props = os.path.join(jdk_path, 'conf', 'net.properties')
    if not os.path.exists(net_props):
        return
    with open(net_props) as f:
        content = f.read()
    if 'jdk.http.auth.tunneling.disabledSchemes=Basic' in content:
        content = content.replace(
            'jdk.http.auth.tunneling.disabledSchemes=Basic',
            'jdk.http.auth.tunneling.disabledSchemes='
        )
        with open(net_props, 'w') as f:
            f.write(content)
        print(f"[session-start] Enabled Basic auth tunneling in {label} net.properties")

def import_ca_into_jdk(jdk_path, label):
    cacerts = os.path.join(jdk_path, 'lib', 'security', 'cacerts')
    keytool = os.path.join(jdk_path, 'bin', 'keytool')
    cacerts_real = os.path.realpath(cacerts)
    sys_ca_bundle = '/etc/ssl/certs/ca-certificates.crt'
    if not (os.path.exists(sys_ca_bundle) and os.path.exists(keytool)):
        return
    with open(sys_ca_bundle) as f:
        bundle = f.read()
    pem_blocks = re.findall(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----', bundle, re.DOTALL)
    for pem in pem_blocks:
        result = subprocess.run(['openssl', 'x509', '-noout', '-subject'], input=pem, capture_output=True, text=True)
        if 'Anthropic' not in result.stdout:
            continue
        cn_match = re.search(r'CN\s*=\s*([^\n,]+)', result.stdout)
        alias = cn_match.group(1).strip().lower().replace(' ', '-') if cn_match else 'anthropic-ca'
        check = subprocess.run([keytool, '-list', '-alias', alias, '-keystore', cacerts_real, '-storepass', 'changeit'],
                               capture_output=True, text=True)
        if check.returncode == 0:
            print(f"[session-start] CA already imported into {label}: {alias}")
            continue
        with tempfile.NamedTemporaryFile(mode='w', suffix='.pem', delete=False) as tmp:
            tmp.write(pem)
            tmp_path = tmp.name
        r = subprocess.run([keytool, '-import', '-trustcacerts', '-noprompt',
                            '-alias', alias, '-file', tmp_path,
                            '-keystore', cacerts_real, '-storepass', 'changeit'],
                           capture_output=True, text=True)
        os.unlink(tmp_path)
        if r.returncode == 0:
            print(f"[session-start] CA imported into {label} truststore: {alias}")
        else:
            print(f"[session-start] Failed to import CA into {label}: {alias} ({r.stderr.strip()})")

java_home = os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-21-openjdk-amd64')
import_ca_into_jdk(java_home, 'JDK 21')
enable_basic_auth_tunneling(java_home, 'JDK 21')

gradle_home_for_jdks = os.path.expanduser('~/.gradle')
gradle_jdks_dir_early = os.path.join(gradle_home_for_jdks, 'jdks')
if os.path.isdir(gradle_jdks_dir_early):
    for jdk_name in os.listdir(gradle_jdks_dir_early):
        jdk_path = os.path.join(gradle_jdks_dir_early, jdk_name)
        if not os.path.isdir(jdk_path):
            continue
        keytool = os.path.join(jdk_path, 'bin', 'keytool')
        if not os.path.exists(keytool):
            continue
        import_ca_into_jdk(jdk_path, f'Gradle JDK ({jdk_name})')
        enable_basic_auth_tunneling(jdk_path, f'Gradle JDK ({jdk_name})')

if not proxy_url:
    print("[session-start] HTTPS_PROXY is not set; skipping proxy configuration")
else:
    parsed   = urllib.parse.urlparse(proxy_url)
    host     = parsed.hostname
    port     = str(parsed.port)
    user     = parsed.username or ''
    password = parsed.password or ''

    gradle_home = os.path.expanduser('~/.gradle')
    os.makedirs(gradle_home, exist_ok=True)

    init_d = os.path.join(gradle_home, 'init.d')
    os.makedirs(init_d, exist_ok=True)
    init_script = os.path.join(init_d, 'proxy-auth.gradle')
    with open(init_script, 'w') as f:
        f.write(f"""import java.net.Authenticator
import java.net.PasswordAuthentication

def proxyUser = System.getProperty("https.proxyUser") ?: System.getProperty("http.proxyUser")
def proxyPassword = System.getProperty("https.proxyPassword") ?: System.getProperty("http.proxyPassword")

if (proxyUser && proxyPassword) {{
    Authenticator.setDefault(new Authenticator() {{
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {{
            if (getRequestorType() == Authenticator.RequestorType.PROXY) {{
                return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray())
            }}
            return null
        }}
    }})
}}
""")
    print(f"[session-start] Gradle init script written: {init_script}")

    props = (
        f"systemProp.https.proxyHost={host}\n"
        f"systemProp.https.proxyPort={port}\n"
        f"systemProp.https.proxyUser={user}\n"
        f"systemProp.https.proxyPassword={password}\n"
        f"systemProp.http.proxyHost={host}\n"
        f"systemProp.http.proxyPort={port}\n"
        f"systemProp.http.proxyUser={user}\n"
        f"systemProp.http.proxyPassword={password}\n"
        f"systemProp.https.nonProxyHosts=localhost|127.0.0.1\n"
        f"systemProp.http.nonProxyHosts=localhost|127.0.0.1\n"
        f"systemProp.jdk.http.auth.tunneling.disabledSchemes=\n"
        f"systemProp.jdk.http.auth.proxying.disabledSchemes=\n"
    )
    gradle_home = os.path.expanduser('~/.gradle')
    with open(os.path.join(gradle_home, 'gradle.properties'), 'w') as f:
        f.write(props)
    print(f"[session-start] gradle.properties written (proxy={host}:{port})")

if proxy_url:
    proxy_handler = urllib.request.ProxyHandler({'https': proxy_url, 'http': proxy_url})
    opener = urllib.request.build_opener(proxy_handler)
else:
    opener = urllib.request.build_opener()

def download(url, dest_path):
    print(f"[session-start] Downloading {url} ...")
    with opener.open(url) as resp:
        total = int(resp.headers.get('Content-Length', 0))
        downloaded = 0
        with open(dest_path, 'wb') as f:
            while True:
                chunk = resp.read(65536)
                if not chunk:
                    break
                f.write(chunk)
                downloaded += len(chunk)
                if total:
                    pct = downloaded * 100 // total
                    print(f"\r  {pct}% ({downloaded}/{total} bytes)", end='', flush=True)
    print()

project_dir = os.environ.get('CLAUDE_PROJECT_DIR', '/home/user/FolderViewer')
android_home = os.path.expanduser('~/android-sdk')
local_props = os.path.join(project_dir, 'local.properties')

os.makedirs(android_home, exist_ok=True)

cmdline_tools_dir = os.path.join(android_home, 'cmdline-tools', 'latest')
sdkmanager_bin = os.path.join(cmdline_tools_dir, 'bin', 'sdkmanager')
if not os.path.exists(sdkmanager_bin):
    print("[session-start] Installing Android SDK command-line tools...")
    cmdline_url = 'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip'
    cmdline_zip = os.path.join(android_home, 'commandlinetools.zip')
    download(cmdline_url, cmdline_zip)
    with zipfile.ZipFile(cmdline_zip, 'r') as zf:
        zf.extractall(android_home)
    os.unlink(cmdline_zip)
    extracted = os.path.join(android_home, 'cmdline-tools')
    temp_dir  = os.path.join(android_home, '_cmdline-tools-temp')
    os.rename(extracted, temp_dir)
    os.makedirs(extracted, exist_ok=True)
    shutil.move(temp_dir, cmdline_tools_dir)
    os.chmod(sdkmanager_bin, 0o755)
    print(f"[session-start] cmdline-tools installed: {cmdline_tools_dir}")

licenses_dir = os.path.join(android_home, 'licenses')
os.makedirs(licenses_dir, exist_ok=True)
with open(os.path.join(licenses_dir, 'android-sdk-license'), 'w') as f:
    f.write('\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n')
print("[session-start] Android SDK license accepted")

with open(local_props, 'w') as f:
    f.write(f"sdk.dir={android_home}\n")
print(f"[session-start] local.properties written: sdk.dir={android_home}")

platform_dir = os.path.join(android_home, 'platforms', 'android-36')
android_jar  = os.path.join(platform_dir, 'android.jar')
if not os.path.exists(android_jar):
    print("[session-start] android-36/android.jar が見つかりません。ダウンロードします...")
    os.makedirs(platform_dir, exist_ok=True)
    platform_zip_path = os.path.join(android_home, 'platform-36.zip')
    download('https://dl.google.com/android/repository/platform-36_r02.zip', platform_zip_path)
    with zipfile.ZipFile(platform_zip_path, 'r') as zf:
        members = [m for m in zf.namelist() if m.startswith('android-36/') and not m.endswith('/')]
        for member in members:
            dest = os.path.join(platform_dir, member[len('android-36/'):])
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with zf.open(member) as src, open(dest, 'wb') as dst:
                dst.write(src.read())
    os.unlink(platform_zip_path)
    print(f"[session-start] android-36 platform installed: {platform_dir}")
else:
    print("[session-start] android-36/android.jar は既に存在します。スキップします。")

local_repo = os.path.join(os.path.expanduser('~/.gradle'), 'local-plugin-repo')

def install_to_local_repo(group, artifact, version, ext, url):
    dest = os.path.join(local_repo, group.replace('.', '/'), artifact, version, f"{artifact}-{version}.{ext}")
    if os.path.exists(dest):
        print(f"[session-start] Local repo hit: {group}:{artifact}:{version} ({ext})")
        return
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    download(url, dest)
    print(f"[session-start] Local repo installed: {group}:{artifact}:{version} ({ext})")

foojay_base = "https://plugins-artifacts.gradle.org/org.gradle.toolchains/foojay-resolver/1.0.0"
foojay_hash = "78b86a47dfdf7697c9bd15da78983fd80da7247d6e02fc106bdf07e0388b60a8"
install_to_local_repo(
    "org.gradle.toolchains", "foojay-resolver", "1.0.0", "jar",
    f"{foojay_base}/{foojay_hash}/foojay-resolver-1.0.0.jar",
)
install_to_local_repo(
    "org.gradle.toolchains", "foojay-resolver", "1.0.0", "pom",
    "https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver/1.0.0/foojay-resolver-1.0.0.pom",
)
install_to_local_repo(
    "org.gradle.toolchains", "foojay-resolver", "1.0.0", "module",
    "https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver/1.0.0/foojay-resolver-1.0.0.module",
)
install_to_local_repo(
    "org.gradle.toolchains.foojay-resolver-convention",
    "org.gradle.toolchains.foojay-resolver-convention.gradle.plugin",
    "1.0.0", "pom",
    "https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/1.0.0/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin-1.0.0.pom",
)

init_d = os.path.join(os.path.expanduser('~/.gradle'), 'init.d')
os.makedirs(init_d, exist_ok=True)
init_script = os.path.join(init_d, 'local-plugin-repo.gradle')
with open(init_script, 'w') as f:
    f.write(f"""beforeSettings {{ settings ->
    settings.pluginManagement {{
        repositories {{
            maven {{
                url = uri("{local_repo}")
                content {{
                    includeGroup("org.gradle.toolchains.foojay-resolver-convention")
                    includeGroup("org.gradle.toolchains")
                }}
            }}
        }}
    }}
}}
""")
print(f"[session-start] Gradle init script written: {init_script}")

default_keystore = os.path.expanduser('~/.android/debug.keystore')
if not os.environ.get('DEBUG_KEYSTORE_FILE') and os.path.exists(default_keystore):
    os.environ['DEBUG_KEYSTORE_FILE'] = default_keystore
    bashrc = os.path.expanduser('~/.bashrc')
    export_line = f'export DEBUG_KEYSTORE_FILE="{default_keystore}"'
    try:
        with open(bashrc) as f:
            content = f.read()
    except FileNotFoundError:
        content = ''
    if export_line not in content:
        with open(bashrc, 'a') as f:
            f.write(f'\n{export_line}\n')
        print(f"[session-start] Set DEBUG_KEYSTORE_FILE to default keystore")

print("[session-start] Environment setup complete!")
PYEOF
