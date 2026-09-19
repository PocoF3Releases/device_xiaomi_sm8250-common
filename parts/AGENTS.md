# XiaomiParts translation instructions for ChatGPT and coding assistants

Snapshot: 2026-09-19. Scope: translations only in this `parts` directory.
This is plain Markdown: in ChatGPT online, upload this file together with the
resource bundle and explicitly ask ChatGPT to follow it. Uploading a file does
not automatically grant access to the local source tree or Git repository.

## Task

All retained locales currently have complete effective resource coverage. Focus
on confirmed semantic corrections and new English keys rather than retranslating
completed locales. Preserve existing
translations. Do not call paid translation APIs or use the old Yaps-generated
outputs. Do not change application code or English wording as part of this task.
Work one language at a time; return the actual UTF-8 XML file or a downloadable
archive containing completed files, with paths preserved.

Russian (`values-ru`) and Ukrainian (`values-uk`) are complete and must remain
unchanged. The user approved removing incomplete low-confidence locales.
Kabyle (`values-kab-rDZ`), Friulian (`values-fur-rIT`) and Sardinian
(`values-sc-rIT`) have already been removed. Do not recreate them. For other
languages, explicitly identify low confidence rather than fabricating a
translation or copying English and calling it translated. Do not remove a
locale merely to reduce the amount of work.

## Resource layout

Repository: `device/xiaomi/sm8250-common` in the Android tree.

- `parts/res/values/strings.xml`: 170 English source strings.
- `parts/res/values/misound_controls.xml`: 5 additional English source strings.
- `parts/res/values/arrays.xml`: non-translatable reference arrays; leave intact.
- `parts/Translations/values-*/`: translated Android resources.
- `parts/Android.bp`: includes both `res` and `Translations` resource directories.

There are 175 translatable English strings at this snapshot. Recalculate from
all English values XML files if the source changes; never rely solely on this
count. Do not put localized files back into `parts/res`.

Existing translations may be split across XML files. Read every XML file in a
locale before deciding a key is missing. Add absent keys to
`parts/Translations/values-<locale>/additional_strings.xml`; if that file already
exists, merge into it without losing its entries. Never define a key twice in
the same locale. Keep existing regional wording. Spanish regional overrides
inherit Spanish; Azerbaijani and Kannada regional overrides inherit their
language resource directories. English variants inherit English defaults.
Keep Android legacy locale names such as `in` (Indonesian) and `iw` (Hebrew).

## Translation and Android XML rules

1. Translate only resources not marked `translatable="false"`. Preserve resource
   names, attributes, XML markup, and array/plural structure when applicable.
2. Preserve every positional format argument exactly, including `%1$s`, `%1$d`,
   and repeated arguments. You may reorder arguments grammatically but not
   change their index, type, or number of occurrences.
3. Preserve Android escapes such as literal `\n` and `\n\n`. Do not replace them
   with doubled backslashes or silently remove paragraph breaks.
4. Escape XML `&` and `<`. Handle Android apostrophes and quotation marks using
   Android string escaping. Current additions wrap each string in double quotes
   so apostrophes are safe; embedded double quotes must be escaped as `\"`.
5. Keep MiSound, Xiaomi, Alioth, product model names, CPU/GPU/DVFS/HBM identifiers,
   node names, sensor identifiers, frequencies, numeric weights, and units exact.
   Brand names and numeric-only templates can legitimately match English.
6. Translate technical meaning accurately: thermal ceilings here are maximum
   CPU/GPU frequencies imposed by thermal control, not temperature limits or
   fixed operating frequencies; the clear temperature is the release threshold;
   touch polling is not display refresh rate; anti-flicker can affect colors;
   bypassing MiSound during calls is temporary, not disabling all call audio.
7. Keep UI labels concise. Review grammatical agreement and regional terminology;
   do not convert European Portuguese through blind word replacements.
8. English fallback makes Android work but does not count as a completed
   translation (except intentionally inherited English locale variants).

## Completed work and commits

- `fa4de95`: separate translated resources into `parts/Translations`, remove
  redundant entries while preserving effective fallback values.
- `8cba582`: remove the three incomplete low-confidence locales listed above.
- `0a45ff6`: add 141 strings each for German, Spanish, French, Indonesian,
  Italian, Dutch, Polish, Brazilian Portuguese, European Portuguese and Turkish.

- `bce8027`: complete the remaining retained locales with translations authored
  by GPT-5.6 Sol in ChatGPT online.
- `df030f6`: correct six Finnish thermal descriptions and restore 126 altered
  product-name entries across the new translation batch.

- `ac7534b`: add four thermal profile navigation/search strings to English,
  Russian and Ukrainian. This translation follow-up adds the same four resources
  to every other non-inherited retained locale; regional language overrides and
  English variants continue to use their intended fallback resources.

All 86 retained locale folders now effectively cover all 175 English string
keys, including language inheritance and English regional fallback. Russian and
Ukrainian remain unchanged. The final local pre-push check compiled both `res`
and `Translations` successfully with AAPT2; XML/duplicate/coverage/placeholder
checks and `git diff --check` passed. This is resource compilation, not a full
APK link or device UI test. Linguistic review was targeted, not exhaustive;
lower-resource locales still benefit from native-speaker review. Do not claim
all wording is certified correct merely because resource checks pass.

