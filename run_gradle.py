import subprocess

def run_gradle():
    try:
        # Run gradlew with the requested task and capture output
        result = subprocess.run(['./gradlew.bat', 'compileDebugKotlin'], capture_output=True, text=True, cwd='e:/adr_hoc/SecureChat')
        
        with open('gradle_output.txt', 'w', encoding='utf-8') as f:
            f.write(result.stdout)
            f.write("\n--- ERRORS ---\n")
            f.write(result.stderr)
            
        print("Gradle output written to gradle_output.txt")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    run_gradle()
