#!/usr/bin/env bash
# Surface actionable failures in public job annotations, with configured secrets redacted.
set -uo pipefail
logfile="${RUNNER_TEMP:-/tmp}/teledrive-release-checks.log"
./gradlew --no-daemon --max-workers=1 :shared:allTests :app:testDebugUnitTest :app:lintRelease :app:assembleRelease -PsplitApks=true 2>&1 | tee "$logfile"
gradle_status=${PIPESTATUS[0]}
python3 - "$logfile" "$gradle_status" <<'PY'
import glob,os,re,sys,xml.etree.ElementTree as E
logfile,status=sys.argv[1],int(sys.argv[2])
text=re.sub(r'\x1b\[[0-9;]*m','',open(logfile,errors='replace').read())
failures=[]
for pattern in ['app/build/test-results/testDebugUnitTest/TEST-*.xml','shared/build/test-results/**/TEST-*.xml']:
 for path in glob.glob(pattern,recursive=True):
  try: root=E.parse(path).getroot()
  except (E.ParseError,OSError):continue
  for test in root.iter('testcase'):
   for child in test:
    if child.tag in ('failure','error'):
     failures.append(f"{test.get('classname')}.{test.get('name')}: {child.get('message','')}\n{child.text or ''}"[:7000])
selected=[line for line in text.splitlines() if re.search(r'FAILED|Exception|Caused by|Execution failed|Error:|Could not|FAILURE:|AssertionError|^e:|OutOfMemory|daemon.*disappeared|error:',line)]
problem='\n\n'.join(failures) or '\n'.join(selected[-60:]) or '\n'.join(text.splitlines()[-60:])
for key,value in os.environ.items():
 if value and len(value)>=6 and (key.startswith(('TELEGRAM_API_','NEXT_RELEASE_')) or key=='KS_PASS'):
  problem=problem.replace(value,'[REDACTED]')
summary=os.environ.get('GITHUB_STEP_SUMMARY')
if summary:
 with open(summary,'a') as f:
  f.write('## Release validation\n\nGradle exit code: '+str(status)+'.\n\n')
  if status:f.write('```text\n'+problem[:24000]+'\n```\n')
if status:
 def escape(s):return s.replace('%','%25').replace('\r','%0D').replace('\n','%0A')
 print('::error title=Release validation diagnostics::'+escape(problem[:24000]))
PY
exit "$gradle_status"
