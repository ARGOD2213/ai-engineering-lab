# Commands cheat sheet (PowerShell, Windows)

Your hands-on runbook: start, stop, develop on a branch, review, push. Type every command yourself.
Replace `<repo>` with `C:\Users\NAVEEN ROYAL\ai-engineering-lab`.

Ports used by this project:

| Port | What | Started by |
|---|---|---|
| 5433 | PostgreSQL + pgvector | `docker compose up -d` |
| 8080 | Spring Boot API | `mvnw.cmd spring-boot:run` |

---

## 1. Daily start (3 terminals)

**Terminal 1: infrastructure (repo root)**

```powershell
cd "<repo>"
docker compose up -d          # start Postgres in the background
docker compose ps             # wait until STATUS shows (healthy)
```

If Docker is not running, open Docker Desktop first and wait until it says "Engine running".

**Terminal 2: the application (keep this window open)**

```powershell
cd "<repo>\services\ai-engineering-api"
$env:MAVEN_OPTS = "-Xmx512m"          # optional: keeps Maven's memory small on an 8 GB laptop
.\mvnw.cmd spring-boot:run
```

Ready when the log shows `Tomcat started on port 8080`. First-ever start downloads the ~90 MB model.

**Terminal 3: your working terminal (git, curl, tests)**

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health      # expect status: UP
```

---

## 2. Check what is running

```powershell
docker compose ps                                  # is Postgres up and (healthy)?
Invoke-RestMethod http://localhost:8080/actuator/health    # is the app up?

# Who is listening on our ports?
Get-NetTCPConnection -State Listen -LocalPort 5433,8080 -ErrorAction SilentlyContinue |
  Select-Object LocalPort, OwningProcess

# Name of the process behind a port (use a PID from the line above)
Get-Process -Id <PID>
```

---

## 3. Try the API

```powershell
$h = @{ 'Content-Type' = 'application/json' }

# text -> vector (nothing stored)
Invoke-RestMethod http://localhost:8080/api/embeddings -Method Post -Headers $h `
  -Body '{"text":"Employees get 12 casual leave days."}'

# store a document
Invoke-RestMethod http://localhost:8080/api/documents -Method Post -Headers $h `
  -Body '{"id":"my-doc-1","text":"Postgres stores vectors with pgvector.","metadata":{"topic":"db"}}'

# semantic search
Invoke-RestMethod http://localhost:8080/api/search -Method Post -Headers $h `
  -Body '{"query":"vector database","topK":3}'

# search with a metadata filter
Invoke-RestMethod http://localhost:8080/api/search -Method Post -Headers $h `
  -Body '{"query":"vector database","topK":3,"filterExpression":"topic == ''db''"}'
```

Look inside the database:

```powershell
cd "<repo>"
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
# then in psql:  \dx    \d documents_minilm_l6_v2    SELECT id, vector_dims(embedding) FROM documents_minilm_l6_v2;
# leave psql with:  \q
```

---

## 4. Run the automated tests

Run from `<repo>\services\ai-engineering-api`. Docker must be running (tests start their own throw-away Postgres).

```powershell
.\mvnw.cmd test                  # 53 tests, no API key needed
.\mvnw.cmd test -Preal-model     # 4 extra tests with the real model
```

Look for `BUILD SUCCESS`. Per-class results are in `target\surefire-reports\`.

---

## 5. Stop everything cleanly

```powershell
# Terminal 2: press Ctrl+C in the window running spring-boot:run
#   (answer Y if PowerShell asks "Terminate batch job")

# Terminal 1 / 3, from the repo root:
cd "<repo>"
docker compose stop              # stops Postgres, KEEPS your data  (recommended daily)
# docker compose down            # removes the container, still KEEPS data (named volume)
# docker compose down -v         # DANGER: also DELETES all stored documents
```

**Check that the ports are free** (nothing should print):

```powershell
Get-NetTCPConnection -State Listen -LocalPort 5433,8080 -ErrorAction SilentlyContinue
```

**If port 8080 is still taken** (a leftover app process):

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080 | Select-Object OwningProcess   # note the PID
Get-Process -Id <PID>                                                              # confirm it is java
Stop-Process -Id <PID>                                                             # stop it
```

Only stop a PID after confirming it is the process you expect.

---

## 6. Git basics you will use every day

```powershell
cd "<repo>"
git status                       # what changed? (run this constantly)
git status -sb                   # short version, shows branch + ahead/behind
git branch                       # local branches (* = current)
git log --oneline -10            # last 10 commits
git diff                         # unstaged changes
git diff --staged                # staged changes (what the next commit will contain)
```

---

## 7. New feature workflow: branch, work, review, merge

Never work directly on `main`. One branch per change.

### 7.1 Start from an up-to-date main

```powershell
git switch main
git pull origin main             # get the latest from GitHub
```

### 7.2 Create your branch

```powershell
git switch -c feature/short-description      # e.g. feature/add-delete-endpoint
```

Naming: `feature/...` new work, `fix/...` bug fix, `docs/...` documentation, `test/...` tests.

