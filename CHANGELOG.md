# Changelog

## 1.0.0

### Added
- Added custom music discs for Plasmo Voice servers.
- Added `/lazodisc burn <url> [title]`, `/lazodisc erase`, and `/lazodisc search <song name>`.
- Added clickable search results in chat.
- Added YouTube, YouTube Music, SoundCloud, Spotify track link, and direct audio URL support through LavaPlayer.
- Added separate editable server language files in `config/lazodiscs/lang`.
- Added separate permission settings for burning, erasing, searching, and playing discs.
- Added Sable / Create Aeronautics moving platform position support.

### Changed
- LavaPlayer sources stream directly into Plasmo Voice instead of being fully decoded into RAM.
- `/lazodisc burn` now checks that LavaPlayer can resolve the track before writing the disc.
- Search accepts song names only; links are burned through `/lazodisc burn`.
- Active jukebox playback is managed per jukebox to stay stable during fast insert and eject spam.
- Finished or failed playback sources are removed from the active jukebox map automatically.
- Burned discs hide the original vanilla music disc tooltip on the server-side item data.

### Removed
- Removed the old public cache command.
- Removed global and per-chunk jukebox limits.
