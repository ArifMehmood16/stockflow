# Free static demo hosting

Live demo: https://arifmehmood16.github.io/stockflow/

GitHub Pages hosts the public repository's static app on the free github.io address. No paid plan, custom domain, database or application server is used. GitHub documents public-repository availability on GitHub Free: https://docs.github.com/en/pages/getting-started-with-github-pages

## Publishing source

The `gh-pages` branch contains only the `prototype/` subtree. It is a required deployment branch, not abandoned development work. GitHub Pages serves its root. `.nojekyll` disables Jekyll processing. Relative links make scripts, logos and navigation work under `/stockflow/` as well as locally. The page includes its own content-security policy because GitHub Pages does not use the local Node server's response headers.

No custom workflow, paid build service or application CI/CD pipeline is required. GitHub handles its standard Pages build/deployment.

## Update the demo

Commit the intended prototype changes first, then run from the repository root:

```sh
make verify
git subtree push --prefix=prototype origin gh-pages
```

This publishes committed app files only; README, article drafts, local `.env`, `.lab`, data caches and credentials are outside the subtree. The deployment command requires Git (including `git subtree`) and existing push access. Do not force-push if histories diverge; inspect before changing the deployment history.

Check the Pages build status in the repository's Settings → Pages or with:

```sh
gh api repos/ArifMehmood16/stockflow/pages/builds/latest --jq .status
```

Updates can take a few minutes to become visible. Smoke-check the live page after publishing. No uptime or latency guarantee is claimed. GitHub Pages limits and policies still apply; this educational static project has no metered backend or usage-based application bill.

The page and its JavaScript imports use a shared asset revision query (`v=database-lessons-1`) to avoid reusing previously cached modules after the deployment lesson was removed. When changing the module/HTML contract, update that revision consistently in the page, imports and regression test. This avoids adding a bundler for the static demo.
