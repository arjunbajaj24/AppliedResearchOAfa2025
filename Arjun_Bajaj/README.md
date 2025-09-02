# StudyBuddy (Java) — Summarize notes + extract action items

## What it does
- Summarizes long text using Hugging Face Inference API (`sshleifer/distilbart-cnn-12-6`).
- Extracts “action items” with simple heuristic rules.
- Pure Java (Maven), CPU only.

## How to run (locally)
1) Install Java 11+ and Maven.
2) Set your Hugging Face token (free):
   - macOS/Linux:
     ```bash
     export HF_TOKEN=hf_your_token_here
     ```
   - Windows (PowerShell):
     ```powershell
     setx HF_TOKEN "hf_your_token_here"
     ```
     Restart the terminal so the env var is available.

3) Build and run:
   ```bash
   mvn -q -e -DskipTests package
   # uses sample_input.txt by default
   java -jar target/studybuddy-java-1.0.0.jar

   # or specify your own .txt
   java -jar target/studybuddy-java-1.0.0.jar path/to/notes.txt