### 7.3 Work and commit in small steps

```powershell
# edit code, run the app/tests, then:
git status
git add path\to\file1 path\to\file2          # stage specific files (preferred)
# git add -p                                  # stage piece by piece, reviewing each chunk
git diff --staged                             # REVIEW what you are about to commit
git commit -m "feat: add delete endpoint for documents"
```

Commit message style used in this repo: `feat:`, `fix:`, `docs:`, `test:` + short description.
Do not commit `.env` (it is git-ignored) or any API key.

Undo mistakes before pushing:

```powershell
git restore path\to\file              # throw away unstaged edits to a file (cannot be undone!)
git restore --staged path\to\file     # unstage a file, keep the edits
git commit --amend -m "better message"   # fix the LAST commit message (only if not pushed yet)
```

### 7.4 Self-review before pushing

```powershell
git log --oneline main..HEAD          # commits on your branch that main doesn't have
git diff main...HEAD                  # everything your branch changes versus main
git diff main...HEAD --stat           # just the list of files + line counts
.\services\ai-engineering-api\mvnw.cmd -f services\ai-engineering-api\pom.xml test   # tests green?
```

Read the diff like a reviewer: leftover debug prints, secrets, unrelated changes, missing tests.

### 7.5 Push the branch to GitHub

```powershell
git push -u origin feature/short-description     # -u only needed the first time
```

### 7.6 Open a pull request (review on GitHub)

1. Open the repository on GitHub. A yellow "Compare & pull request" banner appears. Click it.
2. Base: `main`, compare: your branch. Write what changed and why. Create the PR.
3. Read the "Files changed" tab as the reviewer. Comment, fix, push more commits to the same branch.
4. When happy, click **Merge pull request** (or "Squash and merge" for one tidy commit).
5. Click "Delete branch" on GitHub.

(If you install the GitHub CLI later: `gh pr create`, `gh pr view`, `gh pr merge`. Not required.)

### 7.7 Update your local main and clean up

```powershell
git switch main
git pull origin main
git branch -d feature/short-description          # delete the local branch (safe: refuses if unmerged)
git fetch --prune                                # forget branches deleted on GitHub
```

### 7.8 Merge locally instead of a PR (only for solo, quick changes)

```powershell
git switch main
git pull origin main
git merge feature/short-description              # add --no-ff to always create a merge commit
git push origin main
```

---

## 8. Keeping a branch up to date with main

```powershell
git fetch origin
git switch feature/short-description
git merge origin/main                 # bring main's new commits into your branch
# conflict? open the files, fix the <<<<<<< ======= >>>>>>> markers, then:
git add path\to\fixed-file
git commit                            # completes the merge
# changed your mind?  git merge --abort
```

---

## 9. Pull, fetch, stash (small but important)

```powershell
git fetch origin                 # download updates, change nothing locally
git pull origin main             # fetch + merge into your current branch
git stash                        # park uncommitted work temporarily
git stash list
git stash pop                    # bring it back
```

---

## 10. Final shutdown and push of the latest code (your end-of-session checklist)

Do these yourself, in order.

```powershell
# 1. Stop the app: Ctrl+C in the spring-boot:run window.

# 2. Stop the database
cd "<repo>"
docker compose stop

# 3. Confirm no ports are left open (should print nothing)
Get-NetTCPConnection -State Listen -LocalPort 5433,8080 -ErrorAction SilentlyContinue

# 4. See exactly what will be committed
git status
git diff

# 5. Stage, review, commit
git add docs/commands-cheatsheet.md          # add the files you actually want
git diff --staged
git commit -m "docs: add commands cheat sheet"

# 6. Make sure you are on the right branch, then push
git branch                                    # * should be your branch (or main for this docs-only change)
git push origin main                          # or: git push -u origin <your-branch>

# 7. Verify
git status -sb                                # expect: ## main...origin/main   (no ahead/behind)
git log --oneline -3
```

If the push is rejected with "fetch first" or "non-fast-forward":

```powershell
git pull origin main        # then fix conflicts if any, and push again
```

If GitHub asks you to sign in, use your GitHub account (a browser window or a Personal Access Token).
The remote is `origin = https://github.com/ARGOD2213/ai-engineering-lab.git`. You need write access to it.

Optional: close Docker Desktop itself when finished to give RAM back to Windows.

---

## 11. Quick troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `docker compose up` says it needs `POSTGRES_PASSWORD` | `.env` is missing: `Copy-Item .env.example .env` |
| App: `Connection refused` to 5433 | Postgres not running: `docker compose up -d` |
| App: `password authentication failed` | `.env` password differs from the one the volume was created with. Fix `.env`, or (deletes data) `docker compose down -v` |
| `Port 8080 was already in use` | Old app still running: see section 5 |
| Model download times out on first start | Download once manually; see the troubleshooting table in `services/ai-engineering-api/README.md` |
| Tests are skipped | Docker is not running |
| Everything is slow | Close browser tabs; use `docker compose stop` when not needed; keep `MAVEN_OPTS=-Xmx512m` |
