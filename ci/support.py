#!/usr/bin/env python3
"""Portable CI provisioning/metadata. No owner PAT or owner signing key is used here."""
from pathlib import Path
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parent.parent
GRADLE_SHA='20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'

def require(value,message):
    if not value:raise RuntimeError(message)

def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def run(args,**kwargs):
    result=subprocess.run(list(map(str,args)),capture_output=True,text=True,check=False,**kwargs)
    require(result.returncode==0,'CI tool failed; arguments and output withheld')
    return result.stdout

def bootstrap():
    require(os.environ.get('GITHUB_REPOSITORY')=='byteKayze/MontoGram','Wrong CI repository')
    setup=ROOT/'setup';tools=setup/'toolchains';tools.mkdir(parents=True,exist_ok=True)
    archive=tools/'gradle-8.13-bin.zip'
    urllib.request.urlretrieve('https://services.gradle.org/distributions/gradle-8.13-bin.zip',archive)
    require(sha(archive)==GRADLE_SHA,'Gradle distribution checksum mismatch')
    with zipfile.ZipFile(archive) as source:
        for name in source.namelist():require(not Path(name).is_absolute() and '..' not in Path(name).parts,'Unsafe Gradle archive')
        source.extractall(tools)
    (tools/'gradle-8.13/bin/gradle').chmod(0o755);archive.unlink()
    sdk=Path(os.environ['ANDROID_HOME']).resolve()
    require(sdk.is_dir(),'Runner Android SDK missing')
    link=setup/'android-sdk';require(not os.path.lexists(link),'Unexpected SDK path already present');link.symlink_to(sdk,target_is_directory=True)
    manager=sdk/'cmdline-tools/latest/bin/sdkmanager';require(manager.is_file(),'Runner sdkmanager missing')
    run([manager,'--sdk_root='+str(sdk),'--licenses'],input='y\n'*200)
    run([manager,'--sdk_root='+str(sdk),'platforms;android-36','build-tools;36.0.0','ndk;27.2.12479018','cmake;3.22.1'])
    yasm=sdk/'ndk/27.2.12479018/prebuilt/linux-x86_64/bin/yasm'
    if not yasm.exists():
        nasm=shutil.which('nasm');require(nasm is not None,'Missing runner NASM and NDK Yasm')
        yasm.parent.mkdir(parents=True,exist_ok=True);yasm.symlink_to(nasm)
    print('Verified Gradle8.13 and SDK36/NDK27.2/CMake3.22.1 ready; no build secrets used')

def provision():
    api_id=os.environ.get('MONTOGRAM_API_ID','');api_hash=os.environ.get('MONTOGRAM_API_HASH','')
    require(re.fullmatch(r'[1-9][0-9]{0,9}',api_id) and int(api_id)<=2147483647,'Missing/invalid CI API ID')
    require(re.fullmatch(r'[a-fA-F0-9]{32}',api_hash),'Missing/invalid CI API hash')
    private=ROOT/'.montogram-private';private.mkdir(mode=0o700);private.chmod(0o700)
    password=private/'signing.password';key=private/'montogram.p12';config=ROOT/'montogram.properties'
    for path,value in ((password,secrets.token_urlsafe(48)+'\n'),(config,'api.id='+api_id+'\napi.hash='+api_hash+'\n')):
        fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w') as stream:stream.write(value)
    run([Path(os.environ['JAVA_HOME'])/'bin/keytool','-genkeypair','-alias','montogram','-keyalg','RSA','-keysize','2048',
         '-validity','2','-dname','CN=Montogram ephemeral CI only','-storetype','PKCS12','-keystore',key,
         '-storepass:file',password,'-keypass:file',password])
    key.chmod(0o600)
    print('CI-only signing identity created; it is NOT the Montogram release key')

def workflow_id():
    base=Path('/tmp/montogram-gradle')
    children=[p for p in base.iterdir() if re.fullmatch(r'[a-f0-9]{32}',p.name) and p.is_dir() and not p.is_symlink()]
    require(len(children)==1,'Ambiguous managed Gradle workflow')
    with Path(os.environ['GITHUB_OUTPUT']).open('a') as stream:stream.write('workflow='+children[0].name+'\n')

def metadata():
    require(os.environ.get('MONTOGRAM_GRADLE_OUTCOME')=='success','Gradle build success required')
    dependencies=json.loads((ROOT/'build/montogram-runtime-dependencies.json').read_text())
    require(dependencies=={'afatStandaloneRuntimeClasspath':['33.7.2-android'],'afatStandaloneCompileClasspath':['33.7.2-android'],'standaloneRuntimeClasspath':['33.7.2-android'],'standaloneCompileClasspath':['33.7.2-android']},'Fixed production dependency proof required')
    commit=run(['git','-C',ROOT,'rev-parse','HEAD']).strip()
    require(commit==os.environ['GITHUB_SHA'],'Checkout/dispatch source mismatch')
    apk=ROOT/'TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/app-internal-unminified.apk'
    require(apk.is_file() and not apk.is_symlink() and apk.stat().st_size<100*1024*1024,'Unexpected APK handoff size/type')
    tools=ROOT/'setup/android-sdk/build-tools/36.0.0'
    certs=run([tools/'apksigner','verify','--print-certs',apk])
    match=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})',certs)
    require(match is not None,'CI signer fingerprint unavailable')
    run([tools/'zipalign','-c','-P','16','4',apk])
    badging=run([tools/'aapt2','dump','badging',apk])
    require("name='app.montogram.android' versionCode='20058'" in badging and 'application-debuggable' not in badging,'Unexpected APK identity/debug state')
    abis=set()
    with zipfile.ZipFile(apk) as source:
        require(source.testzip() is None,'Corrupt CI APK')
        for name in source.namelist():
            if name.startswith('lib/') and name.endswith('.so'):abis.add(name.split('/')[1])
    require(abis=={'arm64-v8a','x86_64'},'Unexpected CI ABI set')
    output=ROOT/'ci-output';output.mkdir();target=output/'montogram-ci-ephemeral.apk';shutil.copyfile(apk,target)
    value={'signing_state':'EPHEMERAL_CI_NOT_RELEASE_SIGNED','certificate_sha256':match.group(1),'apk_sha256':sha(target),
           'bytes':target.stat().st_size,'repository':'byteKayze/MontoGram','commit':commit,'run_id':os.environ['GITHUB_RUN_ID'],
           'run_attempt':os.environ['GITHUB_RUN_ATTEMPT'],'version_code':20058,'abis':sorted(abis),'gradle':'8.13',
           'java_version':re.search(r'JAVA_VERSION="([^"]+)"',(Path(os.environ['JAVA_HOME'])/'release').read_text()).group(1),
           'runtime_android_tested':False,'owner_signing_key_uploaded':False,'native_compile_jobs':int(os.environ.get('MONTOGRAM_NATIVE_JOBS','1')),'runtime_dependency_versions':dependencies}
    (output/'ci-metadata.json').write_text(json.dumps(value,indent=2)+'\n')
    print('Bounded CI handoff ready: ephemeral APK plus metadata only')

if __name__=='__main__':
    require(len(sys.argv)==2 and sys.argv[1] in ('bootstrap','provision','workflow-id','metadata'),'Expected one CI operation')
    {'bootstrap':bootstrap,'provision':provision,'workflow-id':workflow_id,'metadata':metadata}[sys.argv[1]]()
