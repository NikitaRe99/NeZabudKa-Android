# UI polish — 0.2.73

- Assistant app heading moved up 4 dp.
- Default reminder sorting is date (including invalid imported values). Explicitly stored user sorting is preserved.
- Overdue/completed expansion states are stored in independent SharedPreferences on every toggle and read on screen creation, including after process restart.
- Notes column switch uses the outlined icon family for both states. Notes card spacing is 8 dp vertically/horizontally, matching reminder list spacing.
- Trash links to the full Notes settings category scrolled to trash retention. Back returns to Notes.
- Alarm compact actions use equal width weights in a full-width Row. Locked-screen swipe track and thumb share height 64 dp and theme action corner radius with zero inset. The label reserves space for the handle. Gesture completion threshold and alarm delivery behavior are unchanged.
- Applied these alarm cosmetics to the actually shipped HEAD layout. The previous stopped, unapproved alarm redesign is preserved separately in docs/showcase/alarm-draft-before-0.2.73.patch; it is not part of the app source or compiled.
- NLU toggle moved above model selector.
- Shared model selector highlights selection by outline/selection semantics instead of checkmark; installed-model delete button is on the left.
- Simplified update and alarm-repeat descriptions.

## Store builds

`android/app/build.gradle.kts` defines BuildConfig.GITHUB_UPDATES_ENABLED from `githubUpdatesEnabled` (default true).
Pass `-PgithubUpdatesEnabled=false` for a store distribution. It removes the updates entry/search result and related category description, prevents startup prompts, disables updater operations/workers, cancels retained updater work, and hides cached update availability. Existing user update preference is retained for other distributions.

Validated the false flag with :app:generateReleaseBuildConfig and the settings catalog with both flag values. Final normal release is built with the default true flag. This flag controls in-app GitHub updates; it is not a claim of full store-policy certification.

Validation: unit tests for default sorting, preserving explicit settings, catalog hiding/search and existing app behaviors passed. Final counts and device checks follow.

Final validation: 358 tests total, 354 passed, 4 optional tests skipped. Signed release build and lintVitalRelease passed. Final normal BuildConfig.GITHUB_UPDATES_ENABLED verified true.

Installed final release 0.2.73 / versionCode 96; device lastUpdateTime 2026-10-14 12:42:31. Device checks confirmed:
- compact test alarm buttons span the row with equal widths;
- the synthetic 5-second locked-screen test shows matching thumb/track height and corners with non-overlapping label;
- completion swipe dismisses the test alarm;
- trash link opens the full Notes category at retention settings, back returns to Notes;
- shared Whisper selector visibly outlines the selected model and places deletion on the left, with no checkmark.

No reminder/note records were created, edited or deleted; synthetic alarms use the reserved ReminderDisplayTest ID and have no Room record. The phone had no overdue/completed records, so expansion persistence could not be exercised against those lists on the phone. It is implemented via preference reads/writes; do not describe it as device-tested. Original model, mode and retention settings were retained.

APK SHA-256: a08d664b93ddfd3ba1aae334fa79b2686ad274b0675328e460de0455792fe827.
