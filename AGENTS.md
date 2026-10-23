# Development guidelines

## Token-efficient work

- Read only task-relevant files; use targeted searches and reuse established architecture and findings.
- Keep planning, reviews, documentation, explanations, PR descriptions and completion summaries concise. Avoid repeated analysis and redundant documentation.
- Generate screenshots only when UI changes need visual review.

## Validation and output

- During implementation, run only tests directly relevant to changed functionality. Use GitHub Actions for comprehensive validation.
- Do not repeatedly run full JVM, emulator, lint, Core or release-build suites locally. Broaden local testing only to investigate failures or protect high-risk changes.
- Apply additional targeted validation to sensitive playback, navigation, profile, persistence and signing changes.
- Redirect verbose Gradle and emulator output to temporary files. Read short summaries and relevant failure excerpts; never dump full successful build logs into context.
- Do not repeatedly inspect identical results or continuously poll GitHub Actions. On CI failure, investigate the specific failed job rather than downloading every log.

## Safety and release requirements

- Never disable, bypass or weaken existing GitHub CI requirements or reduce mandatory test coverage. Full required GitHub CI must pass before merging or releasing.
- Preserve signing, update and user-data safeguards.
- Never merge, sign, publish or install without authorization.
