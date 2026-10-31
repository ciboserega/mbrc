# Tempo and pitch controls — implementation boundary

Base: CIBO MusicBee Remote v6, upstream GitHub Actions build #274
(`bbce7f860afbd4098f50639f5107ef31f5261c7b`).

## User interaction

- On **Now playing**, offer **Pitch** buttons **−0.5 / +0.5 semitone**.
- Offer **Tempo** buttons **−1% / +1%**, starting from 100% playback speed.
- Pitch and tempo are independent; **1% is not the same as 1 BPM**.
- Provide a reset action to 0.0 semitones and 100% when backend is supported.
- Do not change track tags/metadata.
- Do not pretend a request succeeded; update displayed values only from actual server state or acknowledged values.

## Current blocker

The upstream MusicBee Remote plugin's V4 and V6 player command catalogs contain no
pitch or tempo getter/setter. The MusicBee C# plugin interface has DSP-enable
and EQ methods, but no exposed pitch or playback-tempo setter. These controls
must not be treated as functional until a safe Windows-side implementation exists.

The Compose `TempoPitchPanel` is a reusable UI prepared in this branch;
it deliberately requires an explicit `enabled` flag and is not injected into
Now playing in the production branch. It may be previewed or mounted only with
`enabled = false` until the backend is implemented.

## Required Windows server work

1. Research how the installed MusicBee Tempo Control DSP can be manipulated
   safely and officially by another plugin. Do not mutate audio tags or directly
   poke unverified BASS stream handles.
2. Add read and explicit set commands to the server plugin, and include the
   operations in V6 advertised capabilities. Suggested names (not implemented):
   `player_dsp_status`, `player_set_pitch`, `player_set_tempo`.
3. Report actual pitch in half-semitone units and tempo as percent of original,
   including state after changes made locally in MusicBee.
4. Reject unsupported/not-connected operations; do not optimistically update UI.
5. Add concurrency and readback tests, then wire the Android ViewModel and mount
   the Compose panel on both portrait and landscape Now playing layouts.
6. Test manually on a Windows computer with the same MusicBee version and DSP.

## Do not ship as functional

This branch is **UI groundwork**, not a completed pitch/tempo remote control APK.