For the four-key thermal navigation follow-up, ChatGPT online validated resource
name uniqueness and positional placeholders before committing. AAPT2 was not
available in the online connector environment, so this follow-up does not claim
a new AAPT2 compile pass.

## Current coverage

Missing counts include language fallback for regional folders. A zero for an
English variant means normal inheritance from the English source. A nonzero
count represents remaining work, not approval to delete the locale.

| Locale folder | Missing English keys |
| --- | ---: |
| `values-af` | 0 |
| `values-ar` | 0 |
| `values-as` | 0 |
| `values-ast-rES` | 0 |
| `values-az` | 0 |
| `values-az-rAZ` | 0 |
| `values-be` | 0 |
| `values-bg` | 0 |
| `values-bn` | 0 |
| `values-bs` | 0 |
| `values-ca` | 0 |
| `values-ckb` | 0 |
| `values-cs` | 0 |
| `values-cy` | 0 |
| `values-da` | 0 |
| `values-de` | 0 |
| `values-el` | 0 |
| `values-en-rAU` | 0 |
| `values-en-rCA` | 0 |
| `values-en-rGB` | 0 |
| `values-en-rIN` | 0 |
| `values-eo` | 0 |
| `values-es` | 0 |
| `values-es-rMX` | 0 |
| `values-es-rUS` | 0 |
| `values-et` | 0 |
| `values-eu` | 0 |
| `values-fa` | 0 |
| `values-fi` | 0 |
| `values-fr` | 0 |
| `values-fy-rNL` | 0 |
| `values-ga-rIE` | 0 |
| `values-gd` | 0 |
| `values-gl` | 0 |
| `values-gu` | 0 |
| `values-hr` | 0 |
| `values-hu` | 0 |
| `values-hy-rAM` | 0 |
| `values-in` | 0 |
| `values-is` | 0 |
| `values-it` | 0 |
| `values-iw` | 0 |
| `values-ja` | 0 |
| `values-ka` | 0 |
| `values-kk-rKZ` | 0 |
| `values-km-rKH` | 0 |
| `values-kn` | 0 |
| `values-kn-rIN` | 0 |
| `values-ko` | 0 |
| `values-lb` | 0 |
| `values-lo-rLA` | 0 |
| `values-lt` | 0 |
| `values-lv` | 0 |
| `values-mk-rMK` | 0 |
| `values-ml` | 0 |
| `values-mr` | 0 |
| `values-ms-rMY` | 0 |
| `values-my-rMM` | 0 |
| `values-nb` | 0 |
| `values-ne-rNP` | 0 |
| `values-nl` | 0 |
| `values-nn-rNO` | 0 |
| `values-or` | 0 |
| `values-pa-rIN` | 0 |
| `values-pl` | 0 |
| `values-pt-rBR` | 0 |
| `values-pt-rPT` | 0 |
| `values-ro` | 0 |
| `values-ru` | 0 |
| `values-sk` | 0 |
| `values-sl` | 0 |
| `values-sq` | 0 |
| `values-sr` | 0 |
| `values-sv` | 0 |
| `values-ta` | 0 |
| `values-te` | 0 |
| `values-th` | 0 |
| `values-tr` | 0 |
| `values-ug` | 0 |
| `values-uk` | 0 |
| `values-ur-rPK` | 0 |
| `values-uz-rUZ` | 0 |
| `values-vi` | 0 |
| `values-zh-rCN` | 0 |
| `values-zh-rHK` | 0 |
| `values-zh-rTW` | 0 |

## Validation and delivery

For each completed language, compare its effective keys to all translatable
English keys, check duplicate keys across files, and compare the multiset of
format arguments per string. Review the actual prose, especially technical
explanations. Confirm Russian/Ukrainian and pre-existing translations are
unchanged. Report exact added counts and any remaining missing keys.

Run compilation once after the reviewed changes are complete, before an
explicitly requested push; do not compile repeatedly per language. From the
Android source root:

```sh
out/host/linux-x86/bin/aapt2 compile --dir device/xiaomi/sm8250-common/parts/res -o /tmp/xiaomiparts-base.zip
out/host/linux-x86/bin/aapt2 compile --dir device/xiaomi/sm8250-common/parts/Translations -o /tmp/xiaomiparts-translations.zip
git -C device/xiaomi/sm8250-common diff --check
```

Do not run a full ROM build just for translations. If ChatGPT online cannot run
AAPT2 or Git, say so and return files for local validation; do not claim a build
passed or a commit was made. The downloadable bundle contains the source and
current translations, not the Android build tools or full repository.

Commit completed changes by meaningful language batches after validation.
Include language scope and validation in each commit description. Never stage
unrelated work or push automatically. The user requested this provenance footer
for the current Codex work:

```text
Created using GPT-6 Astra | Light reasoning via Codex

Co-authored-by: codex <noreply@openai.com>
```

For work actually performed in ChatGPT online, describe the actual tool/model
instead of falsely claiming Codex provenance. Retain the requested co-author
trailer when committing the returned translations locally.

## Suggested ChatGPT prompt

Read parts/AGENTS.md and compare the current English resources with the existing
translations. Coverage is complete at this snapshot. Fix only confirmed meaning
or formatting errors, or translate newly added English keys. Preserve existing
correct translations, Android placeholders, escapes and exact product names.
Leave Russian and Ukrainian unchanged. Report concrete changes and review
limitations. Return changed files at their original paths; do not claim tests
or commits you did not perform. Compile resources only at the final pre-push
stage when local tools are available.
