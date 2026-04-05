import subprocess
result = subprocess.run(
    ['cmd.exe', '/c', 'gradlew.bat', 'compileDebugKotlin', '--console=plain'],
    capture_output=True, text=True, cwd=r'e:\adr_hoc\SecureChat'
)
output = result.stdout + result.stderr
# Write FULL output
with open(r'e:\adr_hoc\SecureChat\full_output.txt', 'w', encoding='utf-8') as f:
    for i, line in enumerate(output.splitlines()):
        f.write(f"{i}: {line}\n")
print("Done. Lines: ", len(output.splitlines()))
