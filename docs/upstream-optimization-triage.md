# Upstream Optimization Backport Triage

Safe to backport now:
- Canvas lifecycle cleanup patterns from upstream `editor/canvas/*`, specifically splitting refresh/observer responsibilities into smaller helpers without changing editor behavior.
- Navigation/state hoisting ideas where they reduce recomposition churn in library and editor flows.
- View-model style data loading for list screens if kept local to performance-sensitive screens and not mixed with feature rewrites.

Useful but high-conflict with local changes:
- Full Hilt migration and injected repository graph.
- Full navigation rewrite around typed destinations and app navigator objects.
- Upstream editor gesture/canvas package reorganization, because local AI, reminders, WebDAV, and extra screens changed the surrounding state model.

Ignore for now:
- Whole-repo architectural reshuffle with no measurable runtime benefit on e-ink hardware.
- Pure settings/UI organization changes that do not affect render cost, IO, or lifecycle behavior.
- Broad refactors that would force conflicts across reminder, calendar, stats, AI, and WebDAV features.
