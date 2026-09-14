# GitHub Pages

Klarinet publishes a static site from [`.github/workflows/pages.yml`](.github/workflows/pages.yml) whenever `main` is updated. The site is a **project Pages site** (not `vectencia.github.io` itself), so every URL is under the `/Klarinet/` prefix.

| What | Production URL |
|---|---|
| Web demo (`:demo-web` production webpack) | [https://vectencia.github.io/Klarinet/](https://vectencia.github.io/Klarinet/) |
| API docs (Dokka HTML for `:klarinet` and `:klarinet-coroutines`) | [https://vectencia.github.io/Klarinet/api/](https://vectencia.github.io/Klarinet/api/) |
| Workflow runs | [Actions → GitHub Pages](https://github.com/vectencia/Klarinet/actions/workflows/pages.yml) |
| Pages settings (org admin) | [Settings → Pages](https://github.com/vectencia/Klarinet/settings/pages) |

The site is public HTTPS. There is no custom domain and no server-side code: GitHub only serves the uploaded artifact.

## Published tree

The workflow does **not** publish the git tree. It builds two Gradle outputs, then copies them into a `public/` directory that becomes the Pages artifact:

```
public/
├── .nojekyll                 # keep dash/underscore paths if Jekyll ever processes the artifact
├── index.html                # Klarinet Web Demo
├── styles.css
├── klarinet-demo.js          # Kotlin/JS production bundle (outputFileName in demo-web/build.gradle.kts)
├── klarinet-demo.js.map      # webpack source map (copied with the whole productionExecutable dir)
└── api/                      # Dokka HTML (root-level :dokkaGenerate)
    ├── index.html            # module list (Klarinet, Klarinet Coroutines)
    ├── klarinet/
    └── klarinet-coroutines/
```

`public/api` is reserved for Dokka. Do not add a demo-web route, webpack file, or resource named `api`.

Asset URLs in both halves are **relative** (`styles.css`, `klarinet-demo.js`, Dokka `pathToRoot`). That is required on a project site: an absolute `/styles.css` would request `https://vectencia.github.io/styles.css` and 404. Keep new links relative, or prefix them with `./`.

## Web demo (site root)

`:demo-web` is Kotlin/JS + Web Audio. It is the same six destinations as the Compose / iOS demos, plus browser-only file helpers:

| Tab | What it exercises |
|---|---|
| Tone Gen | `AudioStream` callback sine, `GainParams.FADE_MS`, `SleepTimer` |
| Mic Meter | `getUserMedia` input, `levelFlow()`, input latency |
| Latency | `LatencyInfo`, device list |
| File | `decodeAudioFile` / `decodeAudioBytes`, `playFile`, mic `recordToFile`, WAV download |
| Effects | Live gain → delay → reverb chain (`FADE_MS` on gain) |
| Scenes | `AudioScenePlayer` crossfade between JSON presets |

Browser constraints that show up on the live site:

- **Microphone.** GitHub Pages is HTTPS, so `getUserMedia` is allowed. The browser still prompts. Denied permission leaves Mic Meter / File record idle; it is not a deploy failure.
- **Autoplay.** Chromium blocks `AudioContext` until a user gesture. Start audio from a click (the demo already does).
- **Decode URL.** `decodeAudioFile(url)` is a cross-origin `fetch`. The remote host must send `Access-Control-Allow-Origin` that includes `https://vectencia.github.io`. The placeholder `https://example.com/audio.mp3` will fail CORS; use the file picker or a CORS-enabled URL.
- **In-memory files.** JS `AudioFileWriter` / decoded readers live in the tab. Reloading the page drops them.

Sources: [`demo-web/src/jsMain/`](demo-web/src/jsMain/).

## API docs (`/api/`)

Root [`build.gradle.kts`](build.gradle.kts) aggregates Dokka from `:klarinet` and `:klarinet-coroutines` (Dokka 2.2.0). Module display names come from each module’s `dokka { moduleName.set(...) }` block.

```bash
./gradlew :dokkaGenerate
# output: build/dokka/html/index.html
```

Linux CI can generate this HTML: Apple DSP CMake/cinterop embedding is macOS-only, so Ubuntu Pages does not need an iOS SDK.

Dokka 2 HTML uses directories such as `com.vectencia.klarinet/-audio-engine/`. `.nojekyll` is written so a future Dokka that emits `_`-prefixed files is not stripped if Jekyll processes the artifact. Current output is already relative-path safe under `/Klarinet/api/`.

## One-time enablement

On **vectencia/Klarinet**, Pages source is already GitHub Actions and the site is live at the URLs above. The workflow still **builds and uploads the artifact**, then the deploy job calls `gh api repos/$GITHUB_REPOSITORY/pages`. HTTP 404 means Pages is off (typical on a new fork): the job prints a warning and skips `actions/deploy-pages`. That is why a green Pages badge on a fork can still mean “built, not published.”

An org admin (the `github-pages` environment and repo Pages settings are org-restricted on `vectencia/Klarinet`):

1. Open [Settings → Pages](https://github.com/vectencia/Klarinet/settings/pages).
2. Under **Build and deployment → Source**, choose **GitHub Actions** (not “Deploy from a branch”).
3. Re-run **GitHub Pages** from the Actions tab (`workflow_dispatch`), or push to `main`.
4. Confirm the `github-pages` environment exists and is not waiting on a required reviewer. The deploy job sets `environment.url` from `steps.deployment.outputs.page_url`.

First publish can take a minute after the workflow finishes. A 404 immediately after the first successful deploy is usually CDN propagation; wait and hard-refresh.

Forks must repeat this on the fork. Their site URL is `https://<user>.github.io/<repo>/`, not the vectencia URL.

## Workflow

File: [`.github/workflows/pages.yml`](.github/workflows/pages.yml).

| Item | Value |
|---|---|
| Triggers | `push` to `main`; `workflow_dispatch` (Actions tab → Run workflow) |
| Not triggered | Pull requests. PRs do not publish a preview site. |
| Runner | `ubuntu-latest`, JDK 17 Temurin, Gradle action cache |
| Permissions | `contents: read`, `pages: write`, `id-token: write` (OIDC for `deploy-pages`) |
| Concurrency | group `pages`, `cancel-in-progress: false` so an in-flight production deploy is not aborted |
| Jobs | `build` then `deploy` (`needs: build`) |

`build`:

1. `./gradlew :demo-web:jsBrowserDistribution :dokkaGenerate`
2. `bash ./scripts/assemble-pages.sh` → `public/`
3. `actions/upload-pages-artifact@v3` with `path: public`

`deploy`:

1. Probe Pages with `gh api` (uses `GITHUB_TOKEN`).
2. If enabled, `actions/deploy-pages@v4`.

`cancel-in-progress: false` means a second push queues behind the current deploy instead of cancelling it. Do not flip this to `true` for Pages.

CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) compiles `:demo-web` Kotlin/JS and runs `:dokkaGenerate` on macOS, but it does **not** run `jsBrowserDistribution` or assemble `public/`. A production webpack failure can still be unique to the Pages workflow.

## Local preview

Development webpack (source maps, rebuild on change). This is **not** the Pages bundle:

```bash
./gradlew :demo-web:jsBrowserDevelopmentRun
# URL Gradle prints, usually http://localhost:8080
```

Production tree, same layout as GitHub:

```bash
make pages
python3 -m http.server 8080 --directory public
```

Then open [http://localhost:8080/](http://localhost:8080/) and [http://localhost:8080/api/](http://localhost:8080/api/). `localhost` is a secure context, so microphone prompts work.

`make pages` is:

```bash
./gradlew :demo-web:jsBrowserDistribution :dokkaGenerate
bash ./scripts/assemble-pages.sh
```

`public/` is gitignored. Assemble fails fast if either `index.html` is missing.

To serve only the demo without Dokka:

```bash
./gradlew :demo-web:jsBrowserProductionRun
```

## Changing what is published

| Change | Where | What to verify |
|---|---|---|
| Demo UI / Web Audio usage | `demo-web/src/jsMain/` | `make pages` and click all six tabs; mic + file picker; CORS failure on a blocked URL is expected |
| Webpack file name / module name | `demo-web/build.gradle.kts` (`outputFileName`, `outputModuleName`) | `index.html` `<script src="klarinet-demo.js">` still matches |
| Demo HTML/CSS | `demo-web/src/jsMain/resources/` | Relative `href`/`src`; no `/api` collision |
| KDoc, module title | `klarinet/**`, `klarinet-coroutines/**`, `dokka { }` blocks | `./gradlew :dokkaGenerate` and browse `public/api/` |
| Site layout / `.nojekyll` | `scripts/assemble-pages.sh` | Script is what Pages CI runs |
| Triggers, permissions, skip-if-disabled | `.github/workflows/pages.yml` | Push to `main` or `workflow_dispatch`; confirm warning vs deploy in the log |

Keep demo resources relative. Do not introduce a `<base href="/">`.

## Troubleshooting

| Symptom | Likely cause | What to do |
|---|---|---|
| Workflow green, site 404 or stale | Pages source is not GitHub Actions; deploy step skipped | Log line: `GitHub Pages is not enabled`. Admin sets Source to GitHub Actions, then re-run the workflow |
| `deploy-pages` fails with permissions / OIDC | Missing `pages: write` or `id-token: write`, or the job is not in the `github-pages` environment | Do not drop those permissions; keep `environment.name: github-pages` |
| Deploy job waiting | Environment protection (required reviewers) | An org admin approves the `github-pages` deployment |
| `Assemble site` fails `missing …/index.html` | Gradle task did not produce the expected directory | Check the “Build web demo and API docs” step; local `make pages` should fail the same way |
| Demo loads, JS 404 | `outputFileName` ≠ `klarinet-demo.js` in `index.html` | Align `demo-web/build.gradle.kts` and `resources/index.html` |
| Demo CSS/JS 404 on GitHub but fine on `localhost:8080` from webpack-dev-server | Absolute paths (`/styles.css`) | Use relative URLs; project site lives at `/Klarinet/` |
| `/api/` missing types or broken nav | Dokka not copied, or `public/api` overwritten | Confirm assemble copies `build/dokka/html/.` into `public/api/`; demo copy must happen first |
| Mic Meter does nothing | Permission denied, or not a secure context | HTTPS (Pages) or `localhost`; allow the prompt |
| “Decode URL” errors | CORS | Use **Choose file**, or host audio with ACAO for `https://vectencia.github.io` |
| Fork workflow warns and never publishes | Fork has no Pages source | Fork Settings → Pages → GitHub Actions; URL is `https://<user>.github.io/<repo>/` |

Useful log checks on a run:

- Build step exit code for `jsBrowserDistribution` / `dokkaGenerate`
- Assemble echo: `assembled …/public (demo at /, API docs at /api/)`
- Deploy: either the skip warning with a link to `settings/pages`, or `page_url` from `deploy-pages`

## Related files

| Path | Role |
|---|---|
| [`.github/workflows/pages.yml`](.github/workflows/pages.yml) | Build, assemble, upload, optional deploy |
| [`scripts/assemble-pages.sh`](scripts/assemble-pages.sh) | `public/` layout used by CI and `make pages` |
| [`demo-web/`](demo-web/) | Kotlin/JS demo module |
| [`demo-web/src/jsMain/resources/index.html`](demo-web/src/jsMain/resources/index.html) | Demo entry; relative script/style |
| [`build.gradle.kts`](build.gradle.kts) | Root Dokka aggregation |
| [`Makefile`](Makefile) | `make pages`, `make docs` |
| [`.github/workflows/ci.yml`](.github/workflows/ci.yml) | Compiles JS and Dokka; does not publish Pages |
