# Accepted V5 palette inputs

These are exact PNG bytes extracted from Git commit
`d01c9f2d627ab2d00127613ded5e85d5e9f5936b` (Interstice 0.6.0 / V5).
They are immutable input patterns for `tools/GenerateRealmV6Palettes.java`.
No PNG was decoded, resized, redrawn or re-encoded while copying this baseline.
`manifest.json` records SHA-256, material family, colour role and the existing
provenance registry for each of the100 PNGs. `manifest.sha256` pins that manifest.

The original sources and licenses remain in `art/sources/cc0/`,
`art/sources/original/`, `docs/ASSET_LIBRARY.md` and
`src/main/resources/assets/interstice/provenance/`. The geology baseline includes
previously accepted edge reconciliation, brick joints and interior variants;
V6 preserves those existing patterns instead of repeating their historical
generation operations. The authored clingweed remains32x32 and vines16x32.

Ocean PNGs and metadata are excluded from overlays. Their original raw runtime
hashes and separate Git blob hashes are recorded (Git may normalize metadata
line endings without changing the accepted local file). Their original hashes, and
the hashes of all other unmodified assets, are recorded in the manifest.
Candidate packs and candidate provenance go only into ignored
`.verification/v6-palette-candidates/`. Changing the common runtime textures is
a separate step after native A/B/C review; it would also change colours in old
V1–V5 worlds, without changing their block IDs or generation.
