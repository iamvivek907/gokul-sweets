# Campaign video fixture

`menu-animation.webm` is a two-second, silent, solid-maroon VP9 clip for browser playback tests. It contains no customer or production media. Reproduce with:

```bash
ffmpeg -f lavfi -i color=c=maroon:s=320x200:r=10 -t 2 -c:v libvpx-vp9 menu-animation.webm
```
